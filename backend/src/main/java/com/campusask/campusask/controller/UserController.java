package com.campusask.campusask.controller;

import com.campusask.campusask.constant.JwtClaimsConstant;
import com.campusask.campusask.context.BaseContext;
import com.campusask.campusask.dto.PageResult;
import com.campusask.campusask.dto.Result;
import com.campusask.campusask.dto.UserDTO;
import com.campusask.campusask.dto.UserUpdateDTO;
import com.campusask.campusask.entity.User;
import com.campusask.campusask.service.UserService;
import com.campusask.campusask.utils.JwtUtil;
import com.campusask.campusask.properties.JwtProperties;
import com.campusask.campusask.utils.OssUtil;
import com.campusask.campusask.vo.UserLoginVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping
@Slf4j
@CrossOrigin
public class UserController {

    @Autowired
    private UserService userService;

    @Autowired
    private JwtProperties jwtProperties;

    @Autowired
    private OssUtil ossUtil;
    /**
     * 用户注册接口
     */
    @PostMapping("/auth/register")
    public Result register(@RequestBody UserDTO userDTO){
        log.info("用户注册：{}",userDTO);
        userService.register(userDTO);
        return Result.success();
    }

    /**
     * 用户登录接口
     */
    @PostMapping("/auth/login")
    public Result<UserLoginVO> login(@RequestBody UserDTO userDTO){
        log.info("用户登录：{}",userDTO);
        User user = userService.login(userDTO);

        // 登录成功，生成jwt令牌
        Map<String, Object> claims = new HashMap<>();
        claims.put("userId", user.getId());
        claims.put("username", user.getUsername()); // 确保这行加上了！
        claims.put("role", user.getRole());
        String token = JwtUtil.createJWT(jwtProperties.getUserSecretKey(), jwtProperties.getUserTtl(), claims);

        UserLoginVO userLoginVO = UserLoginVO.builder()
                .id(user.getId())
                .username(user.getUsername())
                .role(user.getRole())
                .token(token)
                .build();

        // 2. 将规范的 VO 对象返回给前端
        return Result.success(userLoginVO);
    }

    /**
     * 修改个人信息接口
     */
    @PutMapping("/auth/profile")
    public Result<String> updateProfile(@RequestBody UserUpdateDTO userUpdateDTO) {
        // 1. 核心修复：改从 BaseContext 中获取当前登录用户的学号
        String currentUsername = BaseContext.getCurrentUsername();

        if (currentUsername == null) {
            return Result.error("未登录或登录已过期");
        }

        // 2. 调用 Service 层
        try {
            userService.updateProfile(currentUsername, userUpdateDTO);
            return Result.success("个人信息修改成功");
        } catch (IllegalArgumentException e) {
            // 捕获不合规的业务异常（例如：原密码输入错误、昵称太长等）
            return Result.error(e.getMessage());
        } catch (Exception e) {
            return Result.error("系统繁忙，请稍后再试");
        }
    }

    /**
     * 获取信息
     */
    @GetMapping("/auth/profile")
    public Result<User> getProfile() {
        // 1. 核心修复：改从 BaseContext 中获取当前登录用户的学号
        String currentUsername = BaseContext.getCurrentUsername();

        if (currentUsername == null) {
            return Result.error("未登录或登录已过期");
        }

        // 2. 调用 Service 层去数据库查询该用户的完整信息
        User user = userService.getByUsername(currentUsername);

        // 3. 返回给前端
        if (user != null) {
            user.setPassword(null);
        }
        return Result.success(user);
    }

    /**
     * 单独的头像上传
     */
    @PutMapping("/auth/avatar")
    public Result<String> uploadAvatar(MultipartFile file) {
        // 调用你们团队写好的工具类，把图片扔到云端，拿到完整的 https://... 链接
        String url = ossUtil.uploadAvatar(file);
        // 直接把链接返给前端
        return Result.success(url);
    }

    /**
     * 获取当前登录用户的提问历史记录
     */
    @GetMapping("/auth/history")
    public Result<PageResult> getHistory(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer size) {

        // question_log 表的 user_id 仍引用 user.id（数字），使用 getCurrentId()
        Long currentUserId = BaseContext.getCurrentId();

        if (currentUserId == null) {
            return Result.error("未登录或登录已过期");
        }

        PageResult pageResult = userService.getQuestionHistory(currentUserId, page, size);
        return Result.success(pageResult);
    }

    /**
     * 获取茶话会历史发帖记录
     */
    @GetMapping("/auth/forum-history")
    public Result<PageResult> getForumHistory(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer size) {

        // 从安全 ThreadLocal 管道中抓出当前登录用户的学号/工号字符串
        String usernameStr = BaseContext.getCurrentUsername();

        // 调用服务层拉取茶话会记录
        PageResult pageResult = userService.getForumPostHistory(usernameStr, page, size);
        return Result.success(pageResult);
    }
    /**
     * 拉取茶话会点赞记录
     */
    @GetMapping("/auth/my-likes")
    public Result<List<Map<String, Object>>> getMyLikes() {
        // 1. 现有的安全上下文管道中捞出当前登录用户的学号/工号字符串
        String usernameStr = BaseContext.getCurrentUsername();

        log.info("🚀 收到拉取点赞历史请求，当前登录用户学号: {}", usernameStr);

        // 2.Service 层去查 forum_like 表（如果没有写复杂的业务，可以直接用统一的查询服务）
        // 如果 userService 还没声明该方法，可以直接调用 mapper 或者先用下方我给的万能动态 SQL 查询
        List<Map<String, Object>> likesList = userService.getUserLikeInteractions(usernameStr);

        return Result.success(likesList);
    }

    /**
     * 拉取历史评论
     */
    @GetMapping("/auth/my-comments")
    public Result<List<Map<String, Object>>> getMyComments() {
        String usernameStr = BaseContext.getCurrentUsername();

        log.info("🚀 收到拉取评论历史请求，当前登录用户学号: {}", usernameStr);

        List<Map<String, Object>> commentsList = userService.getUserCommentInteractions(usernameStr);

        return Result.success(commentsList);
    }
}
