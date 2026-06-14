package com.campusask.campusask.mapper;

import com.campusask.campusask.entity.ForumLike;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface ForumLikeMapper {
    void insert(ForumLike like);
    int delete(@Param("postId") Long postId, @Param("userId") String userId);
    ForumLike selectByPostAndUser(@Param("postId") Long postId, @Param("userId") String userId);
    List<Long> selectLikedPostIdsByUser(@Param("userId") String userId);
    int countByPostId(@Param("postId") Long postId);
}
