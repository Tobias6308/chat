package com.chat.config;

import com.chat.security.JwtUtil;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * API 认证拦截器
 * 
 * 验证 JWT Token 并将用户 ID 放入请求属性
 */
@Component
public class AuthInterceptor implements HandlerInterceptor {
    
	private static final Logger logger = LoggerFactory.getLogger(AuthInterceptor.class);
	
    @Autowired
    private JwtUtil jwtUtil;
    
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // 放行 OPTIONS 预检请求
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        
        // 获取 Authorization 头
        String authHeader = request.getHeader("authorization");
        
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            response.setStatus(401);
            response.getWriter().write("{\"code\":\"UNAUTHORIZED\",\"message\":\"未授权\"}");
            logger.info("UNAUTHORIZED : authHeader - {} ", authHeader);
            return false;
        }
        
        // 解析 token
        String token = authHeader.substring(7);
        String userId = jwtUtil.parseToken(token);
        
        if (userId == null) {
            response.setStatus(401);
            response.getWriter().write("{\"code\":\"INVALID_TOKEN\",\"message\":\"无效的令牌\"}");
            return false;
        }
        
        // 将用户 ID 放入请求属性
        request.setAttribute("userId", userId);
        
        return true;
    }
}