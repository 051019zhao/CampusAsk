package com.campusask.campusask.dto;

import jakarta.validation.constraints.*;

public record AskRequest(
        @NotBlank(message="请输入问题") @Size(max=2000,message="问题不能超过 2000 字符") String keyword,
        @Pattern(regexp="[A-Za-z0-9_-]{1,100}",message="会话 ID 无效") String sessionId,
        Boolean memoryEnabled, String imageUrl) {}
