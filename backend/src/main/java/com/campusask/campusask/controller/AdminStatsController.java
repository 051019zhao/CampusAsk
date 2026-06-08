package com.campusask.campusask.controller;

import com.campusask.campusask.dto.Result;
import com.campusask.campusask.mapper.KnowledgeMapper;
import com.campusask.campusask.mapper.QuestionLogMapper;
import com.campusask.campusask.mapper.UserMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDate;
import java.util.*;

/**
 * 管理员统计面板接口
 */
@RestController
@RequestMapping("/api/admin/stats")
public class AdminStatsController {

    @Autowired
    private QuestionLogMapper questionLogMapper;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private KnowledgeMapper knowledgeMapper;

    @Value("${deepseek.api.key}")
    private String apiKey;

    /**
     * 统计面板概览（三张卡片 + 今日查询量）
     * GET /api/admin/stats/overview
     */
    @GetMapping("/overview")
    public Result<Map<String, Object>> overview() {
        String today = LocalDate.now().toString();
        Map<String, Object> data = new HashMap<>();
        data.put("todayCount", questionLogMapper.countByDate(today));
        data.put("userCount", userMapper.countAll());
        data.put("knowledgeCount", knowledgeMapper.countAll());
        data.put("date", today);
        return Result.success(data);
    }

    /**
     * 今日查询量
     * GET /api/admin/stats/daily
     */
    @GetMapping("/daily")
    public Result<Map<String, Object>> daily() {
        String today = LocalDate.now().toString();
        int count = questionLogMapper.countByDate(today);
        Map<String, Object> data = new HashMap<>();
        data.put("date", today);
        data.put("count", count);
        return Result.success(data);
    }

    /**
     * 近7日趋势
     * GET /api/admin/stats/trend
     */
    @GetMapping("/trend")
    public Result<List<Map<String, Object>>> trend() {
        String endDate = LocalDate.now().toString();
        String startDate = LocalDate.now().minusDays(6).toString();
        List<Map<String, Object>> raw = questionLogMapper.countByDateRange(startDate, endDate);

        // 补全缺失日期为 0
        Map<String, Integer> map = new LinkedHashMap<>();
        LocalDate cursor = LocalDate.now().minusDays(6);
        while (!cursor.isAfter(LocalDate.now())) {
            map.put(cursor.toString(), 0);
            cursor = cursor.plusDays(1);
        }
        for (Map<String, Object> row : raw) {
            String d = row.get("date").toString();
            if (map.containsKey(d)) {
                map.put(d, ((Number) row.get("count")).intValue());
            }
        }
        List<Map<String, Object>> result = new ArrayList<>();
        map.forEach((k, v) -> {
            Map<String, Object> item = new HashMap<>();
            item.put("date", k);
            item.put("count", v);
            result.add(item);
        });
        return Result.success(result);
    }

    /**
     * 来源占比
     * GET /api/admin/stats/source
     */
    @GetMapping("/source")
    public Result<List<Map<String, Object>>> source() {
        return Result.success(questionLogMapper.countBySource());
    }

    /**
     * 知识库分类统计
     * GET /api/admin/stats/knowledge-categories
     */
    @GetMapping("/knowledge-categories")
    public Result<List<Map<String, Object>>> knowledgeCategories() {
        return Result.success(knowledgeMapper.countByCategory());
    }

    /**
     * 今日提问话题分布（基于关键词匹配归类）
     * GET /api/admin/stats/topics
     */
    @GetMapping("/topics")
    public Result<List<Map<String, Object>>> topics() {
        String today = LocalDate.now().toString();
        List<String> questions = questionLogMapper.findQuestionsByDate(today);
        Map<String, Integer> topicCount = new LinkedHashMap<>();
        topicCount.put("餐饮", 0);
        topicCount.put("宿舍", 0);
        topicCount.put("学习", 0);
        topicCount.put("交通", 0);
        topicCount.put("校园服务", 0);
        topicCount.put("其他", 0);

        for (String q : questions) {
            if (q == null) { topicCount.merge("其他", 1, Integer::sum); continue; }
            String ql = q.toLowerCase();
            if (ql.contains("食堂") || ql.contains("饭") || ql.contains("吃") || ql.contains("餐饮") || ql.contains("餐厅")) topicCount.merge("餐饮", 1, Integer::sum);
            else if (ql.contains("宿舍") || ql.contains("寝室") || ql.contains("住宿") || ql.contains("洗澡") || ql.contains("热水") || ql.contains("门禁")) topicCount.merge("宿舍", 1, Integer::sum);
            else if (ql.contains("图书馆") || ql.contains("课程") || ql.contains("选课") || ql.contains("考试") || ql.contains("成绩") || ql.contains("自习") || ql.contains("学习")) topicCount.merge("学习", 1, Integer::sum);
            else if (ql.contains("校车") || ql.contains("交通") || ql.contains("出行")) topicCount.merge("交通", 1, Integer::sum);
            else if (ql.contains("学费") || ql.contains("补贴") || ql.contains("打印") || ql.contains("快递") || ql.contains("一卡通") || ql.contains("社团") || ql.contains("医务室") || ql.contains("校园")) topicCount.merge("校园服务", 1, Integer::sum);
            else topicCount.merge("其他", 1, Integer::sum);
        }
        List<Map<String, Object>> result = new ArrayList<>();
        topicCount.forEach((k, v) -> {
            Map<String, Object> item = new HashMap<>();
            item.put("name", k);
            item.put("value", v);
            result.add(item);
        });
        return Result.success(result);
    }

    /**
     * AI 每日统计报告
     * GET /api/admin/stats/report
     */
    @GetMapping("/report")
    public Result<String> report() {
        String today = LocalDate.now().toString();
        int todayCount = questionLogMapper.countByDate(today);
        List<Map<String, Object>> sourceData = questionLogMapper.countBySource();
        List<String> topQs = questionLogMapper.findQuestionsByDateLimit(today, 3);

        StringBuilder prompt = new StringBuilder();
        prompt.append("你是校园数据助手，根据以下今日统计，用1-2句话简洁总结今日问答情况：\n");
        prompt.append("今日查询量：").append(todayCount).append("次\n");
        prompt.append("来源分布：");
        for (Map<String, Object> s : sourceData) prompt.append(s.get("name")).append("(").append(s.get("value")).append("次) ");
        prompt.append("\n热门问题：");
        for (String q : topQs) prompt.append(q).append("；");
        prompt.append("\n请用50字内总结。");

        try {
            String body = "{\"model\":\"deepseek-chat\",\"messages\":["
                    + "{\"role\":\"user\",\"content\":\"" + esc(prompt.toString()) + "\"}],\"max_tokens\":100}";
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create("https://api.deepseek.com/chat/completions"))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + apiKey)
                    .timeout(Duration.ofSeconds(10))
                    .POST(HttpRequest.BodyPublishers.ofString(body)).build();
            HttpResponse<String> resp = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build()
                    .send(req, HttpResponse.BodyHandlers.ofString());
            String text = resp.body();
            int s = text.indexOf("\"content\":\"") + 11;
            int e = text.indexOf("\"", s);
            return Result.success(s > 10 && e > 0 ? text.substring(s, e).replace("\\n", "\n") : "今日暂无报告");
        } catch (Exception ex) {
            return Result.success("AI 报告生成失败：" + ex.getMessage());
        }
    }

    private String esc(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
