package com.campusask.campusask.controller;

import com.campusask.campusask.dto.Result;
import com.campusask.campusask.mapper.FeedbackMapper;
import com.campusask.campusask.mapper.KnowledgeGapMapper;
import com.campusask.campusask.mapper.KnowledgeMapper;
import com.campusask.campusask.mapper.QuestionLogMapper;
import com.campusask.campusask.mapper.UserMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
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

    @Autowired
    private KnowledgeGapMapper knowledgeGapMapper;

    @Autowired
    private FeedbackMapper feedbackMapper;

    /**
     * V4: 统计面板概览（6 张卡片）
     */
    @GetMapping("/overview")
    public Result<Map<String, Object>> overview() {
        String today = LocalDate.now().toString();
        Map<String, Object> data = new HashMap<>();
        data.put("todayCount", questionLogMapper.countByDate(today));
        data.put("userCount", userMapper.countAll());
        data.put("knowledgeCount", knowledgeMapper.countAll());
        data.put("gapPending", knowledgeGapMapper.countPending());        // 待审核缺口
        data.put("totalQueries", questionLogMapper.countAll());           // 累计查询
        data.put("feedbackRatio", feedbackMapper.countHelpful() > 0      // 好评率
                ? Math.round(feedbackMapper.countHelpful() * 100.0 / Math.max(feedbackMapper.countAll(), 1)) : 0);
        data.put("date", today);
        return Result.success(data);
    }

    /** V4: 近期活跃动态（最新 10 条问答） */
    @GetMapping("/recent-activity")
    public Result<List<Map<String, Object>>> recentActivity() {
        List<Map<String, Object>> raw = questionLogMapper.findRecentQuestionsWithSource(10);
        List<Map<String, Object>> result = new ArrayList<>();
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("HH:mm:ss");
        for (Map<String, Object> row : raw) {
            Map<String, Object> item = new HashMap<>();
            item.put("question", row.get("question"));
            item.put("source", row.get("source"));
            Object timeObj = row.get("created_at");
            if (timeObj != null) {
                try {
                    if (timeObj instanceof LocalDateTime) {
                        item.put("time", ((LocalDateTime) timeObj).format(fmt));
                    } else {
                        item.put("time", timeObj.toString());
                    }
                } catch (Exception e) {
                    item.put("time", timeObj.toString());
                }
            }
            result.add(item);
        }
        return Result.success(result);
    }

    /** V4: 全时段高频提问 Top 10 */
    @GetMapping("/top-questions")
    public Result<List<Map<String, Object>>> topQuestions() {
        return Result.success(questionLogMapper.findTopQuestions(10));
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
     * AI 每日统计报告（基于数据自动生成，不消耗 API token）
     * GET /api/admin/stats/report
     */
    @GetMapping("/report")
    public Result<String> report() {
        String today = LocalDate.now().toString();
        int todayCount = questionLogMapper.countByDate(today);
        List<Map<String, Object>> sourceData = questionLogMapper.countBySource();
        List<String> topQs = questionLogMapper.findQuestionsByDateLimit(today, 5);
        int userCount = userMapper.countAll();
        int knowledgeCount = knowledgeMapper.countAll();

        StringBuilder report = new StringBuilder();
        report.append("📅 截至 ").append(today).append("，系统累计注册用户 ").append(userCount)
             .append(" 人，知识库条目 ").append(knowledgeCount).append(" 条。");
        report.append("今日共处理查询 ").append(todayCount).append(" 次，");

        long aiCount = 0, localCount = 0;
        for (Map<String, Object> s : sourceData) {
            String name = s.get("name").toString();
            long val = ((Number)s.get("value")).longValue();
            if (name.contains("ai") || name.contains("AI")) aiCount += val;
            else if (!"rejected".equals(name)) localCount += val;
        }
        if (aiCount > 0 && localCount > 0)
            report.append("其中本地检索 ").append(localCount).append(" 次，AI 回答 ").append(aiCount).append(" 次。");
        else if (localCount > 0)
            report.append("主要通过本地知识库检索完成。");
        else
            report.append("今日暂无问答记录。");

        if (!topQs.isEmpty()) {
            report.append("高频问题：");
            for (int i = 0; i < Math.min(3, topQs.size()); i++)
                report.append("「").append(topQs.get(i)).append("」");
            report.append("。");
        }
        return Result.success(report.toString());
    }
}
