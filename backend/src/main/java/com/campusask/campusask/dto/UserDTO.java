package com.campusask.campusask.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class UserDTO {
    //用户昵称
    private String nickname;
    //邮箱或者学号
    private String username;
    //密码
    private String password;
    //身份
    private String role;//0学生1管理员
}
