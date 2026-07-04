package com.campusask.campusask.mapper;

import com.campusask.campusask.entity.KnowledgeGap;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface KnowledgeGapMapper {

    /** 插入或更新（同一问题合并计数） */
    int upsertGap(KnowledgeGap gap);

    /** 分页查询待审核 */
    List<KnowledgeGap> selectPending(@Param("offset") int offset, @Param("size") int size);

    /** 待审核总数 */
    int countPending();

    /** 查询已忽略 */
    List<KnowledgeGap> selectIgnored(@Param("offset") int offset, @Param("size") int size);

    /** 已忽略总数 */
    int countIgnored();

    /** 统计所有来源的缺口数 */
    List<java.util.Map<String, Object>> countBySource();

    /** 按 ID 查询 */
    KnowledgeGap selectById(@Param("id") Long id);

    /** 更新状态（审核通过/忽略） */
    int updateStatus(@Param("id") Long id,
                     @Param("status") String status,
                     @Param("category") String category,
                     @Param("keywords") String keywords,
                     @Param("reviewedBy") Long reviewedBy);

    /** 根据问题匹配（用于自动查重） */
    KnowledgeGap selectByQuestion(@Param("question") String question);
}
