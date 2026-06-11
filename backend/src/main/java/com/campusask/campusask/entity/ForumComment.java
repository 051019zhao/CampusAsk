package com.campusask.campusask.entity;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class ForumComment {
    private Long id;
    private Long postId;
    private Long userId;
    private String content;
    private LocalDateTime createdAt;

    // 非数据库字段
    private String nickname;
}
