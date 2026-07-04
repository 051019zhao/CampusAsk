package com.campusask.campusask.entity;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class ForumPost {
    private Long id;
    private String userId;
    private String title;
    private String content;
    private String images;
    private String tags;       // V4: 标签，逗号分隔
    private Integer viewCount;
    private Integer likeCount;
    private Integer favoriteCount;  // V4: 收藏数
    private Integer commentCount;   // V4: 评论数冗余
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    // 非数据库字段
    private String nickname;
    private Boolean liked;
    private Double hotScore;  // V4: 热度分数
}
