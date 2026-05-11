package com.chat.netty;

import com.chat.document.Message;
import com.chat.dto.ClientPayload;
import com.chat.dto.ServerPayload;
import com.chat.service.ServiceSessionService;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 客服 WebSocket 消息处理器
 * 处理用户端的客服相关 WebSocket 消息
 * 
 * 支持的操作:
 * - send_message: 发送消息
 * - typing: 发送输入状态
 * - read: 标记消息已读
 * - end_session: 结束会话
 * - get_messages: 获取历史消息
 * - rate_session: 评价会话
 */
@Component
public class ServiceMessageHandler {

    private static final Logger logger = LoggerFactory.getLogger(ServiceMessageHandler.class);

    @Autowired
    private ServiceSessionService serviceSessionService;

    @Autowired
    private SessionManager sessionManager;

    /**
     * 处理客服 WebSocket 消息
     * @param ctx 通道上下文
     * @param msg 客户端消息
     */
    public void handleServiceMessage(ChannelHandlerContext ctx, ClientPayload msg) {
        String userId = sessionManager.getUserId(ctx.channel());
        if (userId == null) {
            logger.warn("[ServiceWS] 未认证的用户消息");
            return;
        }

        Map<String, Object> data = (Map<String, Object>) msg.getPayload();
        String action = data != null ? (String) data.get("action") : null;

        logger.info("[ServiceWS] userId={}, action={}", userId, action);

        switch (action != null ? action : "") {
            case "join_queue":
                logger.debug("[ServiceWS] join_queue userId={}", userId);
                handleJoinQueue(userId, data);
                break;
            case "leave_queue":
                logger.debug("[ServiceWS] leave_queue userId={}", userId);
                handleLeaveQueue(userId);
                break;
            case "get_status":
                logger.debug("[ServiceWS] get_status userId={}", userId);
                handleGetStatus(ctx, userId);
                break;
            case "get_messages":
                logger.debug("[ServiceWS] get_messages userId={}", userId);
                handleGetMessages(ctx, userId);
                break;
            case "send_message":
                logger.debug("[ServiceWS] send_message userId={}", userId);
                handleSendMessage(userId, data);
                break;
            case "admin_send":
                String sessionId = data != null ? (String) data.get("sessionId") : null;
                logger.debug("[ServiceWS] admin_send userId={}, sessionId={}", userId, sessionId);
                handleAdminSendMessage(userId, data);
                break;
            case "end_session":
                logger.debug("[ServiceWS] end_session userId={}", userId);
                handleEndSession(userId);
                break;
            case "mark_read":
                logger.debug("[ServiceWS] mark_read userId={}", userId);
                handleMarkRead(userId);
                break;
            case "typing":
                logger.debug("[ServiceWS] typing userId={}", userId);
                handleTyping(userId);
                break;
            default:
                logger.warn("[ServiceWS] 未知action: {}, userId={}", action, userId);
        }
    }

    private void handleJoinQueue(String userId, Map<String, Object> data) {
        String userName = data != null ? (String) data.get("userName") : null;
        String userAvatar = data != null ? (String) data.get("userAvatar") : null;

        Map<String, Object> result = serviceSessionService.joinQueue(userId, userName, userAvatar);
        broadcastToUser(userId, "service_queue_join", result);
    }

    private void handleLeaveQueue(String userId) {
        boolean success = serviceSessionService.leaveQueue(userId);
        Map<String, Object> m = new HashMap<>();
        m.put("success", success);
        broadcastToUser(userId, "service_queue_leave", m);
    }

    private void handleGetStatus(ChannelHandlerContext ctx, String userId) {
        Map<String, Object> status = serviceSessionService.getServiceStatus();
        Map<String, Object> queueInfo = serviceSessionService.getQueueInfo(userId);
        
        Map<String, Object> m = new HashMap<>();
        m.put("serviceStatus", status);
        m.put("queueStatus", queueInfo);
        broadcastToUser(userId, "service_status", m);
    }

    private void handleGetMessages(ChannelHandlerContext ctx, String userId) {
        Map<String, Object> queueInfo = serviceSessionService.getQueueInfo(userId);
        if ("chatting".equals(queueInfo.get("status"))) {
            String sessionId = (String) queueInfo.get("sessionId");
            List<Map<String, Object>> messages = serviceSessionService.getSessionMessages(sessionId, 50, 0);
            Map<String, Object> m = new HashMap<>();
            m.put("messages", messages);
            broadcastToUser(userId, "service_messages", m);
        }
    }

    private void handleSendMessage(String userId, Map<String, Object> data) {
        if (data == null) return;

        String content = (String) data.get("content");
        String contentType = (String) data.get("contentType");
        if (contentType == null) contentType = "text";

        Map<String, Object> queueInfo = serviceSessionService.getQueueInfo(userId);
        if (!"chatting".equals(queueInfo.get("status"))) {
            Map<String, Object> m = new HashMap<>();
            m.put("message", "未在会话中");
            broadcastToUser(userId, "service_error", m);
            return;
        }

        String sessionId = (String) queueInfo.get("sessionId");
        String adminId = (String) queueInfo.get("serviceId");
        Message message = serviceSessionService.sendMessage(sessionId, userId, content, contentType);

        // 转换为包含完整信息的 Map
        Map<String, Object> messageMap = serviceSessionService.convertMessageToMapWithSession(sessionId, message);

        Map<String, Object> m = new HashMap<>();
        m.put("message", messageMap);
        broadcastToUser(userId, "service_message_sent", m);
        if (adminId != null) {
            Map<String, Object> pushData = new HashMap<>();
            pushData.put("sessionId", sessionId);
            pushData.put("message", messageMap);
            broadcastToUser(adminId, "service_new_message", pushData);
        }
    }

    private void handleEndSession(String userId) {
        Map<String, Object> queueInfo = serviceSessionService.getQueueInfo(userId);
        if (!"chatting".equals(queueInfo.get("status"))) {
            Map<String, Object> m = new HashMap<>();
            m.put("message", "未在会话中");
            broadcastToUser(userId, "service_error", m);
            return;
        }

        String sessionId = (String) queueInfo.get("sessionId");
        boolean success = serviceSessionService.endSession(sessionId, null);
        Map<String, Object> m = new HashMap<>();
        m.put("success", success);
        broadcastToUser(userId, "service_session_ended", m);
    }

    private void handleMarkRead(String userId) {
        serviceSessionService.markUserRead(userId);
    }

    private void handleTyping(String userId) {
        Map<String, Object> queueInfo = serviceSessionService.getQueueInfo(userId);
        if ("chatting".equals(queueInfo.get("status"))) {
            String serviceId = (String) queueInfo.get("serviceId");
            if (serviceId != null) {
                Channel channel = sessionManager.getChannelByUserId(serviceId);
                if (channel != null && channel.isActive()) {
                    ServerPayload payload = new ServerPayload();
                    payload.setType("service_typing");
                    Map<String, Object> m = new HashMap<>();
                    m.put("userId", userId);
                    m.put("userName", queueInfo.get("userName"));
                    payload.setPayload(m);
                    channel.writeAndFlush(payload);
                }
            }
        }
    }

    /**
     * 客服通过WebSocket发送消息给用户
     * action: admin_send
     * data: { sessionId, content, contentType }
     */
    private void handleAdminSendMessage(String adminId, Map<String, Object> data) {
        logger.info("[ServiceWS] handleAdminSendMessage adminId={}, sessionId={}", adminId, data != null ? data.get("sessionId") : null);
        if (data == null) return;

        String sessionId = (String) data.get("sessionId");
        String content = (String) data.get("content");
        String contentType = (String) data.get("contentType");
        String fileUrl = (String) data.get("fileUrl");
        String fileName = (String) data.get("fileName");

        if (sessionId == null || content == null) {
            Map<String, Object> error = new HashMap<>();
            error.put("message", "参数不完整");
            broadcastToUser(adminId, "service_error", error);
            return;
        }

        if (contentType == null) contentType = "text";

        // 发送消息（adminId 作为 senderId）
        Message message = serviceSessionService.sendMessage(sessionId, adminId, content, contentType, fileUrl, fileName);

        // 转换为包含完整信息的 Map
        Map<String, Object> messageMap = serviceSessionService.convertMessageToMapWithSession(sessionId, message);

        Map<String, Object> result = new HashMap<>();
        result.put("message", messageMap);
        broadcastToUser(adminId, "service_message_sent", result);
        logger.info("[ServiceWS] 客服发送消息完成 sessionId={}, adminId={}, messageId={}", sessionId, adminId, message.getId());
    }

    private void broadcastToUser(String userId, String type, Object data) {
        Channel channel = sessionManager.getChannelByUserId(userId);
        if (channel == null) {
            logger.warn("[ServiceWS] 推送失败，用户channel不存在 userId={}, type={}", userId, type);
            return;
        }
        if (!channel.isActive()) {
            logger.warn("[ServiceWS] 推送失败，用户channel未激活 userId={}, type={}, active={}",
                userId, type, channel.isActive());
            return;
        }

        // 使用 ServerPayload (出站消息)
        ServerPayload payload = new ServerPayload();
        payload.setType(type);
        payload.setPayload(data);
        payload.setTimestamp(System.currentTimeMillis());
        channel.writeAndFlush(payload);

        // 调试：打印发送的 JSON
        try {
            String json = new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(payload);
            logger.info("[ServiceWS] 推送消息 JSON userId={}, type={}, json={}", userId, type, json.substring(0, Math.min(200, json.length())));
        } catch (Exception e) {
            logger.warn("[ServiceWS] 序列化失败 userId={}, type={}", userId, type);
        }

        logger.info("[ServiceWS] 推送成功 userId={}, type={}, channelId={}",
            userId, type, channel.id().asLongText());
    }
}