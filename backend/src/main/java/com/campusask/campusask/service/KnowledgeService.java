package com.campusask.campusask.service;

 
import com.campusask.campusask.dto.ImportResult;
import com.campusask.campusask.entity.Knowledge;
import org.springframework.web.multipart.MultipartFile;
import java.util.List;

public interface KnowledgeService {
    // 导入Excel数据
     ImportResult importFromExcel(MultipartFile file);    // 查询所有知识点
    List<Knowledge> listAll();
    // 根据ID查询知识点
    Knowledge getById(Long id);
    // 更新知识点
    void update(Knowledge knowledge);
    // 删除知识点
    void deleteById(Long id);
    // 分页查询知识点（支持关键词和分类筛选）
    List<Knowledge> listByCondition(String keyword, String category, int offset, int size);
 
    // 统计符合条件的知识点数量
    int countByCondition(String keyword, String category);
 
    // 获取所有分类列表
    List<String> getAllCategories();
 
    // 单条添加知识点（带敏感词检测）
    void add(Knowledge knowledge);

}