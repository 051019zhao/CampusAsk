package com.campusask.campusask.controller;

import com.campusask.campusask.context.BaseContext;
import com.campusask.campusask.dto.Result;
import com.campusask.campusask.entity.ForumComment;
import com.campusask.campusask.entity.ForumPost;
import com.campusask.campusask.service.ForumPostService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/forum")
public class ForumPostController {

    @Autowired
    private ForumPostService forumPostService;

    /** V4: 热门帖子排行榜 */
    @GetMapping("/hot")
    public Result<java.util.List<ForumPost>> hotPosts(
            @RequestParam(defaultValue = "10") int limit) {
        return Result.success(forumPostService.getHotPosts(limit));
    }

    /** V4: 按标签搜索帖子 */
    @GetMapping("/posts/by-tag")
    public Result<Map<String, Object>> postsByTag(
            @RequestParam String tag,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        return Result.success(forumPostService.getPostsByTag(tag, page, size));
    }

    /** V4: 热门标签列表 */
    @GetMapping("/tags")
    public Result<java.util.List<Map<String, Object>>> hotTags(
            @RequestParam(defaultValue = "10") int limit) {
        return Result.success(forumPostService.getHotTags(limit));
    }

    /** V4: 搜索帖子（标题+内容） */
    @GetMapping("/posts/search")
    public Result<Map<String, Object>> searchPosts(
            @RequestParam String q,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        return Result.success(forumPostService.searchPosts(q, page, size));
    }

    @Autowired
    private com.campusask.campusask.utils.OssUtil ossUtil;

    @Autowired
    private JdbcTemplate jdbc;

    /** V4: 上传帖子图片到 OSS */
    @PostMapping("/upload-image")
    public Result<String> uploadImage(@RequestParam("file") org.springframework.web.multipart.MultipartFile file) {
        if (file == null || file.isEmpty()) return Result.error("文件为空");
        try {
            String url = ossUtil.uploadAvatar(file); // 复用头像上传逻辑
            return Result.success(url);
        } catch (Exception e) {
            return Result.error("上传失败：" + e.getMessage());
        }
    }

    /**
     * 获取帖子列表（分页）
     */
    @GetMapping("/posts")
    public Result<Map<String, Object>> getPosts(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        String userId = BaseContext.getCurrentUsername();
        return Result.success(forumPostService.getPosts(page, size, userId));
    }

    /**
     * 获取帖子详情（含评论）
     */
    @GetMapping("/posts/{id}")
    public Result<Map<String, Object>> getPostDetail(@PathVariable Long id) {
        String userId = BaseContext.getCurrentUsername();
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
        post.setUserId(BaseContext.getCurrentUsername());
        post.setTitle(title.trim());
        post.setContent(content.trim());
        post.setImages(images);
        post.setTags(body.get("tags"));  // V4: 标签

        Long id = forumPostService.createPost(post);
        return Result.success(id);
    }

    /**
     * 添加评论或回复
     */
    @PostMapping("/posts/{postId}/comments")
    public Result<Long> addComment(@PathVariable Long postId, @RequestBody Map<String, String> body) {
        String content = body.get("content");
        if (content == null || content.trim().isEmpty()) {
            return Result.error("评论内容不能为空");
        }

        ForumComment comment = new ForumComment();
        comment.setPostId(postId);
        comment.setUserId(BaseContext.getCurrentUsername());
        comment.setContent(content.trim());

        // 回复功能：如果传了 parentId，则为子回复
        String parentIdStr = body.get("parentId");
        if (parentIdStr != null && !parentIdStr.isEmpty()) {
            comment.setParentId(Long.parseLong(parentIdStr));
        }
        String replyToUserIdStr = body.get("replyToUserId");
        if (replyToUserIdStr != null && !replyToUserIdStr.isEmpty()) {
            comment.setReplyToUserId(replyToUserIdStr);
        }

        Long id = forumPostService.addComment(comment);
        // V4: 通知帖子作者
        ForumPost post = forumPostService.getPostById(postId);
        String myUid = BaseContext.getCurrentUsername();
        String notifyTarget = body.get("replyToUserId");
        if (notifyTarget == null || notifyTarget.isEmpty()) notifyTarget = post != null ? post.getUserId() : null;
        if (notifyTarget != null && !notifyTarget.equals(myUid)) {
            try { jdbc.update("INSERT INTO notification(user_id,type,content,post_id) VALUES(?,'comment',?,?)", notifyTarget, (myUid!=null?myUid:"匿名")+" 评论了你的帖子", postId); } catch(Exception e){}
        }
        return Result.success(id);
    }

    /** V4: 获取帖子作者（内部用） */
    public ForumPost getPostById(Long id) { return forumPostService.getPostById(id); }

    /**
     * 点赞/取消点赞（基于数据库记录，自动切换，防止重复点赞）
     */
    @PostMapping("/posts/{id}/like")
    public Result<Map<String, Object>> likePost(@PathVariable Long id) {
        String userId = BaseContext.getCurrentUsername();
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
        String userId = BaseContext.getCurrentUsername();
        forumPostService.deletePost(id, userId);
        return Result.success("删除成功");
    }

    /** V4: 收藏帖子 */
    @PostMapping("/posts/{id}/favorite")
    public Result<Map<String,Object>> favoritePost(@PathVariable Long id) {
        String uid = BaseContext.getCurrentUsername();
        int c = jdbc.queryForObject("SELECT COUNT(*) FROM forum_favorite WHERE user_id=? AND post_id=?", Integer.class, uid, id);
        if (c > 0) { jdbc.update("DELETE FROM forum_favorite WHERE user_id=? AND post_id=?", uid, id); return Result.success(Map.of("favorited",false)); }
        else { jdbc.update("INSERT INTO forum_favorite(user_id,post_id) VALUES(?,?)", uid, id); return Result.success(Map.of("favorited",true)); }
    }

    /** V4: 评论点赞 */
    @PostMapping("/comments/{id}/like")
    public Result<Map<String,Object>> likeComment(@PathVariable Long id) {
        String uid = BaseContext.getCurrentUsername();
        int c = jdbc.queryForObject("SELECT COUNT(*) FROM forum_comment_like WHERE comment_id=? AND user_id=?", Integer.class, id, uid);
        if (c > 0) {
            jdbc.update("DELETE FROM forum_comment_like WHERE comment_id=? AND user_id=?", id, uid);
            jdbc.update("UPDATE forum_comment SET like_count = GREATEST(like_count-1, 0) WHERE id=?", id);
            return Result.success(Map.of("liked",false));
        } else {
            jdbc.update("INSERT INTO forum_comment_like(comment_id,user_id) VALUES(?,?)", id, uid);
            jdbc.update("UPDATE forum_comment SET like_count = like_count+1 WHERE id=?", id);
            return Result.success(Map.of("liked",true));
        }
    }
}
