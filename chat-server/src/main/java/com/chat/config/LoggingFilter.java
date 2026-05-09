package com.chat.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.util.ContentCachingResponseWrapper;

import javax.servlet.*;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Component
@Order(1)
public class LoggingFilter implements Filter {
    
    private static final Logger logger = LoggerFactory.getLogger(LoggingFilter.class);
    
    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) 
            throws IOException, ServletException {
        
        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;
        
        // 强制设置 UTF-8 编码
        httpResponse.setCharacterEncoding("UTF-8");
        
        long startTime = System.currentTimeMillis();
        
        String method = httpRequest.getMethod();
        String uri = httpRequest.getRequestURI();
        String queryString = httpRequest.getQueryString();
        String remoteAddr = httpRequest.getRemoteAddr();
        String origin = httpRequest.getHeader("Origin");
        
        if (origin != null && !origin.isEmpty()) {
            logger.info("==> {} {} {} from {} [Origin: {}]", method, uri, 
                queryString != null ? "?" + queryString : "", remoteAddr, origin);
        } else {
            logger.info("==> {} {} {} from {}", method, uri, 
                queryString != null ? "?" + queryString : "", remoteAddr);
        }
        
        ContentCachingResponseWrapper responseWrapper = new ContentCachingResponseWrapper(httpResponse);
        
        chain.doFilter(httpRequest, responseWrapper);
        
        long duration = System.currentTimeMillis() - startTime;
        int status = httpResponse.getStatus();
        
        String responseBody = new String(responseWrapper.getContentAsByteArray(), StandardCharsets.UTF_8);
        
        if (status >= 400) {
            logger.warn("<== {} {} - {} ({}ms) [Response: {}]", method, uri, status, duration, responseBody);
        } else {
            logger.info("<== {} {} - {} ({}ms)", method, uri, status, duration);
        }
        
        responseWrapper.copyBodyToResponse();
    }
}