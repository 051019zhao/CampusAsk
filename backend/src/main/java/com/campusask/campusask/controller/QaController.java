package com.campusask.campusask.controller;

import com.campusask.campusask.dto.Result;
import com.campusask.campusask.service.QaService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 赵丹丹 - 周3 任务1：API 降级兜底逻辑
 *
 * 新接口路径：/api/v2/qa/ask
 * 与组员的 /api/qa/ask 隔离，互不影响。
 *
 * 响应示例：
 *   { "code":200, "data": { "source":"ai", "answer":"..." } }
 *   { "code":200, "data": { "source":"fallback", "fallbackReason":"timeout", "answer":[...] } }
 */
@RestController
@RequestMapping("/api/v2/qa")
public class QaController {

    @Autowired
    private QaService qaService;

    @PostMapping("/ask")
    public Result<Map<String, Object>> ask(@RequestBody Map<String, String> body) {
        String keyword = body.get("keyword");
        Map<String, Object> data = qaService.ask(keyword);
        return Result.success(data);
    }
}
