package com.chat.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.ModelAndView;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

@Component
public class ApiLoggingInterceptor implements HandlerInterceptor {

    private static final Logger apiLogger = LoggerFactory.getLogger("apiLog");
    private static final Logger slowApiLogger = LoggerFactory.getLogger("slowApiLog");
    private static final long SLOW_THRESHOLD_MS = 300;

    private static final ThreadLocal<Long> startTime = new ThreadLocal<>();

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        startTime.set(System.currentTimeMillis());
        return true;
    }

    @Override
    public void postHandle(HttpServletRequest request, HttpServletResponse response, Object handler, ModelAndView modelAndView) throws Exception {
        Long start = startTime.get();
        if (start == null) {
            return;
        }

        long duration = System.currentTimeMillis() - start;
        String method = request.getMethod();
        String uri = request.getRequestURI();
        String queryString = request.getQueryString();
        String fullUri = queryString != null ? uri + "?" + queryString : uri;
        int status = response.getStatus();

        // 写入所有 API 日志
        String logMessage = String.format("%s | %s | %dms | %s %s",
                method, status, duration, fullUri, getClientIp(request));

        apiLogger.info(logMessage);

        // 写入慢 API 日志
        if (duration > SLOW_THRESHOLD_MS) {
            String slowLogMessage = String.format("SLOW API | %s | %dms | %s %s | status: %d",
                    method, duration, fullUri, getClientIp(request), status);
            slowApiLogger.warn(slowLogMessage);
        }

        startTime.remove();
    }

    private String getClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("X-Real-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        return ip;
    }
}