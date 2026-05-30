package com.campusask.campusask.controller;

import com.campusask.campusask.constant.JwtClaimsConstant;
import com.campusask.campusask.dto.Result;
import com.campusask.campusask.dto.UserDTO;
import com.campusask.campusask.entity.User;
import com.campusask.campusask.service.UserService;
import com.campusask.campusask.utils.JwtUtil;
import com.campusask.campusask.properties.JwtProperties;
import com.campusask.campusask.vo.UserLoginVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
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

        //登录成功，生成jwt令牌
        Map<String, Object> claims = new HashMap<>();
        claims.put(JwtClaimsConstant.USER_ID, user.getId());
        String token = JwtUtil.createJWT(
                jwtProperties.getUserSecretKey(),
                jwtProperties.getUserTtl(),
                claims);

        UserLoginVO userLoginVO = UserLoginVO.builder()
                .id(user.getId())
                .username(user.getUsername())
                .role(user.getRole())
                .token(token)
                .build();

        // 2. 将规范的 VO 对象返回给前端
        return Result.success(userLoginVO);
    }
}
