package com.campusask.campusask.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface HotspotMapper {
    List<String> findTopKeywords(@Param("limit") int limit);

    // 返回带频次的结果，按时间范围过滤（startDate/endDate 可为空）并排除 source='rejected'、单字词和纯数字
    List<java.util.Map<String, Object>> findTopKeywordsByRange(@Param("startDate") String startDate,
                                                               @Param("endDate") String endDate,
                                                               @Param("limit") int limit);
}
