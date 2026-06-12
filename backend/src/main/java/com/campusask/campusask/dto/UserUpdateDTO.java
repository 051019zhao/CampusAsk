package com.campusask.campusask.dto;

import lombok.Data;

@Data
public class UserUpdateDTO {
    private String nickname;     // 新昵称
    private String oldPassword;  // 原密码（选填，不改密码则为空）
    private String newPassword;  // 新密码（选填，不改密码则为空）
    private String avatarUrl;      // 新头像的云端 URL 链接
    private String motto;          // 新的个性名言文本
}
