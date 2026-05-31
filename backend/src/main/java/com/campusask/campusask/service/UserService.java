package com.campusask.campusask.service;


import com.campusask.campusask.entity.User;
import com.campusask.campusask.mapper.UserMapper;
import com.campusask.campusask.utils.JwtUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.DigestUtils;

import java.util.HashMap;
import java.util.Map;

@Service
public class UserService {
    

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private JwtUtil jwtUtil;

    public void register(String username, String password) {
        if (userMapper.findByUsername(username) != null) {
            throw new RuntimeException("用户名已存在");
        }
        User user = new User();
        user.setUsername(username);
        user.setPassword(DigestUtils.md5DigestAsHex(password.getBytes()));
        user.setRole("user");
        userMapper.insert(user);
    }

    public String login(String username, String password) {
        User user = userMapper.findByUsername(username);
        if (user == null) {
            throw new RuntimeException("用户不存在");
        }
        String encoded = DigestUtils.md5DigestAsHex(password.getBytes());
        if (!encoded.equals(user.getPassword())) {
            throw new RuntimeException("密码错误");
        }
        Map<String, Object> claims = new HashMap<>();
        claims.put("id", user.getId());
        claims.put("username", user.getUsername());
        claims.put("role", user.getRole());
        return jwtUtil.generateToken(claims);
    }

}
