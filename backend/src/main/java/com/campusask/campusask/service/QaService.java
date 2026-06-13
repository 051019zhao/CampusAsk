package com.campusask.campusask.service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.campusask.campusask.context.BaseContext;
import com.campusask.campusask.entity.Knowledge;
import com.campusask.campusask.entity.QuestionLog;
import com.campusask.campusask.mapper.HotKeywordMapper;
import com.campusask.campusask.mapper.KnowledgeMapper;

/**
 * 赵丹丹 - 问答核心 Service
 *
 * 三级检索策略：
 *   1. 数据库关键词检索（LIKE）           → source: knowledge
 *   2. AI 检索数据库（RAG，把知识库喂给 AI）→ source: ai_knowledge
 *   3. AI 自主回答（基于华南理工大学知识）  → source: ai_web
 *
 * AI 故障降级：超时/限流 → 回退到步骤1的关键词结果，source: fallback
 */
@Service
public class QaService {

    private static final Logger log = LoggerFactory.getLogger(QaService.class);
    private static final int DEEPSEEK_TIMEOUT_SECONDS = 8;

    // AI 在 RAG 回答末尾打的标记，用于判断来源
    private static final String TAG_DB  = "[来源:知识库]";
    private static final String TAG_WEB = "[来源:AI自主回答]";

    @Autowired
    private KnowledgeMapper knowledgeMapper;

    @Autowired
    private SearchService searchService;

    @Autowired
    private com.campusask.campusask.mapper.QuestionLogMapper questionLogMapper;

    @Autowired
    private HotKeywordMapper hotKeywordMapper;

    @Autowired
    private HotspotService hotspotService;

    @Value("${deepseek.api.key}")
    private String apiKey;

    // ===== 回答缓存 =====
    private static final int CACHE_MAX_SIZE = 100;
    private static final long CACHE_TTL_MS = 5 * 60 * 1000; // 5分钟
    private final ConcurrentHashMap<String, CacheEntry> cache = new ConcurrentHashMap<>();

    private static class CacheEntry {
        final Map<String, Object> data;
        final long createdAt;
        CacheEntry(Map<String, Object> data) { this.data = data; this.createdAt = System.currentTimeMillis(); }
        boolean expired() { return System.currentTimeMillis() - createdAt > CACHE_TTL_MS; }
    }

    private String md5(String input) {
        try {
            byte[] digest = MessageDigest.getInstance("MD5").digest(input.getBytes());
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (NoSuchAlgorithmException e) { return input; }
    }

    private Map<String, Object> getFromCache(String keyword) {
        CacheEntry entry = cache.get(md5(keyword));
        if (entry == null) return null;
        if (entry.expired()) { cache.remove(md5(keyword)); return null; }
        log.info("缓存命中：{}", keyword);
        Map<String, Object> resp = new HashMap<>(entry.data);
        resp.put("cached", true);
        resp.put("sourceLabel", "⚡ 缓存命中 · " + resp.get("sourceLabel"));
        return resp;
    }

    private void putInCache(String keyword, Map<String, Object> resp) {
        if (cache.size() >= CACHE_MAX_SIZE) {
            // 清理一半过期条目
            cache.entrySet().removeIf(e -> e.getValue().expired());
        }
        cache.put(md5(keyword), new CacheEntry(new HashMap<>(resp)));
    }

    // ===== 主入口 =====

    public Map<String, Object> ask(String keyword) {
        Map<String, Object> resp = new HashMap<>();
        if (keyword == null || keyword.trim().isEmpty()) {
            resp.put("source", "fallback");
            resp.put("answer", "请输入问题");
            return resp;
        }
        // 归一化：去多余空白、去标点
        String trimmed = keyword.trim().replaceAll("[\\s\\p{Z}]+", "");

        // 第 -2 步：AI 语义归一化 + 同义词映射双重保险
        String cacheKey = normalizeQuestion(trimmed);
        Map<String, Object> cached = getFromCache(cacheKey);
        if (cached != null) {
            recordAsync(cacheKey, String.valueOf(cached.get("source")));
            return cached;
        }

        // 第 0 步：非校园问题直接拒绝，不消耗 token
        if (!isCampusRelated(trimmed)) {
            resp.put("source", "rejected");
            resp.put("sourceLabel", "🚫 超出服务范围");
            resp.put("answer", "抱歉，我只能回答华南理工大学校园相关的问题哦～ 比如食堂、宿舍、图书馆、课程、考试等，欢迎继续提问！");
            return resp;
        }

        // 第 1 步：数据库关键词 LIKE 检索（含同义词替换）
        String searchKeyword = normalizeSynonym(trimmed);
        List<Knowledge> localHits = knowledgeMapper.searchByKeyword(searchKeyword);
        if (localHits != null && !localHits.isEmpty()) {
            resp.put("source", "knowledge");
            resp.put("sourceLabel", "📚 数据库关键词检索");
            resp.put("answer", localHits);
            putInCache(cacheKey, resp);
            recordAsync(trimmed, "knowledge");
            return resp;
        }

        // 第 2 步：AI 检索数据库（RAG）
        List<Knowledge> allKnowledge = knowledgeMapper.listAll();
        try {
            String raw = callDeepSeekWithRAG(trimmed, allKnowledge);

            if (raw.contains(TAG_DB)) {
                String answer = raw.replace(TAG_DB, "").trim();
                resp.put("source", "ai_knowledge");
                resp.put("sourceLabel", "🤖 AI检索数据库");
                resp.put("answer", answer);
                putInCache(cacheKey, resp);
                recordAsync(trimmed, "ai_knowledge");
                return resp;
            }

            // 第 3 步：RAG 也没找到 → 调 Tavily 联网搜索
            String searchResult = searchService.search(trimmed);
            if (searchResult != null && !searchResult.isBlank()) {
                List<String> urls = searchService.getLastUrls();
                String summary = summarizeWithDeepSeek(trimmed, searchResult);
                // AI 成功提取答案 → 直接返回
                if (summary != null && !summary.isBlank()
                        && !summary.contains("暂无相关")) {
                    resp.put("source", "ai_search");
                    resp.put("sourceLabel", "🌐 AI联网搜索(华南理工)");
                    resp.put("answer", summary);
                    resp.put("searchUrls", urls);
                    putInCache(cacheKey, resp);
                    recordAsync(trimmed, "ai_search");
                    return resp;
                }
                // AI 提取失败但搜到了网页 → 展示搜索结果列表
                if (!urls.isEmpty()) {
                    resp.put("source", "ai_search");
                    resp.put("sourceLabel", "🌐 AI联网搜索(华南理工)");
                    resp.put("answer", "以下网页可能包含您需要的信息：\n\n"
                            + searchService.extractResultTitles(searchResult));
                    resp.put("searchUrls", urls);
                    putInCache(cacheKey, resp);
                    recordAsync(trimmed, "ai_search");
                    return resp;
                }
            }

            // 第 4 步：搜索也没结果 → AI 自主回答
            String answer = raw.replace(TAG_WEB, "").trim();
            resp.put("source", "ai_web");
            resp.put("sourceLabel", "💡 AI自主回答");
            resp.put("answer", answer);
            putInCache(cacheKey, resp);
            recordAsync(trimmed, "ai_web");
            return resp;

        } catch (HttpTimeoutException e) {
            log.warn("DeepSeek 超时，降级。keyword={}", trimmed);
            return fallbackToLocal(cacheKey,"timeout");
        } catch (RateLimitException e) {
            log.warn("DeepSeek 限流，降级。keyword={}", trimmed);
            return fallbackToLocal(cacheKey,"rate_limited");
        } catch (Exception e) {
            log.warn("DeepSeek 失败，降级。keyword={}, err={}", trimmed, e.getMessage());
            return fallbackToLocal(cacheKey,"exception:" + e.getClass().getSimpleName());
        }
    }

    // ===== AI 降级兜底 =====

    private Map<String, Object> fallbackToLocal(String keyword, String reason) {
        Map<String, Object> resp = new HashMap<>();
        resp.put("source", "fallback");
        resp.put("sourceLabel", "⚠️ AI不可用·本地兜底");
        resp.put("fallbackReason", reason);
        List<Knowledge> hits = knowledgeMapper.searchByKeyword(keyword);
        resp.put("answer", (hits != null && !hits.isEmpty())
                ? hits
                : "AI 暂不可用，知识库也未找到相关内容，请稍后重试或换个关键词。");
        putInCache(keyword, resp);
        recordAsync(keyword, "fallback");
        return resp;
    }

    // ===== Prompt 构建（RAG 版本） =====

    /**
     * 构建 RAG Prompt：把知识库内容注入 system prompt，
     * 让 AI 优先基于知识库回答，答案末尾打 [来源:知识库] 或 [来源:AI自主回答] 标记。
     */
    private String buildPrompt(String question, List<Knowledge> knowledgeList) {
        StringBuilder kb = new StringBuilder();
        if (knowledgeList != null && !knowledgeList.isEmpty()) {
            for (Knowledge k : knowledgeList) {
                kb.append("【").append(k.getTitle()).append("】")
                  .append(k.getContent());
                if (k.getKeywords() != null) {
                    kb.append("（关键词：").append(k.getKeywords()).append("）");
                }
                kb.append("\\n");
            }
        }

        String systemPrompt = "你是华南理工大学（SCUT）的校园生活智能助手。\\n"
                + "以下是校园知识库数据，请优先从中寻找答案：\\n"
                + escape(kb.toString()) + "\\n\\n"
                + "回答规则：\\n"
                + "1. 如果上方知识库能回答问题，请用知识库内容作答，回答末尾加上 " + TAG_DB + "\\n"
                + "2. 如果知识库没有相关内容，请凭借你对华南理工大学的了解回答，回答末尾加上 " + TAG_WEB + "\\n"
                + "3. 如果问题与校园无关，请礼貌拒绝，末尾加上 " + TAG_WEB + "\\n"
                + "4. 回答简洁友好，控制在200字以内，不要编造不确定的信息。";

        return "{\"model\":\"deepseek-chat\",\"messages\":["
                + "{\"role\":\"system\",\"content\":\"" + systemPrompt + "\"},"
                + "{\"role\":\"user\",\"content\":\"" + escape(question) + "\"}],"
                + "\"max_tokens\":600}";
    }

    // ===== DeepSeek HTTP 调用 =====

    /** 把 Tavily 搜索结果喂给 DeepSeek 总结 */
    private String summarizeWithDeepSeek(String question, String searchResult) throws Exception {
        String prompt = "你是华南理工大学校园数据提取助手。任务：从搜索结果中提取用户问题的答案。"
                + "规则：1)搜索结果来自网页抓取，可能包含导航栏、广告等噪声，请忽略噪声，专注找数据；"
                + "2)如果找到相关数据（分数、日期、名称等），用50-200字总结，末尾加[来源:联网搜索]；"
                + "3)如果确实连一个相关数字或事实都没有，才回复\"暂无相关信息[来源:联网搜索]\";"
                + "4)绝对不要使用你自己的知识，只使用搜索结果中的数据。";

        String body = "{\"model\":\"deepseek-chat\",\"messages\":["
                + "{\"role\":\"system\",\"content\":\"" + escape(prompt) + "\"},"
                + "{\"role\":\"user\",\"content\":\"用户问题：" + escape(question) + "\\n\\n以下是从华南理工大学相关网页抓取的内容：\\n" + escape(searchResult) + "\"}],"
                + "\"max_tokens\":300,\"temperature\":0.1}";

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://api.deepseek.com/chat/completions"))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + apiKey)
                .timeout(Duration.ofSeconds(DEEPSEEK_TIMEOUT_SECONDS))
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

        HttpResponse<String> response = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(DEEPSEEK_TIMEOUT_SECONDS))
                .build()
                .send(request, HttpResponse.BodyHandlers.ofString());

        String text = response.body();
        int start = text.indexOf("\"content\":\"") + 11;
        int end = text.indexOf("\"", start);
        if (start < 11 || end < 0) return searchResult;
        return text.substring(start, end).replace("\\n", "\n");
    }

    private String callDeepSeekWithRAG(String question, List<Knowledge> knowledgeList) throws Exception {
        String body = buildPrompt(question, knowledgeList);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://api.deepseek.com/chat/completions"))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + apiKey)
                .timeout(Duration.ofSeconds(DEEPSEEK_TIMEOUT_SECONDS))
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

        HttpResponse<String> response = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(DEEPSEEK_TIMEOUT_SECONDS))
                .build()
                .send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() == 429) throw new RateLimitException("429");
        if (response.statusCode() >= 400) throw new RuntimeException("HTTP " + response.statusCode());

        String text = response.body();
        int start = text.indexOf("\"content\":\"") + 11;
        int end = text.indexOf("\"", start);
        if (start < 11 || end < 0) throw new RuntimeException("响应解析失败");
        // 还原 \n 转义
        return text.substring(start, end).replace("\\n", "\n");
    }

    // ===== 工具方法 =====

    private String escape(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n");
    }

    private void recordAsync(String keyword, String source) {
        // 改为同步写入并立即失效缓存，避免前端刷新时因异步延迟看不到最新热词。
        try {
            QuestionLog log = new QuestionLog();
            log.setQuestion(keyword);
            log.setSource(source);
            log.setUserId(BaseContext.getCurrentUsername()); // 已登录用户记录学号/工号
            questionLogMapper.insertLog(log);
            hotspotService.invalidateCache();
        } catch (Exception ex) {
            QaService.log.warn("question_log 写入失败：{}", ex.getMessage());
        }
    }

    public List<String> getHotKeywords(int limit) {
        return hotKeywordMapper.findTopKeywordNames(limit);
    }

    public List<String> getRecentQuestions(String userId, int limit) {
        if (userId == null) return List.of();
        return questionLogMapper.findRecentByUserId(userId, limit)
                .stream()
                .map(QuestionLog::getQuestion)
                .collect(java.util.stream.Collectors.toList());
    }

    private static final List<String> CAMPUS_KEYWORDS = List.of(
        // 餐饮（含广东用语）
        "食堂", "饭堂", "餐厅", "餐饮", "打饭", "饭卡", "饭菜", "吃饭", "午饭", "早饭", "晚饭",
        // 住宿
        "宿舍", "寝室", "宿管", "住宿", "门禁", "洗澡", "热水", "晾衣",
        // 学习
        "图书馆", "课程", "选课", "考试", "成绩", "作业", "教室", "上课", "自习", "借书", "期末", "期中",
        // 校园服务
        "学费", "奖学金", "补贴", "打印", "快递", "校车", "医务室", "校医", "一卡通", "饭卡",
        // 人员
        "老师", "辅导员", "学生", "同学", "班级",
        // 学业
        "专业", "毕业", "实习", "社团", "活动", "校园",
        // 华工专属
        "华工", "华南理工", "scut", "五山", "大学城", "国际校区", "南校", "北校", "东校", "天河"
    );

    /**
     * 问题归一化：AI 语义解析 + 规则兜底
     * "饭堂几时开啊" → "食堂开放时间"
     * "华工寒假放到啥时候" → "寒假放假时间"
     */
    private String normalizeQuestion(String q) {
        // 先用手动规则快速归一（零成本）
        String rule = normalizeSynonym(q);
        // 如果规则归一化已经足够短且标准，直接用
        if (rule.length() >= 4 && !rule.equals(q)) return rule;
        // 否则尝试 AI 语义归一化（消耗一次轻量 API 调用）
        try {
            String prompt = "解析用户输入，输出核心实体和意图。格式：实体|意图。"
                    + "示例：'饭堂几时开'→食堂|时间 '华工寒假放多久'→寒假|时间 "
                    + "'图书馆在哪'→图书馆|地点 '一卡通怎么办'→一卡通|方式";
            String body = "{\"model\":\"deepseek-chat\",\"messages\":["
                    + "{\"role\":\"system\",\"content\":\"" + escape(prompt) + "\"},"
                    + "{\"role\":\"user\",\"content\":\"" + escape(q) + "\"}],"
                    + "\"max_tokens\":30,\"temperature\":0}";
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create("https://api.deepseek.com/chat/completions"))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + apiKey)
                    .timeout(Duration.ofSeconds(4))
                    .POST(HttpRequest.BodyPublishers.ofString(body)).build();
            HttpResponse<String> resp = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(4)).build()
                    .send(req, HttpResponse.BodyHandlers.ofString());
            String text = resp.body();
            int s = text.indexOf("\"content\":\"") + 11;
            int e = text.indexOf("\"", s);
            if (s > 10 && e > 0) {
                String parsed = text.substring(s, e).replace(" ", "").trim();
                if (parsed.contains("|")) {
                    String[] parts = parsed.split("\\|");
                    if (parts.length >= 2) return parts[0] + "|" + parts[1];
                }
                return rule.isEmpty() ? q : rule;
            }
        } catch (Exception ex) { log.debug("AI归一化失败，用手动规则"); }
        return rule.isEmpty() ? q : rule;
    }

    /** 同义词归一化：广东用语 + 校园口语 → 标准词 */
    private String normalizeSynonym(String question) {
        String q = question;
        // === 学校名称归一化（先保护"华南理工大学"，再处理缩写） ===
        q = q.replace("华南理工大学", "华南理工大学"); // no-op 占位，不做破坏性替换
        q = q.replace("scut", "华南理工大学").replace("SCUT", "华南理工大学");
        // === 场所 ===
        q = q.replace("饭堂", "食堂").replace("餐厅", "食堂").replace("吃饭的地方", "食堂");
        q = q.replace("寝室", "宿舍").replace("住宿", "宿舍");
        q = q.replace("校医", "医务室").replace("校医院", "医务室").replace("看病", "医务室");
        q = q.replace("图书室", "图书馆").replace("自习室", "图书馆");
        q = q.replace("班车", "校车").replace("校园巴士", "校车");
        q = q.replace("校园卡", "一卡通").replace("饭卡", "一卡通").replace("学生卡", "一卡通");
        q = q.replace("无线网", "校园网").replace("wifi", "校园网").replace("WiFi", "校园网");
        q = q.replace("打印店", "打印").replace("复印", "打印");
        q = q.replace("课室", "教室").replace("教学楼", "教室");
        // === 时间问法归一化 ===
        q = q.replace("什么时候", "时间").replace("啥时候", "时间").replace("几时", "时间")
         .replace("几点", "时间").replace("啥时", "时间").replace("何时", "时间");
        q = q.replace("寒假假期", "寒假放假").replace("寒假时间", "寒假放假");
        q = q.replace("暑假假期", "暑假放假").replace("暑假时间", "暑假放假");
        q = q.replace("放假安排", "放假").replace("放假日期", "放假");
        // === 地点问法归一化 ===
        q = q.replace("在哪里", "在哪").replace("什么地方", "在哪")
         .replace("位置在哪", "在哪").replace("地点在哪", "在哪").replace("位于", "在哪");
        // === 方式问法归一化 ===
        q = q.replace("如何", "怎么").replace("咋", "怎么").replace("怎样", "怎么");
        // === 费用问法归一化 ===
        q = q.replace("多少钱", "收费").replace("收费标准", "收费").replace("费用", "收费");
        // === 校园专属用语 ===
        q = q.replace("开放时间", "时间").replace("开门时间", "时间").replace("关门时间", "时间");
        q = q.replace("借书", "借阅").replace("还书", "借阅");
        q = q.replace("选课系统", "选课").replace("抢课", "选课");
        q = q.replace("奖学金申请", "奖学金").replace("助学金", "奖学金");
        q = q.replace("课程表", "课表").replace("课程安排", "课表");
        q = q.replace("校历", "放假");
        // === 去掉口语化噪音词 ===
        q = q.replace("请问", "").replace("我想问", "").replace("我想知道", "")
         .replace("告诉我", "").replace("知不知道", "").replace("知道吗", "")
         .replace("可以告诉我", "").replace("想问一下", "").replace("问一下", "");
        // === 去重空格 ===
        q = q.replaceAll("\\s+", "");
        return q;
    }

    private boolean isCampusRelated(String question) {
        String q = question.toLowerCase();
        return CAMPUS_KEYWORDS.stream().anyMatch(q::contains);
    }

    private static class RateLimitException extends RuntimeException {
        RateLimitException(String msg) { super(msg); }
    }
}
