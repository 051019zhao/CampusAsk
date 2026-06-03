package com.campusask.campusask.service;

import com.campusask.campusask.entity.Knowledge;
import org.springframework.web.multipart.MultipartFile;
import java.util.List;

public interface KnowledgeService {
    // 导入Excel数据
    void importFromExcel(MultipartFile file);
    // 查询所有知识点
    List<Knowledge> listAll();
    // 根据ID查询知识点
    Knowledge getById(Long id);
    // 更新知识点
    void update(Knowledge knowledge);
    // 删除知识点
    void deleteById(Long id);

}