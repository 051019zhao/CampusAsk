package com.campusask.campusask.mapper;

import com.campusask.campusask.entity.ForumPost;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface ForumPostMapper {
    void insertPost(ForumPost post);
    ForumPost selectById(@Param("id") Long id);
    List<ForumPost> selectAll(@Param("offset") int offset, @Param("size") int size);
    int countAll();
    List<ForumPost> selectByUserId(@Param("userId") String userId);
    int updateViewCount(@Param("id") Long id);
    int updateLikeCount(@Param("id") Long id, @Param("delta") int delta);
    int deleteById(@Param("id") Long id);
    List<ForumPost> selectHotPosts(@Param("limit") int limit);  // V4
    List<ForumPost> selectByTag(@Param("tag") String tag, @Param("offset") int offset, @Param("size") int size);
    int countByTag(@Param("tag") String tag);
    List<java.util.Map<String, Object>> selectHotTags(@Param("limit") int limit);
    List<ForumPost> searchPosts(@Param("keyword") String keyword, @Param("offset") int offset, @Param("size") int size);
    int countSearchPosts(@Param("keyword") String keyword);
}
