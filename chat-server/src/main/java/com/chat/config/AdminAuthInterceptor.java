package com.chat.config;

import com.chat.security.AdminJwtUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

@Component
public class AdminAuthInterceptor implements HandlerInterceptor {

    private static final Logger logger = LoggerFactory.getLogger(AdminAuthInterceptor.class);

    @Autowired
    private AdminJwtUtil adminJwtUtil;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String path = request.getRequestURI();

        if (path.equals("/api/admin/login") || path.equals("/api/admin/init-admin")) {
            return true;
        }

        String authHeader = request.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            logger.warn("Missing or invalid Authorization header for path: {}", path);
            response.setStatus(401);
            response.setContentType("application/json");
            response.getWriter().write("{\"code\":\"UNAUTHORIZED\",\"message\":\"无效的令牌或令牌已过期\"}");
            return false;
        }

        String token = authHeader.substring(7);
        
        if (token == null || token.isEmpty()) {
            logger.warn("Empty token for path: {}", path);
            response.setStatus(401);
            response.setContentType("application/json");
            response.getWriter().write("{\"code\":\"UNAUTHORIZED\",\"message\":\"无效的令牌\"}");
            return false;
        }
        
        String adminId = adminJwtUtil.parseToken(token);
        if (adminId == null) {
            logger.warn("Invalid token for path: {}, token length: {}, first 20 chars: {}", 
                path, token.length(), token.substring(0, Math.min(20, token.length())));
            response.setStatus(401);
            response.setContentType("application/json");
            response.getWriter().write("{\"code\":\"UNAUTHORIZED\",\"message\":\"无效的令牌或令牌已过期\"}");
            return false;
        }

        // 设置 adminId 到 request 属性，供控制器使用
        request.setAttribute("adminId", adminId);
        logger.debug("Admin authenticated: {} for path: {}", adminId, path);

        return true;
    }
}