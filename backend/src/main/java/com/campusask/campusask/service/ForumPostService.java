package com.campusask.campusask.service;

import com.campusask.campusask.entity.ForumPost;
import com.campusask.campusask.entity.ForumComment;

import java.util.Map;

public interface ForumPostService {
    /** 创建帖子 */
    Long createPost(ForumPost post);

    /** 分页获取帖子列表（含当前用户点赞状态） */
    Map<String, Object> getPosts(int page, int size, Long currentUserId);

    /** 获取帖子详情（含评论及当前用户点赞状态） */
    Map<String, Object> getPostDetail(Long postId, Long currentUserId);

    /** 添加评论 */
    Long addComment(ForumComment comment);

    /** 点赞/取消点赞（基于数据库记录，防止重复点赞） */
    boolean likePost(Long postId, Long userId);

    /** 删除帖子（仅作者） */
    void deletePost(Long postId, Long userId);
}
