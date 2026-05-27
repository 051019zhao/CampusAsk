package com.campusask.campusask.controller;

import com.campusask.campusask.dto.Result;
import com.campusask.campusask.entity.Knowledge;
import com.campusask.campusask.service.QuestionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/qa")
public class QuestionController {

    @Autowired
    private QuestionService questionService;

    @PostMapping("/ask")
    public Result ask(@RequestBody java.util.Map<String, String> body) {
        String keyword = body.get("keyword");
        List<Knowledge> results = questionService.search(keyword);
        if (results.isEmpty()) {
            return Result.success("暂无相关答案，请尝试其他关键词");
        }
        return Result.success(results);
    }
}
