package com.campusask.campusask.controller;

import com.campusask.campusask.context.BaseContext;
import com.campusask.campusask.dto.Result;
import com.campusask.campusask.entity.Contribution;
import com.campusask.campusask.service.ContributionService;
import com.campusask.campusask.service.SensitiveWordService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

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

    @GetMapping("/pending")
    public Result<List<Contribution>> pendingList() {
        return Result.success(contributionService.listPendingContributions());
    }

    @GetMapping("/rejected")
    public Result<List<Contribution>> rejectedList() {
        return Result.success(contributionService.listRejectedContributions());
    }

    @GetMapping("/my-contributions")
    public Result<List<Contribution>> getMyContributions() {
        Long userId = BaseContext.getCurrentId();
        return Result.success(contributionService.getUserContributions(userId));
    }

    @PutMapping("/{id}/review")
    public Result<String> review(@PathVariable Long id, @RequestBody Map<String, String> body) {
        String action = body.get("action");
        if (action == null || action.isBlank()) {
            return Result.error("action 参数不能为空");
        }

        if ("approve".equalsIgnoreCase(action)) {
            String category = body.get("category");
            String keywords = body.get("keywords");
            if (category == null || category.isBlank()) {
                return Result.error("分类不能为空");
            }
            Long reviewerId = BaseContext.getCurrentId();
            contributionService.approveContribution(id, category, keywords, reviewerId);
            return Result.success("已通过并已加入知识库");
        }

        if ("reject".equalsIgnoreCase(action)) {
            String reason = body.get("reason");
            Long reviewerId = BaseContext.getCurrentId();
            contributionService.rejectContribution(id, reason, reviewerId);
            return Result.success("已驳回" + (reason != null && !reason.isBlank() ? "，原因：" + reason : ""));
        }

        return Result.error("action 参数必须是 approve 或 reject");
    }
}
