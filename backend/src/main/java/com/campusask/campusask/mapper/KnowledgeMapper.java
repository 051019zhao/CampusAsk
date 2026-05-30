package com.campusask.campusask.mapper;

import com.campusask.campusask.entity.Knowledge;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import java.util.List;

@Mapper
public interface KnowledgeMapper {

    @Select("SELECT * FROM knowledge WHERE title LIKE CONCAT('%',#{keyword},'%') " +
            "OR keywords LIKE CONCAT('%',#{keyword},'%') ORDER BY view_count DESC LIMIT 5")
    List<Knowledge> searchByKeyword(@Param("keyword") String keyword);

    @Select("SELECT * FROM knowledge ORDER BY created_at DESC")
    List<Knowledge> listAll();

    @Insert("INSERT INTO knowledge(title, content, category, keywords, view_count, created_by, created_at) " +
            "VALUES(#{title}, #{content}, #{category}, #{keywords}, #{viewCount}, #{createdBy}, NOW())")
    int insert(Knowledge knowledge);
}