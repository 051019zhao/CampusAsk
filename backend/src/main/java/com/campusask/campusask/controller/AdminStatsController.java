package com.campusask.campusask.controller;

import com.campusask.campusask.dto.Result;
import com.campusask.campusask.mapper.KnowledgeMapper;
import com.campusask.campusask.mapper.QuestionLogMapper;
import com.campusask.campusask.mapper.UserMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

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
}
