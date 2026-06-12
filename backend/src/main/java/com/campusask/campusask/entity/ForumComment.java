package com.campusask.campusask.entity;

import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class ForumComment {
    private Long id;
    private Long postId;
    private String userId;
    private String content;
    private Long parentId;
    private String replyToUserId;
    private LocalDateTime createdAt;

    // 非数据库字段
    private String nickname;
    private String replyToNickname;
    private List<ForumComment> replies;
}
