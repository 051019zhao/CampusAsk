package com.campusask.campusask.entity;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class ForumLike {
    private Long id;
    private Long postId;
    private String userId;
    private LocalDateTime createdAt;
}
