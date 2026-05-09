package com.chat.netty;

import com.chat.common.ErrorCode;
import com.chat.dto.AuthPayload;
import com.chat.dto.ClientPayload;
import com.chat.dto.ServerPayload;
import com.chat.security.JwtUtil;
import io.netty.channel.ChannelHandlerContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Component
public class AuthHandler {
    
    private static final Logger logger = LoggerFactory.getLogger(AuthHandler.class);
    
    @Autowired
    private SessionManager sessionManager;
    
    @Autowired
    private JwtUtil jwtUtil;
    
    /**
     * 处理认证消息
     * 
     * @param ctx Channel 上下文
     * @param payload 客户端消息载荷
     */
    public void handle(ChannelHandlerContext ctx, ClientPayload payload) {
        try {
            // 获取认证信息
            AuthPayload authPayload = convertPayload(payload.getPayload(), AuthPayload.class);
            
            if (authPayload == null || authPayload.getToken() == null) {
                sendError(ctx, ErrorCode.INVALID_AUTH);
                return;
            }
            
            // 验证 JWT token
            String userId = jwtUtil.parseToken(authPayload.getToken());
            
            if (userId == null) {
                sendError(ctx, ErrorCode.INVALID_TOKEN);
                return;
            }
            
            // 注册会话
            sessionManager.register(userId, ctx.channel());
            
            // 发送认证成功响应
            ServerPayload response = new ServerPayload();
            response.setType("auth_ok");
            response.setId(payload.getId());
            
            Map<String, Object> responsePayload = new HashMap<>();
            responsePayload.put("userId", userId);
            responsePayload.put("serverTime", System.currentTimeMillis());
            
            response.setPayload(responsePayload);
            response.setTimestamp(System.currentTimeMillis());
            
            ctx.writeAndFlush(response);
            
            logger.info("User authenticated successfully: {}", userId);
            
        } catch (Exception e) {
            logger.error("Auth error: {}", e.getMessage(), e);
            sendError(ctx, ErrorCode.AUTH_FAILED, e.getMessage());
        }
    }
    
    /**
     * 发送错误响应
     * 
     * @param ctx Channel 上下文
     * @param errorCode 错误码枚举
     */
    private void sendError(ChannelHandlerContext ctx, ErrorCode errorCode) {
        sendError(ctx, errorCode, null);
    }
    
    /**
     * 发送错误响应
     * 
     * @param ctx Channel 上下文
     * @param errorCode 错误码枚举
     * @param extraMessage 额外错误消息
     */
    private void sendError(ChannelHandlerContext ctx, ErrorCode errorCode, String extraMessage) {
        ServerPayload error = new ServerPayload();
        error.setType("error");
        
        Map<String, String> errorPayload = new HashMap<>();
        errorPayload.put("code", errorCode.getCode());
        String message = extraMessage != null ? errorCode.getMessage() + ": " + extraMessage : errorCode.getMessage();
        errorPayload.put("message", message);
        
        error.setPayload(errorPayload);
        error.setTimestamp(System.currentTimeMillis());
        
        ctx.writeAndFlush(error);
    }
    
    /**
     * 将 payload 转换为指定类型
     * 
     * @param payload 原始 payload
     * @param clazz 目标类型
     * @param <T> 泛型
     * @return 转换后的对象
     */
    private <T> T convertPayload(Object payload, Class<T> clazz) {
        if (payload == null) {
            return null;
        }
        
        if (payload instanceof Map) {
            // 使用 Jackson 或 Gson 进行转换
            try {
                com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
                return mapper.convertValue(payload, clazz);
            } catch (Exception e) {
                logger.error("Failed to convert payload", e);
                return null;
            }
        }
        
        return clazz.cast(payload);
    }
}