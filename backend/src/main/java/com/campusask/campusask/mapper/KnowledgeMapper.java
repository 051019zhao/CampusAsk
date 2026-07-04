package com.campusask.campusask.mapper;

import com.campusask.campusask.entity.Knowledge;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface KnowledgeMapper {

    // 关键词搜索（组员已添加）
    List<Knowledge> searchByKeyword(@Param("keyword") String keyword);

    // 新增知识点（Excel导入用）
    @Insert("INSERT INTO knowledge(title, content, category, keywords, view_count, created_by, created_at) " +
            "VALUES(#{title}, #{content}, #{category}, #{keywords}, #{viewCount}, #{createdBy}, NOW())")
    int insert(Knowledge knowledge);

     // 批量插入知识点
    void batchInsert(@Param("list") List<Knowledge> list);

    // 查询全部知识点（前端列表展示用）
     // 分页查询知识点（支持关键词和分类筛选）
    List<Knowledge> listByCondition(@Param("keyword") String keyword, @Param("category") String category, @Param("offset") int offset, @Param("size") int size);
 
    // 统计符合条件的知识点数量
    int countByCondition(@Param("keyword") String keyword, @Param("category") String category);
    @Select("SELECT * FROM knowledge ORDER BY created_at DESC")
    List<Knowledge> listAll();

     // 根据ID查询知识点
    @Select("SELECT * FROM knowledge WHERE id = #{id}")
    Knowledge selectById(@Param("id") Long id);
   // 获取所有分类列表
    @Select("SELECT DISTINCT category FROM knowledge WHERE category IS NOT NULL AND category != '' ORDER BY category")
    List<String> getAllCategories();
    // 更新知识点
    @Update("UPDATE knowledge SET title = #{title}, content = #{content}, category = #{category}, keywords = #{keywords} WHERE id = #{id}")
    int update(Knowledge knowledge);
 
    // 删除知识点
    @Delete("DELETE FROM knowledge WHERE id = #{id}")
    int deleteById(@Param("id") Long id);

    @Select("SELECT COUNT(*) FROM knowledge")
    int countAll();

    @Select("SELECT category AS name, COUNT(*) AS value FROM knowledge WHERE category IS NOT NULL AND category != '' GROUP BY category ORDER BY value DESC")
    List<java.util.Map<String, Object>> countByCategory();

    /** V4: 浏览量+1 */
    @Update("UPDATE knowledge SET view_count = view_count + 1 WHERE id = #{id}")
    int incrementViewCount(@Param("id") Long id);

    /** V4: 根据标题查询知识（用于重复检测） */
    @Select("SELECT * FROM knowledge WHERE title = #{title} LIMIT 1")
    Knowledge selectByTitle(@Param("title") String title);
}