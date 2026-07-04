package com.campusask.campusask.service;

import com.campusask.campusask.entity.ForumPost;
import com.campusask.campusask.entity.ForumComment;

import java.util.Map;

public interface ForumPostService {
    /** 创建帖子 */
    Long createPost(ForumPost post);

    /** 分页获取帖子列表（含当前用户点赞状态） */
    Map<String, Object> getPosts(int page, int size, String currentUserId);

    /** 获取帖子详情（含评论及当前用户点赞状态，评论含层级回复） */
    Map<String, Object> getPostDetail(Long postId, String currentUserId);

    /** 添加评论或回复 */
    Long addComment(ForumComment comment);

    /** 点赞/取消点赞（基于数据库记录，防止重复点赞） */
    boolean likePost(Long postId, String userId);

    /** 删除帖子（仅作者） */
    void deletePost(Long postId, String userId);

    /** V4: 获取热门帖子排行榜 */
    java.util.List<ForumPost> getHotPosts(int limit);

    /** V4: 按标签搜索帖子 */
    Map<String, Object> getPostsByTag(String tag, int page, int size);

    /** V4: 获取热门标签 */
    java.util.List<Map<String, Object>> getHotTags(int limit);

    /** V4: 搜索帖子 */
    Map<String, Object> searchPosts(String keyword, int page, int size);

    /** V4: 获取帖子简要信息 */
    ForumPost getPostById(Long id);
}
