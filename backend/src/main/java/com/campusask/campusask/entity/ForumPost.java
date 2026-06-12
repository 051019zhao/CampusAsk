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
    private Integer viewCount;
    private Integer likeCount;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    // 非数据库字段
    private String nickname;
    private Integer commentCount;
    private Boolean liked;
}
