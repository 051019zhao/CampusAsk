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
    @Override 
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // 判断当前拦截到的是Controller的方法还是其他资源（如静态页面、Swagger等）
        if (!(handler instanceof HandlerMethod)) {
            
            return true;
        }

        String requestUri = request.getRequestURI();
        log.info("请求URI: {}", requestUri);
 
        // 1、从请求头中获取令牌
        String token = request.getHeader(jwtProperties.getUserTokenName());

        // 2、校验令牌
        try {
            log.info("开始进行 JWT 权限校验，获取到的 Token 为: {}", token);

            // 解析 Token
            Claims claims = JwtUtil.parseJWT(jwtProperties.getUserSecretKey(), token);

            // 获取当前登录用户 ID
            Long userId = Long.valueOf(claims.get("userId").toString());

            log.info("当前登录的校园系统用户 ID 为：{}", userId);

            // 获取用户角色
            String role = claims.get("role", String.class);
            log.info("当前登录用户角色：{}", role);
 
            // 存入 ThreadLocal 上下文
            BaseContext.setCurrentId(userId);

             // 3、检查管理员权限（知识库管理接口需要管理员权限）
            if (requestUri.startsWith("/api/knowledge")) {
                // 检查是否为管理员
                if (!"admin".equals(role)) {
                    log.warn("用户无管理员权限，拒绝访问管理员接口");
                    response.setStatus(403);
                    response.setContentType("application/json;charset=UTF-8");
                    response.getWriter().write("{\"code\": 403, \"message\": \"无管理员权限\"}");
                    return false;
                }
            }
 
            // 4、通过，放行
            return true;
        } catch (Exception ex) {
            log.warn("JWT 校验失败或已过期，拦截请求。原因: {}", ex.getMessage());
            // 4、不通过，响应 401 状态码（未授权）
            response.setStatus(401);
             response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"code\": 401, \"message\": \"登录已过期，请重新登录\"}");
            return false;
        }
    }
}