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
import java.util.ArrayList;
import java.util.List;

/**
 * Tavily 联网搜索 Service - 双轮搜索机制
 * 第一轮：精准搜索华南理工大学官网（scut.edu.cn）
 * 第二轮：如果第一轮结果不足，放宽到全网搜索
 */
@Service
public class SearchService {

    private static final Logger log = LoggerFactory.getLogger(SearchService.class);
    private static final String TAVILY_URL = "https://api.tavily.com/search";

    @Value("${tavily.api.key}")
    private String apiKey;

    /** 最后一次搜索的原始响应，用于提取 URL */
    private String lastRawJson;

    /**
     * 双轮搜索：先精准后泛搜
     */
    public String search(String question) {
        // 预处理搜索词
        String cleaned = cleanQuery(question);
        String preciseQuery = "华南理工大学 " + cleaned;

        // 第一轮：精准搜索华工官网
        String result = doSearch(preciseQuery, true);
        if (result != null && result.length() > 50) {
            log.info("第一轮精准搜索命中，结果长度={}", result.length());
            return result;
        }

        // 第二轮：放宽到全网搜索
        log.info("第一轮精准搜结果不足，启动第二轮全网搜索");
        String broadResult = doSearch(preciseQuery, false);
        if (broadResult != null && broadResult.length() > 20) {
            return broadResult;
        }

        // 两轮都不行，纯关键词搜索
        String bareQuery = extractKeywords(cleaned);
        if (!bareQuery.equals(cleaned)) {
            log.info("尝试纯关键词搜索：{}", bareQuery);
            String bareResult = doSearch(bareQuery, false);
            if (bareResult != null && bareResult.length() > 20) {
                return bareResult;
            }
        }
        return null;
    }

    /** 获取最后一次搜索的来源 URL 列表 */
    public List<String> getLastUrls() {
        return lastRawJson != null ? extractUrls(lastRawJson) : List.of();
    }

    /** 从搜索结果文本中提取标题列表（AI 总结失败时的降级展示） */
    public String extractResultTitles(String extractedText) {
        StringBuilder sb = new StringBuilder();
        String[] lines = extractedText.split("\n");
        for (String line : lines) {
            if (line.startsWith("【") && line.contains("】")) {
                sb.append("• ").append(line.substring(line.indexOf("】") + 1)).append("\n");
            }
        }
        return sb.length() > 0 ? sb.toString().trim() : "请点击下方链接查看详情";
    }

    /** 执行单次搜索 */
    private String doSearch(String query, boolean scutOnly) {
        try {
            StringBuilder json = new StringBuilder("{");
            json.append("\"api_key\":\"").append(apiKey).append("\",");
            json.append("\"query\":\"").append(escapeJson(query)).append("\",");
            json.append("\"search_depth\":\"advanced\",");
            json.append("\"max_results\":").append(scutOnly ? "8" : "6").append(",");
            json.append("\"include_answer\":true,");
            json.append("\"include_raw_content\":true,");
            if (scutOnly) {
                json.append("\"include_domains\":[\"scut.edu.cn\"],");
            }
            json.append("\"language\":\"zh\"");
            json.append("}");

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(TAVILY_URL))
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofSeconds(10))
                    .POST(HttpRequest.BodyPublishers.ofString(json.toString()))
                    .build();

            HttpResponse<String> response = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(10))
                    .build()
                    .send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                log.warn("Tavily 搜索失败，状态码={}", response.statusCode());
                return null;
            }

            this.lastRawJson = response.body();
            return extractResult(this.lastRawJson);

        } catch (Exception e) {
            log.warn("Tavily 搜索异常：{}", e.getMessage());
            return null;
        }
    }

    /** 搜索词预处理 */
    private String cleanQuery(String q) {
        return q.replace("华工", "华南理工大学")
                .replace("25年", "2025年")
                .replace("26年", "2026年")
                .replace("24年", "2024年")
                .replace("今年", "2026年")
                .replace("去年", "2025年");
    }

    /** 提取纯关键词 */
    private String extractKeywords(String q) {
        // 去掉常见的口语化前缀
        return q.replace("华南理工大学", "")
                .replace("请问", "").replace("我想问", "")
                .replace("怎么样", "").replace("如何", "")
                .trim();
    }

    /** 从 Tavily 响应中提取结构化内容 */
    private String extractResult(String json) {
        StringBuilder result = new StringBuilder();

        // 1. 优先提取 Tavily 自动摘要
        String answer = extractJsonField(json, "answer");
        if (answer != null && answer.length() > 20) {
            result.append(answer).append("\n\n");
        }

        // 2. 逐条提取标题+正文（raw_content 优先，更长更详细）
        int pos = 0;
        for (int i = 1; i <= 5; i++) {
            int titleIdx = json.indexOf("\"title\":\"", pos);
            if (titleIdx < 0) break;
            String title = extractJsonField(json.substring(titleIdx), "title");

            // raw_content 优先（含完整页面正文），其次 content
            int rawIdx = json.indexOf("\"raw_content\":\"", titleIdx);
            int contentIdx = json.indexOf("\"content\":\"", titleIdx);
            boolean useRaw = rawIdx >= 0;
            int fieldStart = useRaw ? rawIdx : contentIdx;
            if (fieldStart < 0) { pos = titleIdx + 10; continue; }
            String content = extractJsonField(json.substring(fieldStart),
                    useRaw ? "raw_content" : "content");

            if (title != null && !title.isBlank()) {
                result.append("【").append(i).append("】").append(title).append("\n");
                if (content != null && !content.isBlank()) {
                    // 放宽截断：800 字，确保数据类页面不会被拦腰截断
                    String snippet = content.length() > 800 ? content.substring(0, 800) + "..." : content;
                    result.append(snippet).append("\n");
                }
            }
            pos = titleIdx + Math.max(title != null ? title.length() : 0, 10) + 10;
        }

        String finalResult = result.toString().trim();
        log.info("搜索结果提取完成：{} 字符", finalResult.length());
        return finalResult.length() > 10 ? finalResult : null;
    }

    private String extractJsonField(String json, String field) {
        int start = json.indexOf("\"" + field + "\":\"");
        if (start < 0) return null;
        start += field.length() + 4;
        int end = json.indexOf("\"", start);
        if (end <= start) return null;
        return json.substring(start, end)
                .replace("\\n", "\n")
                .replace("\\t", " ")
                .replace("\\\"", "\"");
    }

    private String escapeJson(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    /** 从最后一次搜索的响应中提取来源 URL（QaService 调用此方法获取引用链接） */
    public static List<String> extractUrls(String json) {
        List<String> urls = new ArrayList<>();
        int pos = 0;
        while (urls.size() < 5) {
            int urlIdx = json.indexOf("\"url\":\"", pos);
            if (urlIdx < 0) break;
            urlIdx += 7;
            int urlEnd = json.indexOf("\"", urlIdx);
            if (urlEnd <= urlIdx) break;
            String url = json.substring(urlIdx, urlEnd);
            if (!url.isBlank() && !urls.contains(url)) urls.add(url);
            pos = urlEnd + 1;
        }
        return urls;
    }
}
