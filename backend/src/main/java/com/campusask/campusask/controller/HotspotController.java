package com.campusask.campusask.controller;

import com.campusask.campusask.dto.Result;
import com.campusask.campusask.service.HotspotService;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/hotspot")
public class HotspotController {

    @Autowired
    private HotspotService hotspotService;

    /**
     * 获取热词排行
     * @param range 时间范围：today（今日）、7d（近7日，默认）、30d（近30日）
     */
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
}
