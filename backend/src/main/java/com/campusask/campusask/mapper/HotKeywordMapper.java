package com.campusask.campusask.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.campusask.campusask.entity.HotKeyword;

@Mapper
public interface HotKeywordMapper {
    void upsertKeyword(@Param("keyword") String keyword);
    List<HotKeyword> findTopKeywords(@Param("limit") int limit);
    List<String> findTopKeywordNames(@Param("limit") int limit);
}
