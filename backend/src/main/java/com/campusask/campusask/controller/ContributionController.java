package com.campusask.campusask.controller;

import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.campusask.campusask.dto.Result;
import com.campusask.campusask.entity.Contribution;
import com.campusask.campusask.service.ContributionService;
import com.campusask.campusask.service.SensitiveWordService;

@RestController
@RequestMapping("/api/contribute")
public class ContributionController {

    @Autowired
    private ContributionService contributionService;

    @Autowired
    private SensitiveWordService sensitiveWordService;

    @PostMapping("/submit")
    public Result<String> submit(@RequestBody Map<String, String> body) {
        String question = body.get("question");
        String answer = body.get("answer");
        String userIdValue = body.get("userId");

        if (question == null || question.trim().isEmpty()) {
            return Result.error("问题不能为空");
        }
        if (answer == null || answer.trim().isEmpty()) {
            return Result.error("回答不能为空");
        }

        String combinedText = question.trim() + "\n" + answer.trim();
        List<String> foundWords = sensitiveWordService.findMatchingWords(combinedText);
        if (!foundWords.isEmpty()) {
            String message = "检测到敏感词：" + String.join("，", foundWords) + "，请修改后再提交。";
            return Result.error(message);
        }

        Contribution contribution = new Contribution();
        contribution.setQuestion(question.trim());
        contribution.setAnswer(answer.trim());
        contribution.setStatus("pending");

        if (userIdValue != null && !userIdValue.isBlank()) {
            try {
                contribution.setUserId(Long.parseLong(userIdValue));
            } catch (NumberFormatException ignored) {
            }
        }

        contributionService.submitContribution(contribution);
        return Result.success("投稿已提交，待审核");
    }

    @GetMapping("/sensitive-words")
    public Result<List<String>> getSensitiveWords() {
        return Result.success(sensitiveWordService.getAllWords());
    }
}
