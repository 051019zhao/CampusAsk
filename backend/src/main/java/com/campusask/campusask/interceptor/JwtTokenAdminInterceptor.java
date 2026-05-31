package com.campusask.campusask.interceptor;

import com.campusask.campusask.context.BaseContext;
import com.campusask.campusask.properties.JwtProperties;
import com.campusask.campusask.utils.JwtUtil;
import io.jsonwebtoken.Claims;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor; // ★ 核心修复：必须显式引入 Spring 的 HandlerInterceptor

/**
 * JWT令牌校验的拦截器（用户端/管理端通用）
 */
@Component
@Slf4j
public class JwtTokenAdminInterceptor implements HandlerInterceptor { // ★ 确保 implements 不爆红

    @Autowired
    private JwtProperties jwtProperties;

    /**
     * 校验jwt
     */
    @Override // 规范加上重写注解
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // 判断当前拦截到的是Controller的方法还是其他资源（如静态页面、Swagger等）
        if (!(handler instanceof HandlerMethod)) {
            // 当前拦截到的不是动态方法，直接放行
            return true;
        }

        // 1、从请求头中获取令牌（通常前端传过来的 Header 名字叫 "token" 或 "Authorization"）
        String token = request.getHeader(jwtProperties.getUserTokenName());

        // 2、校验令牌
        try {
            log.info("开始进行 JWT 权限校验，获取到的 Token 为: {}", token);

            // 解析 Token
            Claims claims = JwtUtil.parseJWT(jwtProperties.getUserSecretKey(), token);

            // ★ 核心修复：将之前的 EMP_ID 变更为符合你当前系统的 USER_ID 或者是 "userId"
            // 注意：请去你的常量类（如 JwtClaimsConstant）里确保把原来的 EMP_ID 换成 USER_ID = "userId"
            Long userId = Long.valueOf(claims.get("userId").toString());

            log.info("当前登录的校园系统用户 ID 为：{}", userId);

            // 存入 ThreadLocal 上下文，方便后续 Service 随时随地获取当前登录用户
            BaseContext.setCurrentId(userId);

            // 3、通过，放行
            return true;
        } catch (Exception ex) {
            log.warn("JWT 校验失败或已过期，拦截请求。原因: {}", ex.getMessage());
            // 4、不通过，响应 401 状态码（未授权）
            response.setStatus(401);
            return false;
        }
    }
}