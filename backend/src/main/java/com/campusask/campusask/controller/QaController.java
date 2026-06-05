package com.campusask.campusask.controller;

import com.campusask.campusask.dto.Result;
import com.campusask.campusask.service.QaService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/qa")
public class QaController {

    @Autowired
    private QaService qaService;

    @PostMapping("/ask")
    public Result<Map<String, Object>> ask(@RequestBody Map<String, String> body) {
        String keyword = body.get("keyword");
        Map<String, Object> data = qaService.ask(keyword);
        return Result.success(data);
    }

    @GetMapping("/hot")
    public Result<java.util.List<String>> hotKeywords() {
        return Result.success(qaService.getHotKeywords(10));
    }

    @GetMapping("/history")
    public Result<java.util.List<String>> recentHistory(
            @RequestParam(required = false) Long userId) {
        return Result.success(qaService.getRecentQuestions(userId, 6));
    }
}
