package com.campusask.campusask.controller;

import com.campusask.campusask.dto.Result;
import com.campusask.campusask.service.QuestionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/qa")
public class QuestionController {

    @Autowired
    private QuestionService questionService;

    @PostMapping("/ask")
    public Result ask(@RequestBody java.util.Map<String, String> body) {
        String keyword = body.get("keyword");
        Object result = questionService.search(keyword);
        return Result.success(result);
    }
}