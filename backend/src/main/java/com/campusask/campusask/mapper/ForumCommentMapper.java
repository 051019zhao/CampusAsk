package com.campusask.campusask.mapper;

import com.campusask.campusask.entity.ForumComment;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface ForumCommentMapper {
    void insertComment(ForumComment comment);
    List<ForumComment> selectByPostId(@Param("postId") Long postId);
    /** 查询帖子的顶级评论（parent_id IS NULL） */
    List<ForumComment> selectTopLevelByPostId(@Param("postId") Long postId);
    /** 查询某条评论的所有子回复 */
    List<ForumComment> selectRepliesByParentId(@Param("parentId") Long parentId);
    int countByPostId(@Param("postId") Long postId);
    int deleteById(@Param("id") Long id);
    int deleteByPostId(@Param("postId") Long postId);
}
