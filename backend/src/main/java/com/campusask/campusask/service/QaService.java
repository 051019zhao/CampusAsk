package com.campusask.campusask.service;

import com.campusask.campusask.entity.Knowledge;
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
 * 赵丹丹 - 周3 任务1：API 降级兜底逻辑
 *
 * 检索策略：
 * 1) 优先调用 DeepSeek
 * 2) 超时 / 限流 (HTTP 429) / 其他异常 → 自动降级到本地 knowledge 表关键词检索
 *
 * 返回结构包含 source 字段标识来源：
 *   - "ai"        : DeepSeek 正常回答
 *   - "fallback"  : DeepSeek 失败 → 走本地兜底
 */
@Service
public class QaService {

    private static final Logger log = LoggerFactory.getLogger(QaService.class);

    /** DeepSeek 请求超时（秒），超过即触发降级 */
    private static final int DEEPSEEK_TIMEOUT_SECONDS = 8;

    @Autowired
    private KnowledgeMapper knowledgeMapper;

    @Value("${deepseek.api.key}")
    private String apiKey;

    public Map<String, Object> ask(String keyword) {
        Map<String, Object> resp = new HashMap<>();
        if (keyword == null || keyword.trim().isEmpty()) {
            resp.put("source", "fallback");
            resp.put("answer", "请输入问题");
            resp.put("fallbackReason", "empty keyword");
            return resp;
        }

        String trimmed = keyword.trim();

        // === 第 1 步：先查本地知识库 ===
        List<Knowledge> localHits = knowledgeMapper.searchByKeyword(trimmed);
        if (localHits != null && !localHits.isEmpty()) {
            resp.put("source", "knowledge");
            resp.put("answer", localHits);
            return resp;
        }

        // === 第 2 步：本地没有 → 尝试 DeepSeek ===
        try {
            String aiAnswer = callDeepSeek(trimmed);
            resp.put("source", "ai");
            resp.put("answer", aiAnswer);
            return resp;
        } catch (HttpTimeoutException e) {
            log.warn("DeepSeek 调用超时，启动降级。keyword={}", trimmed);
            return fallbackToLocal(trimmed, "timeout");
        } catch (RateLimitException e) {
            log.warn("DeepSeek 触发限流(429)，启动降级。keyword={}", trimmed);
            return fallbackToLocal(trimmed, "rate_limited");
        } catch (Exception e) {
            log.warn("DeepSeek 调用失败，启动降级。keyword={}, err={}", trimmed, e.getMessage());
            return fallbackToLocal(trimmed, "exception:" + e.getClass().getSimpleName());
        }
    }

    /** 走本地 knowledge 表搜索 */
    private Map<String, Object> fallbackToLocal(String keyword, String reason) {
        Map<String, Object> resp = new HashMap<>();
        resp.put("source", "fallback");
        resp.put("fallbackReason", reason);
        List<Knowledge> hits = knowledgeMapper.searchByKeyword(keyword);
        if (hits != null && !hits.isEmpty()) {
            resp.put("answer", hits);
        } else {
            resp.put("answer", "AI 暂不可用，且本地知识库未命中。请稍后再试或换个关键词。");
        }
        return resp;
    }

    /** 真实调用 DeepSeek，自带超时和 429 检测 */
    private String callDeepSeek(String question) throws Exception {
        String body = "{\"model\":\"deepseek-chat\",\"messages\":["
                + "{\"role\":\"system\",\"content\":\"你是一个校园生活助手，回答简洁友好。\"},"
                + "{\"role\":\"user\",\"content\":\"" + escape(question) + "\"}],"
                + "\"max_tokens\":500}";

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://api.deepseek.com/chat/completions"))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + apiKey)
                .timeout(Duration.ofSeconds(DEEPSEEK_TIMEOUT_SECONDS))
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(DEEPSEEK_TIMEOUT_SECONDS))
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        // 429 = Too Many Requests，DeepSeek 限流
        if (response.statusCode() == 429) {
            throw new RateLimitException("DeepSeek returned 429");
        }
        if (response.statusCode() >= 400) {
            throw new RuntimeException("DeepSeek HTTP " + response.statusCode());
        }

        String text = response.body();
        int start = text.indexOf("\"content\":\"") + 11;
        int end = text.indexOf("\"", start);
        if (start < 11 || end < 0) {
            throw new RuntimeException("DeepSeek 响应解析失败");
        }
        return text.substring(start, end);
    }

    private String escape(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    /** 自定义异常：用于识别 429 限流 */
    private static class RateLimitException extends RuntimeException {
        RateLimitException(String msg) { super(msg); }
    }
}
