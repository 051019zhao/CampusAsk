package com.campusask.campusask.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class CorsConfig implements WebMvcConfigurer {
    @org.springframework.beans.factory.annotation.Value("${app.cors.allowed-origins:http://localhost:8081,http://127.0.0.1:8081,http://localhost:8082,http://127.0.0.1:8082}")
    private String[] allowedOrigins;
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        for(String path:new String[]{"/api/**","/auth/**"}) registry.addMapping(path)
                .allowedOrigins(allowedOrigins)
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("Content-Type","token")
                .exposedHeaders("X-Request-ID");
    }
}
