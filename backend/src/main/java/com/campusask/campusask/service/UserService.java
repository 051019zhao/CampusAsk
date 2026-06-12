package com.campusask.campusask.service;

import com.campusask.campusask.dto.PageResult;
import com.campusask.campusask.dto.UserDTO;
import com.campusask.campusask.dto.UserUpdateDTO;
import com.campusask.campusask.entity.User;

import java.util.List;
import java.util.Map;

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

    /**
     * 更新用户个人信息
     * @param username
     * @param userUpdateDTO
     */
    void updateProfile(String username, UserUpdateDTO userUpdateDTO);

    /**
     * 根据学号/工号查询用户的完整信息
     * @param username 学号或工号
     * @return 包含用户所有字段的实体对象
     */
    User getByUsername(String username);

    /**
     * 查询历史提问记录
     * @param currentUsername
     * @param page
     * @param size
     * @return
     */
    PageResult getQuestionHistory(Long currentUsername, Integer page, Integer size);

    /**
     * 获取茶话会记录
     * @param currentUsername
     * @param page
     * @param size
     * @return
     */
    PageResult getForumPostHistory(Long currentUsername, Integer page, Integer size);

    /**
     * 获取茶话会点赞记录
     * @param currentUsername
     * @return
     */
    List<Map<String, Object>> getUserLikeInteractions(Long currentUsername);

    /**
     * 获取茶话会评论记录
     * @param currentUsername
     * @return
     */
    List<Map<String, Object>> getUserCommentInteractions(Long currentUsername);
}
