package com.campusask.campusask.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.campusask.campusask.entity.QuestionLog;

@Mapper
public interface QuestionLogMapper {
    void insertLog(QuestionLog log);
    List<QuestionLog> findRecentByUserId(@Param("userId") String userId, @Param("limit") int limit);
    List<String> findTopKeywords(@Param("limit") int limit);
    List<String> findRecentQuestions(@Param("limit") int limit);
    List<QuestionLog> findUnprocessed(@Param("limit") int limit);
    void markProcessed(@Param("id") Long id);

    // ===== 统计面板接口 =====
    int countByDate(@Param("date") String date);
    int countAll();  // V4: 累计查询总数
    List<java.util.Map<String, Object>> countByDateRange(@Param("startDate") String startDate, @Param("endDate") String endDate);
    List<java.util.Map<String, Object>> countBySource();
    List<String> findQuestionsByDate(@Param("date") String date);
    List<String> findQuestionsByDateLimit(@Param("date") String date, @Param("limit") int limit);
    List<java.util.Map<String, Object>> findRecentQuestionsWithSource(@Param("limit") int limit);  // V4
    List<java.util.Map<String, Object>> findTopQuestions(@Param("limit") int limit);  // V4
}
