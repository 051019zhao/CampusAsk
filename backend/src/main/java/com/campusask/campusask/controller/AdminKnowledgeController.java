package com.campusask.campusask.controller;

import com.campusask.campusask.context.BaseContext;
import com.campusask.campusask.dto.ImportResult;
import com.campusask.campusask.dto.Result;
import com.campusask.campusask.entity.Knowledge;
import com.campusask.campusask.mapper.KnowledgeMapper;
import com.campusask.campusask.service.KnowledgeService;
import com.campusask.campusask.service.QaService;
import com.campusask.campusask.service.SensitiveWordService;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.jdbc.core.JdbcTemplate;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 知识库管理接口（管理员专用）
 */
@RestController
@RequestMapping("/api/knowledge")
@CrossOrigin(origins = "http://localhost:8080")
@Validated  // 启用请求参数校验
public class AdminKnowledgeController {

    @Autowired
    private KnowledgeService knowledgeService;

    @Autowired
    private KnowledgeMapper knowledgeMapper;

    @Autowired
    private QaService qaService;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private SensitiveWordService sensitiveWordService;

    /**
     * 获取所有知识点列表
     */
    @GetMapping("/listAll")
    public Result<List<Knowledge>> listAll() {
        List<Knowledge> list = knowledgeService.listAll();
        return Result.success(list);
    }

    /**
     * 分页查询知识点（支持关键词和分类筛选）
     * GET /api/knowledge/list?keyword=&category=&page=&size=
     */
    @GetMapping("/list")
    public Result<Map<String, Object>> list(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String category,
            @RequestParam(defaultValue = "1") @Min(value = 1, message = "页码最小为1") int page,
            @RequestParam(defaultValue = "10") @Min(value = 1, message = "每页数量最小为1") @Max(value = 100, message = "每页数量最大为100") int size) {
        
        int offset = (page - 1) * size;
        List<Knowledge> list = knowledgeService.listByCondition(keyword, category, offset, size);
        int total = knowledgeService.countByCondition(keyword, category);
        
        Map<String, Object> result = new HashMap<>();
        result.put("list", list);
        result.put("total", total);
        result.put("page", page);
        result.put("size", size);
        
        return Result.success(result);
    }

    /**
     * 获取所有分类列表
     */
    @GetMapping("/categories")
    public Result<List<String>> getCategories() {
        List<String> categories = knowledgeService.getAllCategories();
        return Result.success(categories);
    }

    /**
     * 新增知识点（单条添加）
     */
    @PostMapping("/")
    public Result<String> add(@Valid @RequestBody Knowledge knowledge) {
        // V4: 敏感词检测
        String checkResult = checkKnowledgeSensitive(knowledge);
        if (checkResult != null) return Result.error(checkResult);
        knowledgeService.add(knowledge);
        qaService.invalidateRelatedCache(knowledge.getTitle());
        return Result.success("添加成功");
    }

    /** V4: 敏感词检测辅助方法 */
    private String checkKnowledgeSensitive(Knowledge k) {
        List<String> titleViolations = sensitiveWordService.findMatchingWords(k.getTitle());
        List<String> contentViolations = sensitiveWordService.findMatchingWords(k.getContent());
        if (!titleViolations.isEmpty()) return "标题包含违规内容：" + String.join("、", titleViolations);
        if (!contentViolations.isEmpty()) return "内容包含违规内容：" + String.join("、", contentViolations);
        if (k.getKeywords() != null) {
            List<String> kwViolations = sensitiveWordService.findMatchingWords(k.getKeywords());
            if (!kwViolations.isEmpty()) return "关键词包含违规内容：" + String.join("、", kwViolations);
        }
        return null;
    }

    /**
     * 根据关键词搜索知识点
     */
    @GetMapping("/search")
    public Result<List<Knowledge>> search(@RequestParam @NotBlank(message = "关键词不能为空") String keyword) {
        List<Knowledge> list = knowledgeMapper.searchByKeyword(keyword);
        return Result.success(list);
    }

    /**
     * 根据ID获取知识点详情
     */
    @GetMapping("/{id}")
    public Result<Knowledge> getById(@PathVariable @Min(value = 1, message = "ID必须大于0") Long id) {
        Knowledge knowledge = knowledgeService.getById(id);
        if (knowledge == null) {
            return Result.error("知识点不存在");
        }
        return Result.success(knowledge);
    }

    /**
     * Excel批量导入知识点
     */
    @PostMapping("/importExcel")
    public Result<ImportResult> importExcel(@RequestParam("file") MultipartFile file) {
        // 文件校验
        if (file == null || file.isEmpty()) {
            return Result.error("文件不能为空");
        }
        ImportResult result = knowledgeService.importFromExcel(file);
        qaService.invalidateRelatedCache(null); // V3: 批量导入后清除全部缓存
        String message = "导入完成，成功：" + result.getSuccessCount() + "条，失败：" + result.getFailedCount() + "条";
        Result<ImportResult> response = Result.success(result);
        response.setMessage(message);
        return response;
    }

    /**
     * 编辑知识点（更新）
     */
    @PutMapping("/{id}")
    public Result<String> update(@PathVariable @Min(value = 1, message = "ID必须大于0") Long id, @Valid @RequestBody Knowledge knowledge) {
        // V4: 敏感词检测
        String checkResult = checkKnowledgeSensitive(knowledge);
        if (checkResult != null) return Result.error(checkResult);
        Knowledge existing = knowledgeService.getById(id);
        if (existing == null) {
            return Result.error("知识点不存在");
        }
        // 设置ID
        knowledge.setId(id);
        // 保持原有创建者和创建时间
        knowledge.setCreatedBy(existing.getCreatedBy());
        knowledge.setCreatedAt(existing.getCreatedAt());
        knowledge.setViewCount(existing.getViewCount());
        
        knowledgeService.update(knowledge);
        qaService.invalidateRelatedCache(knowledge.getTitle());
        // V4: 记录版本历史
        jdbc.update("INSERT INTO knowledge_history(knowledge_id,title,content,category,keywords,changed_by,changed_at) VALUES(?,?,?,?,?,?,NOW())",
                id, knowledge.getTitle(), knowledge.getContent(), knowledge.getCategory(), knowledge.getKeywords(), BaseContext.getCurrentId());
        return Result.success("更新成功");
    }

    /** V4: 导出全部知识库为 CSV */
    @GetMapping("/export")
    public void exportCsv(HttpServletResponse response) throws Exception {
        List<Knowledge> list = knowledgeService.listAll();
        response.setContentType("text/csv;charset=UTF-8");
        response.setHeader("Content-Disposition", "attachment;filename=knowledge_export.csv");
        java.io.PrintWriter w = response.getWriter();
        w.write("﻿"); // BOM for Excel
        w.println("标题,内容,分类,关键词,浏览量");
        for (Knowledge k : list) {
            w.printf("\"%s\",\"%s\",\"%s\",\"%s\",%d\n",
                    escCsv(k.getTitle()), escCsv(k.getContent()),
                    escCsv(k.getCategory()), escCsv(k.getKeywords()), k.getViewCount());
        }
        w.flush();
    }

    private String escCsv(String s) {
        return s == null ? "" : s.replace("\"", "\"\"");
    }

    /**
     * 删除知识点
     */
    @DeleteMapping("/{id}")
    public Result<String> delete(@PathVariable @Min(value = 1, message = "ID必须大于0") Long id) {
        // 检查知识点是否存在
        Knowledge existing = knowledgeService.getById(id);
        if (existing == null) {
            return Result.error("知识点不存在");
        }
        knowledgeService.deleteById(id);
        qaService.invalidateRelatedCache(null); // V3: 清除全部缓存
        return Result.success("删除成功");
    }

    /** V4: 知识点版本历史 */
    @GetMapping("/{id}/history")
    public Result<java.util.List<Map<String,Object>>> history(@PathVariable Long id) {
        return Result.success(jdbc.queryForList("SELECT * FROM knowledge_history WHERE knowledge_id=? ORDER BY changed_at DESC LIMIT 20", id));
    }
}