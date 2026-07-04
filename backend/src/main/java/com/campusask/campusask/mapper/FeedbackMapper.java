package com.campusask.campusask.mapper;

import com.campusask.campusask.entity.Feedback;
import org.apache.ibatis.annotations.*;
import java.util.List;
import java.util.Map;

@Mapper
public interface FeedbackMapper {

    @Insert("INSERT INTO feedback (question, answer, source, helpful, created_at) " +
            "VALUES (#{question}, #{answer}, #{source}, #{helpful}, NOW())")
    void insert(Feedback feedback);

    @Select("SELECT COUNT(*) FROM feedback WHERE helpful = 1")
    int countHelpful();

    @Select("SELECT COUNT(*) FROM feedback WHERE helpful = 0")
    int countUnhelpful();

    @Select("SELECT COUNT(*) FROM feedback")
    int countAll();

    @Select("SELECT * FROM feedback ORDER BY created_at DESC LIMIT 100")
    List<Map<String, Object>> listAll();

    @Select("SELECT question, " +
            "SUM(CASE WHEN helpful = 1 THEN 1 ELSE 0 END) AS helpful, " +
            "SUM(CASE WHEN helpful = 0 THEN 1 ELSE 0 END) AS unhelpful, " +
            "COUNT(*) AS total " +
            "FROM feedback GROUP BY question ORDER BY total DESC LIMIT 20")
    List<Map<String, Object>> groupByQuestion();

    @Select("SELECT source, " +
            "SUM(CASE WHEN helpful = 1 THEN 1 ELSE 0 END) AS helpful, " +
            "SUM(CASE WHEN helpful = 0 THEN 1 ELSE 0 END) AS unhelpful, " +
            "COUNT(*) AS total " +
            "FROM feedback GROUP BY source ORDER BY total DESC")
    List<Map<String, Object>> groupBySource();

    /** V3: 统计某个问题的差评数 */
    @Select("SELECT COUNT(*) FROM feedback WHERE question = #{question} AND helpful = 0")
    int countNegativeByQuestion(@Param("question") String question);
}
