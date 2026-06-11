package com.campusask.campusask.entity;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class ForumLike {
    private Long id;
    private Long postId;
    private Long userId;
    private LocalDateTime createdAt;
}
