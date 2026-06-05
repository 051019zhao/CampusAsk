package com.campusask.campusask.config;

import com.campusask.campusask.interceptor.JwtTokenAdminInterceptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web MVC配置类，注册拦截器
 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    @Autowired
    private JwtTokenAdminInterceptor jwtTokenAdminInterceptor;

    /**
     * 注册拦截器
     */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(jwtTokenAdminInterceptor)
                // 拦截所有请求
                .addPathPatterns("/**")
                // 放行登录注册接口
                .excludePathPatterns("/auth/login", "/auth/register")
                // 放行静态资源
                .excludePathPatterns("/static/**", "/favicon.ico");
    }
}