package com.chat.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web MVC 配置
 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    @Autowired
    private AuthInterceptor authInterceptor;

    @Autowired
    private AdminAuthInterceptor adminAuthInterceptor;

    @Autowired
    private ApiLoggingInterceptor apiLoggingInterceptor;

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
            .allowedOriginPatterns("http://localhost:*")
            .allowedMethods("*")
            .allowedHeaders("*")
            .allowCredentials(true)
            .maxAge(3600);
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/uploads/**")
            .addResourceLocations("file:./uploads/");
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // 用户认证拦截器
        registry.addInterceptor(authInterceptor)
            .addPathPatterns("/api/chat/**", "/api/user/**", "/api/friend/**", "/api/group/**", "/api/message/**")
            .excludePathPatterns(
                "/api/auth/register",
                "/api/auth/login",
                "/api/chat/upload/**",
                "/api/chat/config"
            );

        // 管理员认证拦截器
        registry.addInterceptor(adminAuthInterceptor)
            .addPathPatterns("/api/admin/**")
            .excludePathPatterns(
                "/api/admin/login",
                "/api/admin/init-admin",
                "/api/chat/upload/**",
                "/api/chat/config"
            );

        // API 日志拦截器
        registry.addInterceptor(apiLoggingInterceptor)
            .addPathPatterns("/api/**");
    }
}