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
    List<ForumPost> selectByUserId(@Param("userId") Long userId);
    int updateViewCount(@Param("id") Long id);
    int updateLikeCount(@Param("id") Long id, @Param("delta") int delta);
    int deleteById(@Param("id") Long id);
}
