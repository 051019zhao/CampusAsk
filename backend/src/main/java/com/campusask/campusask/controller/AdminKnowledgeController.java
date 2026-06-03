package com.campusask.campusask.controller;

import com.campusask.campusask.dto.Result;
import com.campusask.campusask.entity.Knowledge;

import com.campusask.campusask.mapper.KnowledgeMapper;
import com.campusask.campusask.service.KnowledgeService;



import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;


import java.util.List;

/**
 * 知识库管理接口（管理员专用）
 */
@RestController
@RequestMapping("/api/knowledge")

@CrossOrigin(origins = "http://localhost:8080")

public class AdminKnowledgeController {

    @Autowired
    private KnowledgeService knowledgeService;
     @Autowired
    private KnowledgeMapper knowledgeMapper;
    /**
     * 获取所有知识点列表
     
     */
    @GetMapping("/listAll")
   
    public Result<List<Knowledge>> listAll() {
        List<Knowledge> list = knowledgeService.listAll();
        return Result.success(list);
    }

    /**
     * 根据关键词搜索知识点
    
     */
    @GetMapping("/search")
    @ResponseBody
      public Result<List<Knowledge>> search(@RequestParam String keyword) {
        List<Knowledge> list = knowledgeMapper.searchByKeyword(keyword);
        return Result.success(list);
    }
    /**
     * 根据ID获取知识点详情
     */
    @GetMapping("/{id}")
    public Result<Knowledge> getById(@PathVariable Long id) {
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
    public Result<String> importExcel(@RequestParam("file") MultipartFile file) {
        try {
            knowledgeService.importFromExcel(file);
            return Result.success("导入成功");
        } catch (RuntimeException e) {
            return Result.error(e.getMessage());
        }
    }
 /**
     * 编辑知识点（更新）
     */
    @PutMapping("/{id}")
    public Result<String> update(@PathVariable Long id, @RequestBody Knowledge knowledge) {
        try {
            // 检查知识点是否存在
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
            return Result.success("更新成功");
        } catch (Exception e) {
            return Result.error("更新失败：" + e.getMessage());
        }
    }
 
    /**
     * 删除知识点
     */
    @DeleteMapping("/{id}")
    public Result<String> delete(@PathVariable Long id) {
        try {
            // 检查知识点是否存在
            Knowledge existing = knowledgeService.getById(id);
            if (existing == null) {
                return Result.error("知识点不存在");
            }
            knowledgeService.deleteById(id);
            return Result.success("删除成功");
        } catch (Exception e) {
            return Result.error("删除失败：" + e.getMessage());
        }
    }
}
