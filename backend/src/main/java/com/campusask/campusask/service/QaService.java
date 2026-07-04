package com.campusask.campusask.service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import jakarta.annotation.PostConstruct;

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
 * 五级检索策略：
 *   0. 校园关键词拦截（非校园问题拒绝）
 *   1. 数据库关键词检索（LIKE）              → source: knowledge
 *   2. AI 检索数据库（RAG + Top-K 预筛选）    → source: ai_knowledge
 *   3. Tavily 联网搜索                        → source: ai_search
 *   4. AI 自主回答（基于华南理工大学知识）     → source: ai_web
 *
 * AI 故障降级：超时/限流 → 回退到步骤1的关键词结果，source: fallback
 *
 * V3 新增特性：
 *   - RAG Top-K 预筛选（Jaccard 相似度），只送最相关条目给 AI
 *   - 多轮对话上下文支持（基于 sessionId）
 *   - 回答后自动推荐相关问题
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

    @Autowired
    private com.campusask.campusask.mapper.KnowledgeGapMapper knowledgeGapMapper;

    @Value("${deepseek.api.key}")
    private String apiKey;

    // ===== V3: 启动后异步预热热点问题缓存（不阻塞启动）=====
    @PostConstruct
    public void warmUpCache() {
        new Thread(() -> {
            try {
                Thread.sleep(3000); // 等应用完全启动后再预热
                List<String> hotKeywords = hotKeywordMapper.findTopKeywordNames(10);
                if (hotKeywords.isEmpty()) {
                    hotKeywords = new ArrayList<>(STATIC_FAQ.keySet());
                }
                int warmed = 0;
                for (String kw : hotKeywords) {
                    if (kw == null || kw.isBlank()) continue;
                    try {
                        ask(kw, null);
                        warmed++;
                    } catch (Exception ignored) { /* 预热失败不影响 */ }
                }
                log.info("缓存预热完成：{} 个热点问题已加载", warmed);
            } catch (Exception e) {
                log.warn("缓存预热异常：{}", e.getMessage());
            }
        }, "cache-warmup").start();
    }

    // ===== 回答缓存 =====
    private static final int CACHE_MAX_SIZE = 100;
    private static final long CACHE_TTL_MS = 5 * 60 * 1000; // 5分钟
    private final ConcurrentHashMap<String, CacheEntry> cache = new ConcurrentHashMap<>();

    // ===== RAG Top-K 预筛选配置 =====
    private static final int RAG_TOP_K = 5;              // 最多取 top-5 相关条目送入 prompt
    private static final double RAG_MIN_SIMILARITY = 0.05; // 最低相似度阈值，低于此值的条目不送入

    // ===== 多轮对话会话缓存 =====
    private static final int MAX_SESSION_ROUNDS = 3;      // 最多保留 3 轮对话历史
    private static final long SESSION_TTL_MS = 10 * 60 * 1000; // 会话有效期 10 分钟
    private final ConcurrentHashMap<String, SessionEntry> sessions = new ConcurrentHashMap<>();

    private static class CacheEntry {
        final Map<String, Object> data;
        final long createdAt;
        CacheEntry(Map<String, Object> data) { this.data = data; this.createdAt = System.currentTimeMillis(); }
        boolean expired() { return System.currentTimeMillis() - createdAt > CACHE_TTL_MS; }
    }

    private static class SessionEntry {
        final LinkedList<Map.Entry<String, String>> history = new LinkedList<>();
        volatile long lastAccess = System.currentTimeMillis();
        boolean expired() { return System.currentTimeMillis() - lastAccess > SESSION_TTL_MS; }
        synchronized void addRound(String question, String answer) {
            if (history.size() >= MAX_SESSION_ROUNDS) history.removeFirst();
            history.add(new AbstractMap.SimpleEntry<>(question, answer));
            lastAccess = System.currentTimeMillis();
        }
    }

    // ===== 静态兜底 FAQ（最高频 20 问，即使 DB 出问题也能回答）=====
    private static final Map<String, String> STATIC_FAQ = new LinkedHashMap<>();
    static {
        STATIC_FAQ.put("食堂开放时间", "食堂开放时间——早餐：7:00-9:00，午餐：11:00-13:00，晚餐：17:00-19:00。具体各食堂可能有微调，请以食堂门口公告为准。");
        STATIC_FAQ.put("图书馆开放时间", "华南理工大学图书馆开放时间为 7:00-23:00（周一至周日），节假日另行通知。五山校区、大学城校区、国际校区图书馆均按此时间运行。");
        STATIC_FAQ.put("图书馆借书规则", "每人最多借阅 5 本书，借阅期限为 30 天，可续借一次（续借 15 天）。逾期将按 0.1 元/天/册收取滞纳金。");
        STATIC_FAQ.put("校车时刻表", "校车每天 7:00、12:00、18:00 从正门发车，终点为地铁站，全程约 20 分钟。高峰期可能增加班次。");
        STATIC_FAQ.put("打印在哪里", "校内打印点位于图书馆一楼和教学楼 B 栋 101，支持 A4 黑白和彩色打印。黑白 0.1 元/页，彩色 0.5 元/页。");
        STATIC_FAQ.put("宿舍门禁时间", "学生宿舍门禁时间为 23:30（周日-周四）和 24:00（周五-周六）。晚归需登记，累计多次将通报辅导员。");
        STATIC_FAQ.put("一卡通补办", "一卡通丢失后请先在「华工一卡通」公众号挂失，然后携带身份证到信息楼一楼服务大厅补办，工本费 15 元。");
        STATIC_FAQ.put("校园网连接", "连接 SCUT-WiFi 后，使用学号和校园网密码登录。如无法连接，可致电信息中心：020-87110000。");
        STATIC_FAQ.put("选课时间", "选课分为预选、正选和补退选三个阶段，具体时间以教务处通知为准。一般开学前一周开放预选。");
        STATIC_FAQ.put("学费缴纳", "学费通过「华南理工大学财务处」公众号或学校统一支付平台缴纳，一般在开学前两周开放缴费通道。");
        STATIC_FAQ.put("奖学金申请", "奖学金每年 9-10 月集中申请，包括国家奖学金、校级奖学金和社会奖学金。具体条件请参考学生手册或咨询辅导员。");
        STATIC_FAQ.put("医务室开放时间", "校医院 24 小时急诊，普通门诊时间为 8:00-12:00、14:30-17:30（周一至周五）。校医院电话：020-87110000。");
        STATIC_FAQ.put("快递在哪里取", "五山校区快递点在东区篮球场旁菜鸟驿站，大学城校区在生活区 C 栋楼下，国际校区在学生公寓一楼。凭取件码取件。");
        STATIC_FAQ.put("社团怎么加入", "每学期开学第一周为「社团招新周」，可在各校区广场参加社团嘉年华现场报名，也可关注「华工社团联合会」公众号在线报名。");
        STATIC_FAQ.put("考试安排查询", "期末考试安排一般在第 16 周前后公布，登录教务系统→考试安排即可查看。如有冲突需提前一周到教务处申请调整。");
        STATIC_FAQ.put("宿舍报修", "宿舍报修可通过「华工后勤」公众号在线报修，或到宿管处填写报修单。一般维修 1-3 个工作日内处理，紧急情况可致电后勤 24 小时热线。");
        STATIC_FAQ.put("体育场馆预约", "体育场馆（羽毛球、乒乓球、游泳馆）通过「华工体育」公众号在线预约，最早可提前 3 天预约，每人每天限约 1 小时。");
        STATIC_FAQ.put("心理辅导咨询", "心理健康教育与咨询中心位于行政楼 2 楼，预约电话 020-87110000，周一至周五 8:00-17:30。所有咨询均免费且严格保密。");
        STATIC_FAQ.put("毕业实习要求", "本科毕业实习一般安排在大四第一学期，要求不少于 4 周。实习单位可由学生自行联系或学校推荐，实习结束后需提交实习报告。");
        STATIC_FAQ.put("国际校区怎么去", "国际校区位于广州市番禺区，可乘坐地铁 4 号线至大学城南站后换乘公交，或在校本部乘坐校车直达。自驾约 40 分钟。");
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

    // ===== 主入口（无 sessionId，向后兼容）=====

    public Map<String, Object> ask(String keyword) {
        return ask(keyword, null);
    }

    /**
     * 多轮对话版本：支持 sessionId，前端传会话 ID 即可关联上下文
     */
    public Map<String, Object> ask(String keyword, String sessionId) {
        Map<String, Object> resp = new HashMap<>();
        if (keyword == null || keyword.trim().isEmpty()) {
            resp.put("source", "fallback");
            resp.put("answer", "请输入问题");
            return resp;
        }
        // 归一化：去多余空白、去标点
        String trimmed = keyword.trim().replaceAll("[\\s\\p{Z}]+", "");

        // 判断是否有多轮对话历史（有历史才跳缓存）
        boolean hasSessionHistory = sessionId != null && !sessionId.isEmpty()
                && sessions.containsKey(sessionId) && !sessions.get(sessionId).history.isEmpty();

        // 第 -3 步：快速缓存检查（纯规则归一化，零 API 调用，毫秒级响应）
        String quickKey = normalizeSynonym(trimmed);
        Map<String, Object> cached = hasSessionHistory ? null : getFromCache(quickKey);
        if (cached != null) {
            recordAsync(quickKey, String.valueOf(cached.get("source")));
            addSuggestedQuestions(cached, trimmed);
            updateSession(sessionId, trimmed, String.valueOf(cached.getOrDefault("answer", "")));
            return cached;
        }

        // 第 -2 步：AI 语义归一化（缓存未命中才执行，消耗 API 调用）
        String cacheKey = normalizeQuestion(trimmed);
        if (!cacheKey.equals(quickKey)) {
            // 用 AI 归一化后的 key 再查一次缓存（可能之前同一语义的问题已缓存）
            cached = hasSessionHistory ? null : getFromCache(cacheKey);
            if (cached != null) {
                recordAsync(cacheKey, String.valueOf(cached.get("source")));
                addSuggestedQuestions(cached, trimmed);
                updateSession(sessionId, trimmed, String.valueOf(cached.getOrDefault("answer", "")));
                return cached;
            }
        }

        // 第 0 步：非校园问题直接拒绝（有会话上下文时跳过，让 AI 用上下文理解）
        if (!hasSessionHistory && !isCampusRelated(trimmed)) {
            resp.put("source", "rejected");
            resp.put("sourceLabel", "🚫 超出服务范围");
            resp.put("answer", "抱歉，我只能回答华南理工大学校园相关的问题哦～ 比如食堂、宿舍、图书馆、课程、考试等，欢迎继续提问！");
            addSuggestedQuestions(resp, trimmed);
            return resp;
        }

        // 第 1 步：数据库关键词 LIKE 检索（有会话上下文时跳过，直奔 RAG 用上下文理解指代）
        String searchKeyword = normalizeSynonym(trimmed);
        if (!hasSessionHistory) {
            List<Knowledge> localHits = knowledgeMapper.searchByKeyword(searchKeyword);
            if (localHits != null && !localHits.isEmpty()) {
                resp.put("source", "knowledge");
                resp.put("sourceLabel", "📚 数据库关键词检索");
                resp.put("answer", localHits);
                putInCache(cacheKey, resp);
                recordAsync(trimmed, "knowledge");
                addSuggestedQuestions(resp, trimmed);
                updateSession(sessionId, trimmed, localHits.get(0).getTitle() + "：" + localHits.get(0).getContent());
                return resp;
            }
        }

        // 第 2 步：AI 检索数据库（RAG + Top-K 预筛选）
        List<Knowledge> allKnowledge = knowledgeMapper.listAll();
        // ★★★ V3 核心优化：只取 Top-K 最相关条目送入 prompt
        List<Knowledge> ragKnowledge = selectTopK(trimmed, allKnowledge, RAG_TOP_K);
        log.info("RAG Top-K：全量 {} 条 → 筛选后 {} 条", allKnowledge.size(), ragKnowledge.size());
        try {
            // 如果有多轮对话历史，传入上下文
            String contextPrompt = buildSessionContext(sessionId);
            String raw = callDeepSeekWithRAG(trimmed, ragKnowledge, contextPrompt);

            if (raw.contains(TAG_DB)) {
                String answer = raw.replace(TAG_DB, "").trim();
                resp.put("source", "ai_knowledge");
                resp.put("sourceLabel", "🤖 AI检索数据库（智能匹配）");
                resp.put("answer", answer);
                putInCache(cacheKey, resp);
                recordAsync(trimmed, "ai_knowledge");
                addSuggestedQuestions(resp, trimmed);
                updateSession(sessionId, trimmed, answer);
                return resp;
            }

            // 第 3 步：RAG 也没找到 → 调 Tavily 联网搜索
            String searchResult = searchService.search(trimmed);
            if (searchResult != null && !searchResult.isBlank()) {
                List<String> urls = searchService.getLastUrls();
                String summary = summarizeWithDeepSeek(trimmed, searchResult);
                if (summary != null && !summary.isBlank()
                        && !summary.contains("暂无相关")) {
                    resp.put("source", "ai_search");
                    resp.put("sourceLabel", "🌐 AI联网搜索(华南理工)");
                    resp.put("answer", summary);
                    resp.put("searchUrls", urls);
                    putInCache(cacheKey, resp);
                    recordAsync(trimmed, "ai_search");
                    recordGapAsync(trimmed, summary, "ai_search",
                            urls != null ? String.join(",", urls) : null);
                    addSuggestedQuestions(resp, trimmed);
                    updateSession(sessionId, trimmed, summary);
                    return resp;
                }
                if (!urls.isEmpty()) {
                    resp.put("source", "ai_search");
                    resp.put("sourceLabel", "🌐 AI联网搜索(华南理工)");
                    resp.put("answer", "以下网页可能包含您需要的信息：\n\n"
                            + searchService.extractResultTitles(searchResult));
                    resp.put("searchUrls", urls);
                    putInCache(cacheKey, resp);
                    recordAsync(trimmed, "ai_search");
                    addSuggestedQuestions(resp, trimmed);
                    updateSession(sessionId, trimmed, "搜索结果列表");
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
            recordGapAsync(trimmed, answer, "ai_web", null);
            addSuggestedQuestions(resp, trimmed);
            updateSession(sessionId, trimmed, answer);
            return resp;

        } catch (HttpTimeoutException e) {
            log.warn("DeepSeek 超时，降级。keyword={}", trimmed);
            return fallbackToLocal(cacheKey, "timeout");
        } catch (RateLimitException e) {
            log.warn("DeepSeek 限流，降级。keyword={}", trimmed);
            return fallbackToLocal(cacheKey, "rate_limited");
        } catch (Exception e) {
            log.warn("DeepSeek 失败，降级。keyword={}, err={}", trimmed, e.getMessage());
            return fallbackToLocal(cacheKey, "exception:" + e.getClass().getSimpleName());
        }
    }

    // ===== AI 降级兜底 =====

    private Map<String, Object> fallbackToLocal(String keyword, String reason) {
        Map<String, Object> resp = new HashMap<>();
        resp.put("source", "fallback");
        resp.put("sourceLabel", "⚠️ AI不可用·本地兜底");
        resp.put("fallbackReason", reason);
        List<Knowledge> hits = knowledgeMapper.searchByKeyword(keyword);
        if (hits != null && !hits.isEmpty()) {
            resp.put("answer", hits);
        } else {
            // ★ V3 新增：尝试静态 FAQ 兜底
            String staticAnswer = getStaticFaqAnswer(keyword);
            if (staticAnswer != null) {
                resp.put("answer", staticAnswer);
                resp.put("sourceLabel", "⚠️ AI不可用·静态FAQ兜底");
            } else {
                resp.put("answer", "AI 暂不可用，知识库也未找到相关内容，请稍后重试或换个关键词。");
            }
        }
        addSuggestedQuestions(resp, keyword);
        putInCache(keyword, resp);
        recordAsync(keyword, "fallback");
        recordGapAsync(keyword,
                resp.get("answer") instanceof String ? (String) resp.get("answer") : "AI不可用",
                "fallback", null);
        return resp;
    }

    // ===== Prompt 构建（RAG 版本，支持多轮上下文）=====

    /**
     * 构建 RAG Prompt：把知识库内容注入 system prompt，
     * 让 AI 优先基于知识库回答，答案末尾打 [来源:知识库] 或 [来源:AI自主回答] 标记。
     */
    private String buildPrompt(String question, List<Knowledge> knowledgeList) {
        return buildPrompt(question, knowledgeList, null);
    }

    private String buildPrompt(String question, List<Knowledge> knowledgeList, String sessionContext) {
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

        StringBuilder systemPrompt = new StringBuilder();
        systemPrompt.append("你是华南理工大学（SCUT）的校园生活智能助手。\\n");

        // ★ V3 新增：多轮对话上下文
        if (sessionContext != null && !sessionContext.isEmpty()) {
            systemPrompt.append("【对话历史】请务必结合上文理解用户当前问题的指代关系（如'那个'、'它'、'还有呢'等）：\\n");
            systemPrompt.append(escape(sessionContext)).append("\\n");
            systemPrompt.append("用户当前问题可能是对上文的追问，请先判断是否与历史话题相关，如是则基于历史话题回答。\\n\\n");
        }

        systemPrompt.append("以下是校园知识库数据，请优先从中寻找答案：\\n");
        systemPrompt.append(escape(kb.toString())).append("\\n\\n");
        systemPrompt.append("回答规则：\\n");
        systemPrompt.append("1. 如果上方知识库能回答问题，请用知识库内容作答，回答末尾加上 ").append(TAG_DB).append("\\n");
        systemPrompt.append("2. 如果知识库没有相关内容，请凭借你对华南理工大学的了解回答，回答末尾加上 ").append(TAG_WEB).append("\\n");
        systemPrompt.append("3. 如果问题与校园无关，请礼貌拒绝，末尾加上 ").append(TAG_WEB).append("\\n");
        systemPrompt.append("4. 回答简洁友好，控制在200字以内，不要编造不确定的信息。");

        return "{\"model\":\"deepseek-chat\",\"messages\":["
                + "{\"role\":\"system\",\"content\":\"" + systemPrompt.toString() + "\"},"
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
        return callDeepSeekWithRAG(question, knowledgeList, null);
    }

    private String callDeepSeekWithRAG(String question, List<Knowledge> knowledgeList, String sessionContext) throws Exception {
        String body = buildPrompt(question, knowledgeList, sessionContext);

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

    /** V3 新增：AI/联网搜索的问答自动记录到知识缺口表，供管理员审核入库 */
    private void recordGapAsync(String question, String answer, String source, String urls) {
        try {
            com.campusask.campusask.entity.KnowledgeGap gap =
                    new com.campusask.campusask.entity.KnowledgeGap();
            gap.setQuestion(question.length() > 500 ? question.substring(0, 500) : question);
            gap.setAnswer(answer);
            gap.setSource(source);
            gap.setUrls(urls);
            knowledgeGapMapper.upsertGap(gap);
            log.info("知识缺口已记录：source={}, question={}", source,
                    question.length() > 30 ? question.substring(0, 30) + "…" : question);
        } catch (Exception ex) {
            QaService.log.warn("knowledge_gap 写入失败：{}", ex.getMessage());
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

    // ===== V3 新增：RAG Top-K 预筛选（Jaccard 相似度）=====

    /**
     * 从全量知识库中筛选出与问题最相关的 topK 条记录
     * 使用 Jaccard 相似度（无需引入 NLP 依赖），零成本粗排
     */
    List<Knowledge> selectTopK(String question, List<Knowledge> allKnowledge, int topK) {
        if (allKnowledge == null || allKnowledge.isEmpty()) return List.of();
        if (allKnowledge.size() <= topK) return allKnowledge;

        // 计算所有条目的相似度并排序
        List<Knowledge> filtered = allKnowledge.stream()
                .map(item -> new AbstractMap.SimpleEntry<>(item,
                        textSimilarity(question, item.getTitle() + " " +
                                (item.getContent() != null ? item.getContent() : "") + " " +
                                (item.getKeywords() != null ? item.getKeywords() : ""))))
                .filter(entry -> entry.getValue() >= RAG_MIN_SIMILARITY)
                .sorted((a, b) -> Double.compare(b.getValue(), a.getValue()))
                .limit(topK)
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());

        // 兜底：如果全部被阈值过滤，至少送 top-K 最高分的条目
        if (filtered.isEmpty()) {
            filtered = allKnowledge.stream()
                    .map(item -> new AbstractMap.SimpleEntry<>(item,
                            textSimilarity(question, item.getTitle() + " " +
                                    (item.getContent() != null ? item.getContent() : "") + " " +
                                    (item.getKeywords() != null ? item.getKeywords() : ""))))
                    .sorted((a, b) -> Double.compare(b.getValue(), a.getValue()))
                    .limit(topK)
                    .map(Map.Entry::getKey)
                    .collect(Collectors.toList());
        }

        return filtered;
    }

    /**
     * 简单分词：按中文字符逐字、英文按空格拆分
     * 中文用 bigram（二字组合）提高匹配度
     */
    private Set<String> tokenize(String text) {
        Set<String> tokens = new HashSet<>();
        if (text == null || text.isEmpty()) return tokens;

        // 英文部分按空格和标点拆分
        String[] words = text.toLowerCase().split("[^a-z0-9\\u4e00-\\u9fff]+");
        for (String word : words) {
            if (word.isEmpty()) continue;
            if (word.matches("[\\u4e00-\\u9fff]+")) {
                // 中文逐字 + bigram
                for (int i = 0; i < word.length(); i++) {
                    tokens.add(String.valueOf(word.charAt(i)));
                    if (i + 1 < word.length()) {
                        tokens.add(word.substring(i, i + 2));
                    }
                }
            } else {
                tokens.add(word);
            }
        }
        return tokens;
    }

    /** Jaccard 相似度：|A ∩ B| / |A ∪ B| */
    private double textSimilarity(String q, String doc) {
        Set<String> qSet = tokenize(q);
        Set<String> dSet = tokenize(doc);
        if (qSet.isEmpty() || dSet.isEmpty()) return 0.0;

        Set<String> intersection = new HashSet<>(qSet);
        intersection.retainAll(dSet);

        Set<String> union = new HashSet<>(qSet);
        union.addAll(dSet);

        return union.isEmpty() ? 0.0 : (double) intersection.size() / union.size();
    }

    // ===== V3 新增：多轮对话上下文 =====

    /** 构建多轮对话上下文 prompt */
    private String buildSessionContext(String sessionId) {
        if (sessionId == null || sessionId.isEmpty()) return null;
        SessionEntry session = sessions.get(sessionId);
        if (session == null || session.history.isEmpty()) return null;

        // 清理过期会话
        if (session.expired()) {
            sessions.remove(sessionId);
            return null;
        }

        StringBuilder ctx = new StringBuilder();
        for (Map.Entry<String, String> round : session.history) {
            ctx.append("用户：").append(round.getKey()).append("\\n");
            ctx.append("助手：").append(truncateAnswer(round.getValue(), 80)).append("\\n");
        }
        log.info("会话 {} 加载 {} 轮对话历史", sessionId, session.history.size());
        return ctx.toString();
    }

    /** 更新会话历史 */
    private void updateSession(String sessionId, String question, String answer) {
        if (sessionId == null || sessionId.isEmpty()) return;
        // 清理过期会话
        sessions.entrySet().removeIf(e -> e.getValue().expired());
        SessionEntry session = sessions.computeIfAbsent(sessionId, k -> new SessionEntry());
        session.addRound(question, answer);
    }

    /** 截断过长答案用于历史摘要 */
    private String truncateAnswer(String answer, int maxLen) {
        if (answer == null) return "";
        return answer.length() > maxLen ? answer.substring(0, maxLen) + "…" : answer;
    }

    /** 清除指定会话（前端可调用清除上下文） */
    public void clearSession(String sessionId) {
        if (sessionId != null) {
            sessions.remove(sessionId);
            log.info("会话 {} 已清除", sessionId);
        }
    }

    // ===== V3 新增：相关问题推荐 =====

    /**
     * 基于用户问题推荐 3 个相关问题，附加到响应中
     * 从知识库标题中查找语义相关的问题
     */
    private void addSuggestedQuestions(Map<String, Object> resp, String question) {
        try {
            List<Knowledge> all = knowledgeMapper.listAll();
            if (all == null || all.isEmpty()) return;

            String normalizedQ = normalizeSynonym(question);

            List<String> suggestions = all.stream()
                    .map(k -> new AbstractMap.SimpleEntry<>(k.getTitle(),
                            textSimilarity(normalizedQ, k.getTitle() + " " +
                                    (k.getContent() != null ? k.getContent() : ""))))
                    .filter(e -> e.getValue() >= 0.05) // 最低相关性阈值
                    .sorted((a, b) -> Double.compare(b.getValue(), a.getValue()))
                    .limit(3)
                    .map(Map.Entry::getKey)
                    .collect(Collectors.toList());

            if (!suggestions.isEmpty()) {
                resp.put("suggestions", suggestions);
                resp.put("suggestionLabel", "💡 你可能还想问：");
            }
        } catch (Exception e) {
            log.debug("生成推荐问题失败：{}", e.getMessage());
        }
    }

    // ===== V3 新增：静态 FAQ 兜底 =====

    /** 从预置静态 FAQ 中模糊匹配答案 */
    private String getStaticFaqAnswer(String keyword) {
        if (keyword == null) return null;
        String bestMatch = null;
        double bestScore = 0.0;

        for (Map.Entry<String, String> entry : STATIC_FAQ.entrySet()) {
            double score = textSimilarity(keyword, entry.getKey());
            if (score > bestScore && score >= 0.1) {
                bestScore = score;
                bestMatch = entry.getValue();
            }
            // 如果问题直接包含 FAQ 标题的关键词
            if (keyword.contains(entry.getKey().substring(0,
                    Math.min(entry.getKey().length(), 4)))) {
                if (bestScore < 0.3) {
                    bestMatch = entry.getValue();
                    bestScore = 0.3;
                }
            }
        }
        return bestMatch;
    }

    /** 知识库变更时清除相关缓存 */
    public void invalidateRelatedCache(String title) {
        if (title == null) return;
        // 清除可能包含该标题的所有缓存（简单策略：全清）
        cache.clear();
        log.info("知识库变更，已清除全部问答缓存");
    }

    // ===== V3 新增：问题意图分类（差异化检索策略）=====

    private static final Set<String> TIME_TOKENS = Set.of(
            "几点", "几时", "时间", "什么时候", "何时", "营业", "开放", "开门", "关门", "时刻"
    );
    private static final Set<String> LOCATION_TOKENS = Set.of(
            "在哪", "哪里", "在哪儿", "地点", "位置", "位于", "怎么走", "在什么地方"
    );
    private static final Set<String> PROCESS_TOKENS = Set.of(
            "怎么", "如何", "怎么办", "怎样", "申请", "补办", "办理", "流程", "步骤"
    );

    /**
     * 问题意图分类：time / location / process / fact
     * 未来可根据类型调整检索策略（如时间类优先数据库，方式类优先 AI）
     */
    private String classifyQuestion(String q) {
        if (q == null) return "fact";
        String lq = q.toLowerCase();
        if (TIME_TOKENS.stream().anyMatch(lq::contains)) return "time";
        if (LOCATION_TOKENS.stream().anyMatch(lq::contains)) return "location";
        if (PROCESS_TOKENS.stream().anyMatch(lq::contains)) return "process";
        return "fact";
    }

    private static class RateLimitException extends RuntimeException {
        RateLimitException(String msg) { super(msg); }
    }
}
