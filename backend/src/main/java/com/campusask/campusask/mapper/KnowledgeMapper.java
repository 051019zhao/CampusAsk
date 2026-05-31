package com.campusask.campusask.mapper;

import com.campusask.campusask.entity.Knowledge;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import java.util.List;

@Mapper
public interface KnowledgeMapper {

    // 关键词搜索（组员已添加）
    List<Knowledge> searchByKeyword(@Param("keyword") String keyword);

    // 新增知识点（Excel导入用）
    @Insert("INSERT INTO knowledge(title, content, category, keywords, view_count, created_by, created_at) " +
            "VALUES(#{title}, #{content}, #{category}, #{keywords}, #{viewCount}, #{createdBy}, NOW())")
    int insert(Knowledge knowledge);

    // 查询全部知识点（前端列表展示用）
    @Select("SELECT * FROM knowledge ORDER BY created_at DESC")
    List<Knowledge> listAll();
}