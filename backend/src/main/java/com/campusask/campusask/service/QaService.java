package com.campusask.campusask.service;

import com.campusask.campusask.entity.Knowledge;
import com.campusask.campusask.entity.QuestionLog;
import com.campusask.campusask.mapper.HotKeywordMapper;
import com.campusask.campusask.mapper.KnowledgeMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
    private HotKeywordMapper hotKeywordMapper;

    @Autowired
    private com.campusask.campusask.mapper.QuestionLogMapper questionLogMapper;

    @Value("${deepseek.api.key}")
    private String apiKey;

    // ===== 主入口 =====

    public Map<String, Object> ask(String keyword) {
        Map<String, Object> resp = new HashMap<>();
        if (keyword == null || keyword.trim().isEmpty()) {
            resp.put("source", "fallback");
            resp.put("answer", "请输入问题");
            return resp;
        }
        String trimmed = keyword.trim();

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
            recordAsync(trimmed, "knowledge");
            return resp;
        }

        // 第 2+3 步：调 AI（RAG：把全部知识库作为上下文）
        List<Knowledge> allKnowledge = knowledgeMapper.listAll();
        try {
            String raw = callDeepSeekWithRAG(trimmed, allKnowledge);

            if (raw.contains(TAG_DB)) {
                // AI 从知识库上下文中找到了答案
                String answer = raw.replace(TAG_DB, "").trim();
                resp.put("source", "ai_knowledge");
                resp.put("sourceLabel", "🤖 AI检索数据库");
                resp.put("answer", answer);
                recordAsync(trimmed, "ai_knowledge");
            } else {
                // AI 用自身知识（华南理工）回答
                String answer = raw.replace(TAG_WEB, "").trim();
                resp.put("source", "ai_web");
                resp.put("sourceLabel", "🌐 AI自主回答(华南理工)");
                resp.put("answer", answer);
                recordAsync(trimmed, "ai_web");
            }
            return resp;

        } catch (HttpTimeoutException e) {
            log.warn("DeepSeek 超时，降级。keyword={}", trimmed);
            return fallbackToLocal(trimmed, "timeout");
        } catch (RateLimitException e) {
            log.warn("DeepSeek 限流，降级。keyword={}", trimmed);
            return fallbackToLocal(trimmed, "rate_limited");
        } catch (Exception e) {
            log.warn("DeepSeek 失败，降级。keyword={}, err={}", trimmed, e.getMessage());
            return fallbackToLocal(trimmed, "exception:" + e.getClass().getSimpleName());
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
        java.util.concurrent.CompletableFuture.runAsync(() -> {
            try {
                QuestionLog log = new QuestionLog();
                log.setQuestion(keyword);
                log.setSource(source);
                questionLogMapper.insertLog(log);
            } catch (Exception ex) {
                QaService.log.warn("question_log 写入失败：{}", ex.getMessage());
            }
        });
    }

    public List<String> getHotKeywords(int limit) {
        return hotKeywordMapper.findTopKeywords(limit)
                .stream()
                .map(item -> item.getKeyword())
                .collect(java.util.stream.Collectors.toList());
    }

    public List<String> getRecentQuestions(Long userId, int limit) {
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

    /** 同义词归一化，让数据库 LIKE 检索能匹配广东用语 */
    private String normalizeSynonym(String question) {
        return question
                .replace("饭堂", "食堂")
                .replace("寝室", "宿舍")
                .replace("校医", "医务室")
                .replace("图书馆", "图书馆");
    }

    private boolean isCampusRelated(String question) {
        String q = question.toLowerCase();
        return CAMPUS_KEYWORDS.stream().anyMatch(q::contains);
    }

    private static class RateLimitException extends RuntimeException {
        RateLimitException(String msg) { super(msg); }
    }
}
