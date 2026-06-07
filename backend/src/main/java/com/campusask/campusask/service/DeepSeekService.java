package com.campusask.campusask.service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class DeepSeekService {

    private static final Pattern QUOTED_VALUE = Pattern.compile("\\\"([^\\\"]+)\\\"");

    @Value("${deepseek.api.key}")
    private String apiKey;

    public String ask(String question) {
        try {
            String body = """
                {
                    "model": "deepseek-chat",
                    "messages": [
                        {"role": "system", "content": "你是一个校园生活助手，专门回答学生关于校园生活的问题，回答要简洁友好。"},
                        {"role": "user", "content": "%s"}
                    ],
                    "max_tokens": 500
                }
                """.formatted(question);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://api.deepseek.com/chat/completions"))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + apiKey)
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();

            HttpResponse<String> response = HttpClient.newHttpClient()
                    .send(request, HttpResponse.BodyHandlers.ofString());

            String result = response.body();
            int start = result.indexOf("\"content\":\"") + 11;
            int end = result.indexOf("\"", start);
            return result.substring(start, end);

        } catch (Exception e) {
            return "抱歉，智能回答暂时不可用，请稍后再试。";
        }
    }

    public List<String> normalizeHotKeywords(String question, List<String> existingKeywords) {
        try {
            String prompt = "你是校园问题热词归一化器。\n"
                    + "任务：从用户问题中提炼 1 到 3 个最核心的关键词。\n"
                    + "规则：\n"
                    + "1. 先在已有关键词里寻找同义词、近义词或更标准的写法。\n"
                    + "2. 如果能归一到已有关键词，直接输出已有关键词。\n"
                    + "3. 如果没有合适的已有关键词，再输出一个新的简短关键词。\n"
                    + "4. 只输出 JSON 数组，例如 [\"食堂\",\"图书馆\"]，不要解释。\n"
                    + "已有关键词：" + String.join(",", existingKeywords == null ? List.of() : existingKeywords) + "\n"
                    + "用户问题：" + question;

            String body = "{"
                    + "\"model\":\"deepseek-chat\"," 
                    + "\"messages\":["
                    + "{\"role\":\"system\",\"content\":\"你只输出 JSON 数组，不要输出其他内容。\"},"
                    + "{\"role\":\"user\",\"content\":\"" + escapeJson(prompt) + "\"}"
                    + "],"
                    + "\"temperature\":0.1"
                    + "}";

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://api.deepseek.com/chat/completions"))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + apiKey)
                    .timeout(Duration.ofSeconds(8))
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();

            HttpResponse<String> response = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(8))
                    .build()
                    .send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() >= 400) {
                return List.of();
            }

            String content = extractContent(response.body());
            if (content == null || content.isBlank()) {
                return List.of();
            }
            return parseKeywordArray(content);
        } catch (Exception e) {
            return List.of();
        }
    }

    private String extractContent(String result) {
        int start = result.indexOf("\"content\":\"");
        if (start < 0) return null;
        start += 11;
        int end = result.indexOf("\"", start);
        if (end < 0) return null;
        return result.substring(start, end).replace("\\n", "\n");
    }

    private List<String> parseKeywordArray(String text) {
        List<String> keywords = new ArrayList<>();
        Matcher matcher = QUOTED_VALUE.matcher(text);
        while (matcher.find()) {
            String keyword = matcher.group(1).trim();
            if (!keyword.isEmpty()) {
                keywords.add(keyword);
            }
        }
        return keywords;
    }

    private String escapeJson(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n");
    }
}
