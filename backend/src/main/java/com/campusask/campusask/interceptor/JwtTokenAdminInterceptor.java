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
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * JWT令牌校验的拦截器（用户端/管理端通用）
 */
@Component
@Slf4j
public class JwtTokenAdminInterceptor implements HandlerInterceptor {

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

        // ★★★ 核心修复：在这里直接对不需要登录的开放接口进行物理放行 ★★★
        if ("/api/hotspot/top10".equals(requestUri) || requestUri.startsWith("/api/qa/")) {
            log.info("【白名单放行】接口 {} 不需要 Token 验证，直接放行", requestUri);
            // 尝试解析 Token：已登录用户则设置上下文，方便记录提问者
            String token = request.getHeader(jwtProperties.getUserTokenName());
            if (token != null && !token.isEmpty()) {
                try {
                    Claims claims = JwtUtil.parseJWT(jwtProperties.getUserSecretKey(), token);
                    String username = claims.get("username", String.class);
                    if (username != null) {
                        BaseContext.setCurrentUsername(username);
                        log.info("【白名单】已登录用户 {} 访问 QA 接口", username);
                    }
                } catch (Exception e) {
                    // Token 无效或过期，忽略
                }
            }
            return true;
        }

        // 1、从请求头中获取令牌
        String token = request.getHeader(jwtProperties.getUserTokenName());

        // 2、校验令牌
        try {
            log.info("开始进行 JWT 权限校验，获取到的 Token 为: {}", token);

            // 解析 Token
            Claims claims = JwtUtil.parseJWT(jwtProperties.getUserSecretKey(), token);

            // 获取当前登录用户 ID（Integer 转 Long）
            Object userIdObj = claims.get("userId");
            if (userIdObj != null) {
                Long userId = ((Number) userIdObj).longValue();
                BaseContext.setCurrentId(userId);
                log.info("当前登录用户 ID：{}", userId);
            }

            // 获取当前登录用户学号
            String username = claims.get("username").toString();
            log.info("当前登录的校园系统用户学号 为：{}", username);

            // 获取用户角色
            String role = claims.get("role", String.class);
            log.info("当前登录用户角色：{}", role);

            // 存入 ThreadLocal 上下文
            BaseContext.setCurrentUsername(username);


            // 管理员接口权限统一校验（V3 新增 /api/admin/ 路径）
            boolean isAdminApi = requestUri.startsWith("/api/knowledge")
                    || requestUri.startsWith("/api/admin/")
                    || requestUri.startsWith("/api/contribute/pending")
                    || requestUri.endsWith("/review");
            if (isAdminApi) {
                if (!"1".equals(role)) {
                    log.warn("用户无管理员权限，拒绝访问管理员接口，当前role={}", role);
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

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
        // 请求结束，清理 ThreadLocal 里的数据，防止内存泄漏和线程复用冲突
        BaseContext.clear();
    }
}