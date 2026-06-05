package com.campusask.campusask.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.campusask.campusask.entity.QuestionLog;

@Mapper
public interface QuestionLogMapper {
    void insertLog(QuestionLog log);
    List<QuestionLog> findRecentByUserId(@Param("userId") Long userId, @Param("limit") int limit);
    List<String> findTopKeywords(@Param("limit") int limit);
    List<String> findRecentQuestions(@Param("limit") int limit);
}
