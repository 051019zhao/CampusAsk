package com.campusask.campusask.controller;

import com.campusask.campusask.dto.Result;
import com.campusask.campusask.service.HotspotService;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/hotspot")
public class HotspotController {

    @Autowired
    private HotspotService hotspotService;

    @GetMapping("/top10")
    public Result<List<String>> top10() {
        return Result.success(hotspotService.getTopKeywords(10));
    }
}
