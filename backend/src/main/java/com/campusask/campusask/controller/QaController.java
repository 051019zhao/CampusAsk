package com.campusask.campusask.controller;

import com.campusask.campusask.dto.Result;
import com.campusask.campusask.entity.Feedback;
import com.campusask.campusask.entity.KnowledgeGap;
import com.campusask.campusask.mapper.FeedbackMapper;
import com.campusask.campusask.mapper.KnowledgeGapMapper;
import com.campusask.campusask.service.QaService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    @Autowired
    private KnowledgeGapMapper knowledgeGapMapper;

    private static final Logger log = LoggerFactory.getLogger(QaController.class);

    @PostMapping("/ask")
    public Result<Map<String, Object>> ask(@RequestBody Map<String, String> body) {
        String keyword = body.get("keyword");
        String sessionId = body.get("sessionId"); // V3 新增：多轮对话会话ID
        Map<String, Object> data = qaService.ask(keyword, sessionId);
        return Result.success(data);
    }

    /** V3 新增：清除会话上下文（用户切换话题时调用） */
    @PostMapping("/clear-session")
    public Result<String> clearSession(@RequestBody Map<String, String> body) {
        String sessionId = body.get("sessionId");
        qaService.clearSession(sessionId);
        return Result.success("会话已清除");
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

    /** 用户反馈：有帮助/没帮助（V3：差评 ≥3 次自动加入知识缺口） */
    @PostMapping("/feedback")
    public Result<String> feedback(@RequestBody Map<String, Object> body) {
        String question = (String) body.getOrDefault("question", "");
        String answer   = (String) body.getOrDefault("answer", "");
        String source   = (String) body.getOrDefault("source", "");
        boolean helpful = (Boolean) body.getOrDefault("helpful", true);

        Feedback fb = new Feedback();
        fb.setQuestion(question);
        fb.setAnswer(answer);
        fb.setSource(source);
        fb.setHelpful(helpful);
        feedbackMapper.insert(fb);

        // V3: 差评累计 ≥3 次 → 自动加入知识缺口，管理员可审核优化
        if (!helpful && question != null && !question.isBlank()) {
            try {
                int negativeCount = feedbackMapper.countNegativeByQuestion(question);
                if (negativeCount >= 3) {
                    KnowledgeGap gap = new KnowledgeGap();
                    gap.setQuestion(question.length() > 500 ? question.substring(0, 500) : question);
                    gap.setAnswer(answer != null ? answer : "");
                    gap.setSource(source != null && !source.isBlank() ? source : "feedback");
                    gap.setSearchCount(negativeCount);
                    knowledgeGapMapper.upsertGap(gap);
                    log.info("差评≥3次，已自动加入知识缺口：{}", question);
                }
            } catch (Exception e) {
                log.warn("差评自动入缺口失败：{}", e.getMessage());
            }
        }

        return Result.success("感谢反馈！");
    }
}
