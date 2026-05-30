package com.campusask.campusask.service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class DeepSeekService {

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
}
