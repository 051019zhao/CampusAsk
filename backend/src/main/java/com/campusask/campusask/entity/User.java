package com.campusask.campusask.entity;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class User {
    //用户昵称
    private String nickname;
    //主键
    private Long id;
    //邮箱或者学号
    private String username;
    //密码
    private String password;
    //身份
    private String role;//0学生1管理员
    //创建时间
    private LocalDateTime createAt;
}
