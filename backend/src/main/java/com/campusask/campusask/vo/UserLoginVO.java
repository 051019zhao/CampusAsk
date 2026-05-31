package com.campusask.campusask.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.io.Serializable;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserLoginVO implements Serializable {

    private static final long serialVersionUID = 1L;

    // 用户唯一ID
    private Long id;

    // 学号/工号（对应数据库的 username）
    private String username;

    // 身份标识：0 学生，1 管理员
    private String role;

    // JWT 访问令牌
    private String token;
}