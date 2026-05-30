package com.campusask.campusask.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.campusask.campusask.dto.Result;
import com.campusask.campusask.service.QuestionService;

@RestController
@RequestMapping("/api/qa")
public class QuestionController {

    @Autowired
    private QuestionService questionService;

    @PostMapping("/ask")
    public Result ask(@RequestBody java.util.Map<String, String> body) {
        String keyword = body.get("keyword");
        Long userId = null;
        String userIdValue = body.get("userId");
        if (userIdValue != null && !userIdValue.isBlank()) {
            try {
                userId = Long.parseLong(userIdValue);
            } catch (NumberFormatException ignored) {
            }
        }
        Object result = questionService.search(keyword, userId);
        return Result.success(result);
    }

    @GetMapping("/hot")
    public Result hotKeywords() {
        return Result.success(questionService.getHotKeywords(10));
    }

    @GetMapping("/history")
    public Result recentHistory(@RequestParam(required = false) Long userId) {
        return Result.success(questionService.getRecentQuestions(userId, 6));
    }
}