package com.campusask.campusask.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * Tavily 联网搜索 Service
 * 当知识库和 AI RAG 都找不到答案时，调用 Tavily 搜索华南理工大学相关信息
 */
@Service
public class SearchService {

    private static final Logger log = LoggerFactory.getLogger(SearchService.class);
    private static final String TAVILY_URL = "https://api.tavily.com/search";

    @Value("${tavily.api.key}")
    private String apiKey;

    /**
     * 搜索华南理工大学相关信息
     * @param question 用户问题
     * @return 搜索结果摘要，失败返回 null
     */
    public String search(String question) {
        try {
            String query = "华南理工大学 " + question;
            String body = "{"
                    + "\"api_key\":\"" + apiKey + "\","
                    + "\"query\":\"" + escapeJson(query) + "\","
                    + "\"search_depth\":\"basic\","
                    + "\"max_results\":3,"
                    + "\"include_answer\":true"
                    + "}";

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(TAVILY_URL))
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofSeconds(10))
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();

            HttpResponse<String> response = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(10))
                    .build()
                    .send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                log.warn("Tavily 搜索失败，状态码：{}", response.statusCode());
                return null;
            }

            return extractResult(response.body());

        } catch (Exception e) {
            log.warn("Tavily 搜索异常：{}", e.getMessage());
            return null;
        }
    }

    /**
     * 从 Tavily 响应中提取有用内容
     * 优先用 answer 字段，其次拼接 results 的 content
     */
    private String extractResult(String json) {
        // 先尝试提取 answer 字段（Tavily 自动生成的摘要）
        int answerStart = json.indexOf("\"answer\":\"");
        if (answerStart >= 0) {
            answerStart += 10;
            int answerEnd = json.indexOf("\"", answerStart);
            if (answerEnd > answerStart) {
                String answer = json.substring(answerStart, answerEnd);
                if (!answer.isBlank() && answer.length() > 10) {
                    return answer.replace("\\n", "\n");
                }
            }
        }

        // answer 为空则拼接前 3 条 content
        StringBuilder sb = new StringBuilder();
        int pos = 0;
        int count = 0;
        while (count < 3) {
            int contentIdx = json.indexOf("\"content\":\"", pos);
            if (contentIdx < 0) break;
            contentIdx += 11;
            int contentEnd = json.indexOf("\"", contentIdx);
            if (contentEnd < 0) break;
            String snippet = json.substring(contentIdx, contentEnd);
            if (!snippet.isBlank()) {
                sb.append(snippet).append(" ");
                count++;
            }
            pos = contentEnd + 1;
        }

        return sb.length() > 0 ? sb.toString().trim() : null;
    }

    private String escapeJson(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
