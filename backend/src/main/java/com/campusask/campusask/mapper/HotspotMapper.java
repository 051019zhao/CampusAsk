package com.campusask.campusask.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface HotspotMapper {
    List<String> findTopKeywords(@Param("limit") int limit);
}
