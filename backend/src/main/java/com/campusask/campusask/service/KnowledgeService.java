package com.campusask.campusask.service;

import com.campusask.campusask.entity.Knowledge;
import org.springframework.web.multipart.MultipartFile;
import java.util.List;

public interface KnowledgeService {
    // 导入Excel数据
    void importFromExcel(MultipartFile file);
    // 查询所有知识点
    List<Knowledge> listAll();
}