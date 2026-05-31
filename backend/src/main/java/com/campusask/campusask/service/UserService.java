package com.campusask.campusask.service;

import com.campusask.campusask.dto.UserDTO;
import com.campusask.campusask.entity.User;

public interface UserService {

    /**
     * 用户注册
     * @param userDTO
     */
    void register(UserDTO userDTO);

    /**
     * 用户登录
     * @param userDTO
     */
    User login(UserDTO userDTO);
}
