package com.campusask.campusask.controller;

import com.campusask.campusask.context.BaseContext;
import com.campusask.campusask.dto.Result;
import com.campusask.campusask.entity.ForumComment;
import com.campusask.campusask.entity.ForumPost;
import com.campusask.campusask.service.ForumPostService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/forum")
public class ForumPostController {

    @Autowired
    private ForumPostService forumPostService;

    /**
     * 获取帖子列表（分页）
     */
    @GetMapping("/posts")
    public Result<Map<String, Object>> getPosts(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        Long userId = BaseContext.getCurrentId();
        return Result.success(forumPostService.getPosts(page, size, userId));
    }

    /**
     * 获取帖子详情（含评论）
     */
    @GetMapping("/posts/{id}")
    public Result<Map<String, Object>> getPostDetail(@PathVariable Long id) {
        Long userId = BaseContext.getCurrentId();
        Map<String, Object> data = forumPostService.getPostDetail(id, userId);
        if (data == null) {
            return Result.error("帖子不存在");
        }
        return Result.success(data);
    }

    /**
     * 创建帖子
     */
    @PostMapping("/posts")
    public Result<Long> createPost(@RequestBody Map<String, String> body) {
        String title = body.get("title");
        String content = body.get("content");
        String images = body.get("images");

        if (title == null || title.trim().isEmpty()) {
            return Result.error("标题不能为空");
        }
        if (content == null || content.trim().isEmpty()) {
            return Result.error("内容不能为空");
        }

        ForumPost post = new ForumPost();
        post.setUserId(BaseContext.getCurrentId());
        post.setTitle(title.trim());
        post.setContent(content.trim());
        post.setImages(images);

        Long id = forumPostService.createPost(post);
        return Result.success(id);
    }

    /**
     * 添加评论
     */
    @PostMapping("/posts/{postId}/comments")
    public Result<Long> addComment(@PathVariable Long postId, @RequestBody Map<String, String> body) {
        String content = body.get("content");
        if (content == null || content.trim().isEmpty()) {
            return Result.error("评论内容不能为空");
        }

        ForumComment comment = new ForumComment();
        comment.setPostId(postId);
        comment.setUserId(BaseContext.getCurrentId());
        comment.setContent(content.trim());

        Long id = forumPostService.addComment(comment);
        return Result.success(id);
    }

    /**
     * 点赞/取消点赞（基于数据库记录，自动切换，防止重复点赞）
     */
    @PostMapping("/posts/{id}/like")
    public Result<Map<String, Object>> likePost(@PathVariable Long id) {
        Long userId = BaseContext.getCurrentId();
        boolean isLiked = forumPostService.likePost(id, userId);
        Map<String, Object> result = new java.util.HashMap<>();
        result.put("liked", isLiked);
        result.put("message", isLiked ? "点赞成功" : "取消点赞");
        return Result.success(result);
    }

    /**
     * 删除帖子
     */
    @DeleteMapping("/posts/{id}")
    public Result<String> deletePost(@PathVariable Long id) {
        Long userId = BaseContext.getCurrentId();
        forumPostService.deletePost(id, userId);
        return Result.success("删除成功");
    }
}
