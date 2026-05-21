package com.campusask.campusask.controller;

import com.campusask.campusask.dto.Result;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class TestController {

    @GetMapping("/hello")
    public Result<String> hello() {
        return Result.success("CampusAsk 后端服务正常运行！");
    }
}