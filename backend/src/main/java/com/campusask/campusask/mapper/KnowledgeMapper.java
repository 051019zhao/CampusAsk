package com.campusask.campusask.mapper;

import com.campusask.campusask.entity.Knowledge;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;

@Mapper
public interface KnowledgeMapper {
    List<Knowledge> searchByKeyword(@Param("keyword") String keyword);
}