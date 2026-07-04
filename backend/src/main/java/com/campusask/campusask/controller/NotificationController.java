package com.campusask.campusask.controller;

import com.campusask.campusask.context.BaseContext;
import com.campusask.campusask.dto.Result;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController
@RequestMapping("/api")
public class NotificationController {
    @Autowired private JdbcTemplate jdbc;

    @GetMapping("/notifications")
    public Result<List<Map<String,Object>>> list() {
        String uid = BaseContext.getCurrentUsername();
        if (uid == null) return Result.error("未登录");
        return Result.success(jdbc.queryForList(
            "SELECT * FROM notification WHERE user_id=? ORDER BY created_at DESC LIMIT 20", uid));
    }

    @PutMapping("/notifications/{id}/read")
    public Result<String> read(@PathVariable Long id) {
        jdbc.update("UPDATE notification SET is_read=1 WHERE id=?", id);
        return Result.success("ok");
    }

    @GetMapping("/notifications/unread-count")
    public Result<Map<String,Object>> unread() {
        String uid = BaseContext.getCurrentUsername();
        int c = jdbc.queryForObject(
            "SELECT COUNT(*) FROM notification WHERE user_id=? AND is_read=0", Integer.class, uid);
        return Result.success(Map.of("count", c));
    }
}
