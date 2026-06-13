package com.campusask.campusask.controller;

import com.campusask.campusask.dto.Result;
import com.campusask.campusask.entity.Feedback;
import com.campusask.campusask.mapper.FeedbackMapper;
import com.campusask.campusask.service.QaService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/qa")
public class QaController {

    @Autowired
    private QaService qaService;

    @Autowired
    private FeedbackMapper feedbackMapper;

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
            @RequestParam(required = false) String userId) {
        return Result.success(qaService.getRecentQuestions(userId, 6));
    }

    /** 用户反馈：有帮助/没帮助 */
    @PostMapping("/feedback")
    public Result<String> feedback(@RequestBody Map<String, Object> body) {
        Feedback fb = new Feedback();
        fb.setQuestion((String) body.getOrDefault("question", ""));
        fb.setAnswer((String) body.getOrDefault("answer", ""));
        fb.setSource((String) body.getOrDefault("source", ""));
        fb.setHelpful((Boolean) body.getOrDefault("helpful", true));
        feedbackMapper.insert(fb);
        return Result.success("感谢反馈！");
    }
}
