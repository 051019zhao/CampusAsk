package com.campusask.campusask.controller;

import com.campusask.campusask.dto.Result;
import com.campusask.campusask.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    @Autowired
    private UserService userService;

    @PostMapping("/register")
    public Result<String> register(@RequestBody Map<String, String> body) {
        try {
            userService.register(body.get("username"), body.get("password"));
            return Result.success("注册成功");
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    @PostMapping("/login")
    public Result<String> login(@RequestBody Map<String, String> body) {
        try {
            String token = userService.login(body.get("username"), body.get("password"));
            return Result.success(token);
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }
}
