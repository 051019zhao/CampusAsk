package com.campusask.campusask.controller;
import com.alibaba.excel.EasyExcel;
import java.io.IOException;
import com.campusask.campusask.dto.KnowledgeExcelDTO;
import com.campusask.campusask.listener.KnowledgeExcelListener;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import com.campusask.campusask.entity.Knowledge;
import com.campusask.campusask.mapper.KnowledgeMapper;
import org.springframework.stereotype.Controller;

import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;


import jakarta.annotation.Resource;
import java.util.List;

// 这里用 @Controller，同时支持页面跳转和接口返回
@RestController
@RequestMapping("/api/knowledge")
@CrossOrigin(origins = "http://localhost:8080") // 允许前端地址跨域访问
@Controller

public class AdminKnowledgeController {

    @Resource
    private KnowledgeMapper knowledgeMapper;

    // ========== 1. 页面跳转接口 ==========
    // 访问 http://localhost:8081/admin 时，跳转到 admin.html 页面
   

    // ========== 2. 数据接口（给前端页面调用） ==========
    /**
     * 获取所有知识点列表
     * @return JSON 格式的知识点列表
     */
    @GetMapping("/listAll")
    @ResponseBody // 加上这个注解，表示返回 JSON 数据，而不是页面
    public List<Knowledge> listAll() {
        return knowledgeMapper.listAll();
    }

    /**
     * 根据关键词搜索知识点
     * @param keyword 搜索关键词
     * @return JSON 格式的搜索结果
     */
    @GetMapping("/search")
    @ResponseBody
    public List<Knowledge> search(@RequestParam String keyword) {
        return knowledgeMapper.searchByKeyword(keyword);
    }
        /**
     * Excel批量导入知识点
     */
   @PostMapping("/importExcel")
@ResponseBody
public String importExcel(@RequestParam("file") MultipartFile file){
    try {
        EasyExcel.read(file.getInputStream(), KnowledgeExcelDTO.class, new KnowledgeExcelListener(knowledgeMapper))
                .sheet()
                .doRead();
        return "导入成功";
    }catch (IOException e){
        e.printStackTrace();
        return "导入失败："+e.getMessage();
    }
}
}