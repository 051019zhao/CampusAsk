package com.campusask.campusask.controller;

import com.campusask.campusask.dto.Result;
import com.campusask.campusask.mapper.FeedbackMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.*;

@RestController
@RequestMapping("/api/admin/feedback")
public class FeedbackController {

    @Autowired
    private FeedbackMapper feedbackMapper;

    @Value("${deepseek.api.key}")
    private String apiKey;

    /** V4: 反馈列表（分页 + 可选筛选） */
    @GetMapping("/list")
    public Result<Map<String, Object>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String filter) {  // all / helpful / unhelpful
        int offset = (page - 1) * size;
        Boolean helpfulFilter = null;
        if ("helpful".equals(filter)) helpfulFilter = true;
        else if ("unhelpful".equals(filter)) helpfulFilter = false;

        List<Map<String, Object>> list = feedbackMapper.listWithAnswer(offset, size, helpfulFilter);
        int total = helpfulFilter == null ? feedbackMapper.countAll()
                : (helpfulFilter ? feedbackMapper.countHelpful() : feedbackMapper.countUnhelpful());

        Map<String, Object> result = new HashMap<>();
        result.put("list", list);
        result.put("total", total);
        result.put("page", page);
        return Result.success(result);
    }

    /** 反馈统计 */
    @GetMapping("/stats")
    public Result<Map<String, Object>> stats() {
        Map<String, Object> data = new HashMap<>();
        data.put("helpful", feedbackMapper.countHelpful());
        data.put("unhelpful", feedbackMapper.countUnhelpful());
        data.put("total", feedbackMapper.countAll());
        return Result.success(data);
    }

    /** V4: 近7日反馈趋势 */
    @GetMapping("/trend")
    public Result<List<Map<String, Object>>> trend() {
        return Result.success(feedbackMapper.dailyTrend(7));
    }

    /** 按问题分组的反馈摘要 */
    @GetMapping("/grouped")
    public Result<List<Map<String, Object>>> grouped() {
        return Result.success(feedbackMapper.groupByQuestion());
    }

    /** 按来源统计 */
    @GetMapping("/by-source")
    public Result<List<Map<String, Object>>> bySource() {
        return Result.success(feedbackMapper.groupBySource());
    }

    /** AI 总结反馈意见 */
    @GetMapping("/summary")
    public Result<String> summary() {
        List<Map<String, Object>> grouped = feedbackMapper.groupByQuestion();
        if (grouped.isEmpty()) return Result.success("暂无反馈数据，用户反馈将在此处汇总分析。");

        // 先用数据生成基础总结（不依赖 DeepSeek）
        int totalHelpful = feedbackMapper.countHelpful();
        int totalUnhelpful = feedbackMapper.countUnhelpful();
        StringBuilder fallback = new StringBuilder();
        fallback.append("共收到 ").append(feedbackMapper.countAll()).append(" 条反馈，");
        fallback.append("其中 ").append(totalHelpful).append(" 条好评、").append(totalUnhelpful).append(" 条差评。");
        if (!grouped.isEmpty()) {
            Map<String, Object> top = grouped.get(0);
            if (((Number)top.get("unhelpful")).intValue() > 0) {
                fallback.append("差评最多的问题：「").append(top.get("question")).append("」（")
                      .append(top.get("unhelpful")).append("次）。");
            }
        }

        // 尝试 AI 增强
        StringBuilder prompt = new StringBuilder();
        prompt.append("以下用户对校园问答系统的反馈，用1-2句话概括核心问题：\n");
        for (int i = 0; i < Math.min(5, grouped.size()); i++) {
            Map<String, Object> g = grouped.get(i);
            prompt.append("- ").append(g.get("question"))
                  .append("（赞").append(g.get("helpful"))
                  .append("/踩").append(g.get("unhelpful")).append("）\n");
        }

        try {
            String body = "{\"model\":\"deepseek-chat\",\"messages\":["
                    + "{\"role\":\"user\",\"content\":\"" + esc(prompt.toString()) + "\"}],"
                    + "\"max_tokens\":150,\"temperature\":0.3}";
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create("https://api.deepseek.com/chat/completions"))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + apiKey)
                    .timeout(Duration.ofSeconds(6))
                    .POST(HttpRequest.BodyPublishers.ofString(body)).build();
            HttpResponse<String> resp = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(6)).build()
                    .send(req, HttpResponse.BodyHandlers.ofString());
            String text = resp.body();
            int s = text.indexOf("\"content\":\"") + 11;
            int e = text.indexOf("\"", s);
            String ai = s > 10 && e > 0 ? text.substring(s, e).replace("\\n", "\n") : null;
            return Result.success(ai != null ? ai + "\n\n" + fallback : fallback.toString());
        } catch (Exception ex) {
            return Result.success(fallback.toString()); // AI 失败用基础总结兜底
        }
    }

    private String esc(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
