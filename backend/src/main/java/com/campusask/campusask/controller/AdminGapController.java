package com.campusask.campusask.controller;

import com.campusask.campusask.context.BaseContext;
import com.campusask.campusask.dto.Result;
import com.campusask.campusask.entity.Knowledge;
import com.campusask.campusask.entity.KnowledgeGap;
import com.campusask.campusask.mapper.KnowledgeGapMapper;
import com.campusask.campusask.mapper.KnowledgeMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 赵丹丹 V3 — 知识缺口管理接口（管理员专用）
 * 来自 AI 联网搜索 / 自主回答 / 降级兜底的问答，
 * 管理员审核后决定是否沉淀到知识库。
 */
@RestController
@RequestMapping("/api/admin/gaps")
public class AdminGapController {

    @Autowired
    private KnowledgeGapMapper gapMapper;

    @Autowired
    private KnowledgeMapper knowledgeMapper;

    /** 待审核列表（分页） */
    @GetMapping("/pending")
    public Result<Map<String, Object>> pendingList(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        int offset = (page - 1) * size;
        List<KnowledgeGap> list = gapMapper.selectPending(offset, size);
        int total = gapMapper.countPending();
        Map<String, Object> result = new HashMap<>();
        result.put("list", list);
        result.put("total", total);
        result.put("page", page);
        return Result.success(result);
    }

    /** 已忽略列表（分页） */
    @GetMapping("/ignored")
    public Result<Map<String, Object>> ignoredList(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        int offset = (page - 1) * size;
        List<KnowledgeGap> list = gapMapper.selectIgnored(offset, size);
        int total = gapMapper.countIgnored();
        Map<String, Object> result = new HashMap<>();
        result.put("list", list);
        result.put("total", total);
        result.put("page", page);
        return Result.success(result);
    }

    /** 来源统计 */
    @GetMapping("/source-stats")
    public Result<List<Map<String, Object>>> sourceStats() {
        return Result.success(gapMapper.countBySource());
    }

    /** 审核通过 → 写入 knowledge 表（支持编辑后再入库） */
    @Transactional
    @PutMapping("/{id}/approve")
    public Result<String> approve(@PathVariable Long id, @RequestBody Map<String, String> body) {
        KnowledgeGap gap = gapMapper.selectById(id);
        if (gap == null) return Result.error("记录不存在");

        String category = body.getOrDefault("category", "综合咨询");
        String keywords = body.getOrDefault("keywords", "");
        // V3: 允许管理员编辑后再入库，不传则用原文
        String editedTitle = body.get("title");
        String editedContent = body.get("content");

        String title = (editedTitle != null && !editedTitle.isBlank())
                ? editedTitle.trim() : gap.getQuestion();
        String content = (editedContent != null && !editedContent.isBlank())
                ? editedContent.trim() : gap.getAnswer();

        // 写入知识库
        Knowledge knowledge = new Knowledge();
        knowledge.setTitle(title.length() > 200 ? title.substring(0, 200) : title);
        knowledge.setContent(content);
        knowledge.setCategory(category);
        knowledge.setKeywords(keywords);
        knowledge.setViewCount(0);
        knowledge.setCreatedBy(BaseContext.getCurrentId() != null ? BaseContext.getCurrentId() : 1L);
        knowledgeMapper.insert(knowledge);

        // 更新缺口状态
        gapMapper.updateStatus(id, "approved", category, keywords,
                BaseContext.getCurrentId() != null ? BaseContext.getCurrentId() : 1L);

        return Result.success("已通过审核并加入知识库");
    }

    /** 忽略 */
    @PutMapping("/{id}/ignore")
    public Result<String> ignore(@PathVariable Long id) {
        KnowledgeGap gap = gapMapper.selectById(id);
        if (gap == null) return Result.error("记录不存在");

        gapMapper.updateStatus(id, "ignored", null, null,
                BaseContext.getCurrentId() != null ? BaseContext.getCurrentId() : 1L);
        return Result.success("已忽略");
    }

    /** 重新提交到待审核（从 ignored 恢复） */
    @PutMapping("/{id}/reopen")
    public Result<String> reopen(@PathVariable Long id) {
        KnowledgeGap gap = gapMapper.selectById(id);
        if (gap == null) return Result.error("记录不存在");

        gapMapper.updateStatus(id, "pending", null, null, null);
        return Result.success("已重新提交审核");
    }
}
