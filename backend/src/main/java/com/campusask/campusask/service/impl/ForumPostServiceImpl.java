package com.campusask.campusask.service.impl;

import com.campusask.campusask.entity.ForumComment;
import com.campusask.campusask.entity.ForumLike;
import com.campusask.campusask.entity.ForumPost;
import com.campusask.campusask.mapper.ForumCommentMapper;
import com.campusask.campusask.mapper.ForumLikeMapper;
import com.campusask.campusask.mapper.ForumPostMapper;
import com.campusask.campusask.service.ForumPostService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class ForumPostServiceImpl implements ForumPostService {

    @Autowired
    private ForumPostMapper forumPostMapper;

    @Autowired
    private ForumCommentMapper forumCommentMapper;

    @Autowired
    private ForumLikeMapper forumLikeMapper;

    @Override
    public Long createPost(ForumPost post) {
        forumPostMapper.insertPost(post);
        return post.getId();
    }

    @Override
    public Map<String, Object> getPosts(int page, int size, String currentUserId) {
        int offset = (page - 1) * size;
        List<ForumPost> list = forumPostMapper.selectAll(offset, size);
        int total = forumPostMapper.countAll();
        int totalPages = (int) Math.ceil((double) total / size);

        // 标记当前用户已点赞的帖子
        if (currentUserId != null) {
            Set<Long> likedIds = forumLikeMapper.selectLikedPostIdsByUser(currentUserId)
                    .stream().collect(Collectors.toSet());
            for (ForumPost post : list) {
                post.setLiked(likedIds.contains(post.getId()));
            }
        }

        Map<String, Object> result = new HashMap<>();
        result.put("list", list);
        result.put("total", total);
        result.put("totalPages", totalPages);
        result.put("page", page);
        result.put("size", size);
        return result;
    }

    @Override
    public Map<String, Object> getPostDetail(Long postId, String currentUserId) {
        // 增加浏览量
        forumPostMapper.updateViewCount(postId);

        ForumPost post = forumPostMapper.selectById(postId);
        if (post == null) {
            return null;
        }

        // 标记当前用户是否已点赞
        if (currentUserId != null) {
            ForumLike existing = forumLikeMapper.selectByPostAndUser(postId, currentUserId);
            post.setLiked(existing != null);
        }

        // 查询顶级评论
        List<ForumComment> topComments = forumCommentMapper.selectTopLevelByPostId(postId);
        // 为每条顶级评论加载子回复
        for (ForumComment comment : topComments) {
            List<ForumComment> replies = forumCommentMapper.selectRepliesByParentId(comment.getId());
            comment.setReplies(replies);
        }

        Map<String, Object> result = new HashMap<>();
        result.put("post", post);
        result.put("comments", topComments);
        return result;
    }

    @Override
    public Long addComment(ForumComment comment) {
        forumCommentMapper.insertComment(comment);
        return comment.getId();
    }

    @Override
    @Transactional
    public boolean likePost(Long postId, String userId) {
        // 检查是否已点赞
        ForumLike existing = forumLikeMapper.selectByPostAndUser(postId, userId);
        if (existing != null) {
            // 已点赞 → 取消点赞
            forumLikeMapper.delete(postId, userId);
            forumPostMapper.updateLikeCount(postId, -1);
            return false; // 当前状态：未点赞
        } else {
            // 未点赞 → 点赞
            ForumLike like = new ForumLike();
            like.setPostId(postId);
            like.setUserId(userId);
            forumLikeMapper.insert(like);
            forumPostMapper.updateLikeCount(postId, 1);
            return true; // 当前状态：已点赞
        }
    }

    @Override
    @Transactional
    public void deletePost(Long postId, String userId) {
        ForumPost post = forumPostMapper.selectById(postId);
        if (post == null) {
            throw new RuntimeException("帖子不存在");
        }
        if (!post.getUserId().equals(userId)) {
            throw new RuntimeException("只能删除自己的帖子");
        }
        forumLikeMapper.delete(postId, userId);
        forumCommentMapper.deleteByPostId(postId);
        forumPostMapper.deleteById(postId);
    }

    @Override
    public List<ForumPost> getHotPosts(int limit) {
        return forumPostMapper.selectHotPosts(limit);
    }

    @Override
    public Map<String, Object> getPostsByTag(String tag, int page, int size) {
        int offset = (page - 1) * size;
        List<ForumPost> list = forumPostMapper.selectByTag(tag, offset, size);
        int total = forumPostMapper.countByTag(tag);
        Map<String, Object> result = new HashMap<>();
        result.put("list", list);
        result.put("total", total);
        result.put("page", page);
        return result;
    }

    @Override
    public Map<String, Object> searchPosts(String keyword, int page, int size) {
        int offset = (page - 1) * size;
        List<ForumPost> list = forumPostMapper.searchPosts(keyword, offset, size);
        int total = forumPostMapper.countSearchPosts(keyword);
        Map<String, Object> result = new HashMap<>();
        result.put("list", list); result.put("total", total); result.put("page", page);
        return result;
    }

    @Override
    public List<Map<String, Object>> getHotTags(int limit) {
        return forumPostMapper.selectHotTags(limit);
    }
}
