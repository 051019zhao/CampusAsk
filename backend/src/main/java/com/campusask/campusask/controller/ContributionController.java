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

    /**
     * 实时敏感内容检测（前端打字时防抖调用）
     */
    @PostMapping("/check-sensitive")
    public Result<Map<String, Object>> checkSensitive(@RequestBody Map<String, String> body) {
        String text = body.get("text");
        if (text == null || text.isBlank()) {
            return Result.success(Map.of("sensitive", false, "reason", ""));
        }
        Map<String, Object> result = contributionService.checkSensitive(text);
        return Result.success(result);
    }

    @PostMapping("/submit")
    public Result<String> submit(@RequestBody Map<String, String> body) {
        String question = body.get("question");
        String answer = body.get("answer");

        if (question == null || question.trim().isEmpty()) {
            return Result.error("问题不能为空");
        }
        if (answer == null || answer.trim().isEmpty()) {
            return Result.error("回答不能为空");
        }

        // 使用 DeepSeek 进行智能敏感内容检测
        String combinedText = question.trim() + "\n" + answer.trim();
        Map<String, Object> checkResult = contributionService.checkSensitive(combinedText);
        boolean isSensitive = Boolean.TRUE.equals(checkResult.get("sensitive"));
        if (isSensitive) {
            String reason = String.valueOf(checkResult.getOrDefault("reason", "内容包含敏感信息"));
            return Result.error("内容审核未通过：" + reason);
        }

        Contribution contribution = new Contribution();
        contribution.setQuestion(question.trim());
        contribution.setAnswer(answer.trim());
        contribution.setStatus("pending");

        // 从 JWT Token 中获取当前登录用户 ID
        Long currentUserId = BaseContext.getCurrentId();
        if (currentUserId != null) {
            contribution.setUserId(currentUserId);
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
            // V4: 支持管理员编辑后再入库
            String editedTitle = body.get("title");
            String editedContent = body.get("content");
            Long reviewerId = BaseContext.getCurrentId();
            contributionService.approveContribution(id, category, keywords, reviewerId,
                    editedTitle, editedContent);
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
