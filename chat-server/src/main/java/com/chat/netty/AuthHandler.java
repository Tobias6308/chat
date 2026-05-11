package com.chat.netty;

import com.chat.common.ErrorCode;
import com.chat.dto.AuthPayload;
import com.chat.dto.ClientPayload;
import com.chat.dto.ServerPayload;
import com.chat.security.JwtUtil;
import com.chat.security.AdminJwtUtil;
import com.chat.service.ServiceSessionService;
import io.netty.channel.ChannelHandlerContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class AuthHandler {

    private static final Logger logger = LoggerFactory.getLogger(AuthHandler.class);

    @Autowired
    private SessionManager sessionManager;

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private AdminJwtUtil adminJwtUtil;

    @Autowired
    private ServiceSessionService serviceSessionService;
    
    /**
     * 处理认证消息
     * 
     * @param ctx Channel 上下文
     * @param payload 客户端消息载荷
     */
    public void handle(ChannelHandlerContext ctx, ClientPayload payload) {
        logger.info("[Auth] 收到认证消息 type={}", payload.getType());
        try {
            // 获取认证信息
            AuthPayload authPayload = convertPayload(payload.getPayload(), AuthPayload.class);
            
            if (authPayload == null || authPayload.getToken() == null) {
                logger.warn("[Auth] 认证信息为空");
                sendError(ctx, ErrorCode.INVALID_AUTH);
                return;
            }

            logger.debug("[Auth] token={}", authPayload.getToken().substring(0, 20) + "...");

            // 尝试解析用户 JWT
            String userId = jwtUtil.parseToken(authPayload.getToken());
            String userType = "user";

            // 如果不是用户 JWT，尝试解析 admin JWT
            if (userId == null) {
                logger.debug("[Auth] 尝试解析admin JWT");
                userId = adminJwtUtil.parseToken(authPayload.getToken());
                userType = "admin";
            }

            if (userId == null) {
                logger.warn("[Auth] token解析失败");
                sendError(ctx, ErrorCode.INVALID_TOKEN);
                return;
            }

            logger.info("[Auth] 认证成功 userId={}, userType={}", userId, userType);

            // 注册会话
            sessionManager.register(userId, ctx.channel());

            // 发送认证成功响应
            ServerPayload response = new ServerPayload();
            response.setType("auth_ok");
            response.setId(payload.getId());

            Map<String, Object> responsePayload = new HashMap<>();
            responsePayload.put("userId", userId);
            responsePayload.put("userType", userType);
            responsePayload.put("serverTime", System.currentTimeMillis());

            response.setPayload(responsePayload);
            response.setTimestamp(System.currentTimeMillis());

            ctx.writeAndFlush(response);

            logger.info("User authenticated successfully: userId={}, userType={}", userId, userType);

            // 如果是客服，发送离线消息
            if ("admin".equals(userType)) {
                sendOfflineMessages(ctx, userId);
            }

        } catch (Exception e) {
            logger.error("Auth error: {}", e.getMessage(), e);
            sendError(ctx, ErrorCode.AUTH_FAILED, e.getMessage());
        }
    }

    /**
     * 发送离线消息给客服
     */
    private void sendOfflineMessages(ChannelHandlerContext ctx, String serviceId) {
        try {
            List<Map<String, Object>> offlineMessages = serviceSessionService.getOfflineMessages(serviceId);
            if (offlineMessages.isEmpty()) {
                logger.info("[Auth] 客服无离线消息 serviceId={}", serviceId);
                return;
            }

            logger.info("[Auth] 发送离线消息 serviceId={}, count={}", serviceId, offlineMessages.size());
            for (Map<String, Object> offlineMsg : offlineMessages) {
                @SuppressWarnings("unchecked")
                Map<String, Object> message = (Map<String, Object>) offlineMsg.get("message");
                String sessionId = (String) offlineMsg.get("sessionId");

                ServerPayload payload = new ServerPayload();
                payload.setType("service_new_message");
                Map<String, Object> pushData = new HashMap<>();
                pushData.put("sessionId", sessionId);
                pushData.put("message", message);
                payload.setPayload(pushData);
                payload.setTimestamp(System.currentTimeMillis());

                ctx.writeAndFlush(payload);
            }

            // 发送离线消息完成通知
            ServerPayload donePayload = new ServerPayload();
            donePayload.setType("service_offline_messages_done");
            Map<String, Object> doneData = new HashMap<>();
            doneData.put("count", offlineMessages.size());
            donePayload.setPayload(doneData);
            donePayload.setTimestamp(System.currentTimeMillis());
            ctx.writeAndFlush(donePayload);

        } catch (Exception e) {
            logger.error("[Auth] 发送离线消息失败 serviceId={}, error={}", serviceId, e.getMessage());
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