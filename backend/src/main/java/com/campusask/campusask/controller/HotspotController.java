package com.campusask.campusask.controller;

import com.campusask.campusask.dto.Result;
import com.campusask.campusask.service.HotspotService;
import com.campusask.campusask.service.QaService;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/hotspot")
public class HotspotController {

    private static final Logger log = LoggerFactory.getLogger(HotspotController.class);

    @Autowired
    private HotspotService hotspotService;

    @Autowired
    private QaService qaService;

    @GetMapping("/top10")
    public Result<List<Map<String, Object>>> top10(
            @org.springframework.web.bind.annotation.RequestParam(defaultValue = "7d") String range) {
        int days = 7;
        if ("today".equals(range)) days = 1;
        else if ("30d".equals(range)) days = 30;

        java.time.LocalDate now = java.time.LocalDate.now();
        String end = now.toString();
        String start = now.minusDays(Math.max(days - 1, 0)).toString();

        List<Map<String, Object>> rows = hotspotService.getTopKeywordsByRange(start, end, 10);
        return Result.success(rows);
    }

    @GetMapping("/questions")
    public Result<List<Map<String, Object>>> questionRanking(
            @org.springframework.web.bind.annotation.RequestParam(defaultValue = "7d") String range) {
        int days = 7;
        if ("today".equals(range)) days = 1;
        else if ("30d".equals(range)) days = 30;

        java.time.LocalDate now = java.time.LocalDate.now();
        String end = now.toString();
        String start = now.minusDays(Math.max(days - 1, 0)).toString();

        List<Map<String, Object>> result = hotspotService.getTopQuestionsByRange(start, end, 10);

        // V4: 异步预热热点问题的答案缓存，用户点击秒回
        new Thread(() -> {
            int warmed = 0;
            for (Map<String, Object> item : result) {
                try {
                    String name = (String) item.get("name");
                    if (name != null && !name.isBlank()) {
                        qaService.ask(name, null);
                        warmed++;
                    }
                } catch (Exception ignored) { }
            }
            log.info("排行榜缓存预热：{} 个热点问题已加载", warmed);
        }, "hotspot-cache-warm").start();

        return Result.success(result);
    }
}
