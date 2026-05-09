package com.chat.netty;

import com.chat.common.ErrorCode;
import com.chat.document.Conversation;
import com.chat.document.Message;
import com.chat.document.User;
import com.chat.dto.*;
import com.chat.repository.ConversationRepository;
import com.chat.repository.UserRepository;
import com.chat.service.ConversationService;
import com.chat.service.MessageService;
import com.chat.service.RedisUnreadService;
import io.netty.channel.ChannelHandlerContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Component
public class MessageHandler {
    
    private static final Logger logger = LoggerFactory.getLogger(MessageHandler.class);
    
    @Autowired
    private SessionManager sessionManager;
    
    @Autowired
    private MessageService messageService;
    
    @Autowired
    private ConversationService conversationService;
    
    @Autowired
    private ConversationRepository conversationRepository;
    
    @Autowired
    private UserRepository userRepository;
    
    @Autowired
    private RedisUnreadService redisUnreadService;
    
    /**
     * Ensure conversation exists
     */
    private Conversation ensureConversation(String conversationId, String senderId) {
        return conversationService.getOrCreateById(conversationId, senderId);
    }

    /**
     * 处理发送消息
     * 
     * @param ctx Channel 上下文
     * @param payload 客户端消息载荷
     */
    public void handleSend(ChannelHandlerContext ctx, ClientPayload payload) {
        try {
            // 获取发送者 ID
            String senderId = sessionManager.getUserId(ctx.channel());
            if (senderId == null) {
                sendError(ctx, ErrorCode.NOT_AUTHENTICATED);
                return;
            }
            
            // 解析消息内容
            Map<String, Object> sendData = (Map<String, Object>) payload.getPayload();
            String conversationId = (String) sendData.get("conversationId");
            String content = (String) sendData.get("content");
            String contentType = (String) sendData.getOrDefault("contentType", "text");
            
            // 确保会话存在 (如果不存在则创建)
            conversationService.getOrCreateById(conversationId, senderId);
            
            // 生成消息 ID (使用 nanoid 格式)
            String messageId = generateId();
            
            // 获取会话类型
            Optional<Conversation> convOpt = conversationRepository.findById(conversationId);
            String conversationType = convOpt.map(Conversation::getType).orElse("private");
            
            // 构建消息并保存到 MongoDB
            Message message = Message.builder()
                .id(messageId)
                .conversationId(conversationId)
                .conversationType(conversationType)
                .senderId(senderId)
                .content(content)
                .contentType(contentType)
                .status("sent")
                .createdAt(System.currentTimeMillis())
                .build();
            
            // 保存消息到 MongoDB
            messageService.save(message);
            
            // 更新Redis会话最新消息时间
            redisUnreadService.setConversationLastMessageTime(conversationId, message.getCreatedAt());
            
            // 发送 ACK 确认给发送者
            ServerPayload ackResponse = new ServerPayload();
            ackResponse.setType("ack_ok");
            ackResponse.setId(payload.getId());
            
            Map<String, Object> ackPayload = new HashMap<>();
            ackPayload.put("messageId", messageId);
            ackPayload.put("status", "sent");
            ackPayload.put("timestamp", System.currentTimeMillis());
            
            ackResponse.setPayload(ackPayload);
            ackResponse.setTimestamp(System.currentTimeMillis());
            
            ctx.writeAndFlush(ackResponse);
            
            // 获取发送者信息
            Optional<User> senderOpt = userRepository.findById(senderId);
            String senderNickname = "用户";
            String senderAvatar = null;
            if (senderOpt.isPresent()) {
                senderNickname = senderOpt.get().getNickname();
                senderAvatar = senderOpt.get().getAvatar();
            }
            
            // 广播消息给会话中的其他用户
            Map<String, Object> messageData = new HashMap<>();
            messageData.put("id", message.getId());
            messageData.put("conversationId", message.getConversationId());
            messageData.put("conversationType", conversationType);
            messageData.put("senderId", message.getSenderId());
            messageData.put("senderNickname", senderNickname);
            messageData.put("senderAvatar", senderAvatar);
            messageData.put("content", message.getContent());
            messageData.put("contentType", message.getContentType());
            messageData.put("status", message.getStatus());
            messageData.put("createdAt", message.getCreatedAt());
            
            broadcastToConversation(conversationId, senderId, messageData);
            
            logger.info("Message sent: conversationId={}, messageId={}", conversationId, messageId);
            
        } catch (Exception e) {
            logger.error("Failed to handle send message: {}", e.getMessage(), e);
            sendError(ctx, ErrorCode.SEND_FAILED);
        }
    }
    
    /**
     * 处理消息确认
     * 
     * @param ctx Channel 上下文
     * @param payload 客户端消息载荷
     */
    public void handleAck(ChannelHandlerContext ctx, ClientPayload payload) {
        try {
            Map<String, Object> ackData = (Map<String, Object>) payload.getPayload();
            String messageId = (String) ackData.get("messageId");
            String status = (String) ackData.get("status");
            
            logger.debug("ACK received: messageId={}, status={}", messageId, status);
            
            // TODO: 更新消息状态
            
        } catch (Exception e) {
            logger.error("Failed to handle ack: {}", e.getMessage(), e);
        }
    }
    
    /**
     * 处理获取历史消息
     * 
     * @param ctx Channel 上下文
     * @param payload 客户端消息载荷
     */
    public void handleHistory(ChannelHandlerContext ctx, ClientPayload payload) {
        try {
            String userId = sessionManager.getUserId(ctx.channel());
            if (userId == null) {
                sendError(ctx, ErrorCode.NOT_AUTHENTICATED);
                return;
            }
            
            Map<String, Object> historyData = (Map<String, Object>) payload.getPayload();
            String conversationId = (String) historyData.get("conversationId");
            Long cursor = historyData.get("cursor") != null ? ((Number) historyData.get("cursor")).longValue() : null;
            Integer limit = historyData.get("limit") != null ? (Integer) historyData.get("limit") : 50;
            
            // 从 MongoDB 获取历史消息
            List<Message> messages = messageService.getHistory(conversationId, cursor, limit + 1);
            boolean hasMore = messages.size() > limit;
            if (hasMore) {
                messages = messages.subList(0, limit);
            }
            
            // 转换为 Map 列表
            List<Map<String, Object>> messageList = new ArrayList<>();
            for (Message msg : messages) {
                Map<String, Object> msgMap = new HashMap<>();
                msgMap.put("id", msg.getId());
                msgMap.put("conversationId", msg.getConversationId());
                msgMap.put("senderId", msg.getSenderId());
                msgMap.put("content", msg.getContent());
                msgMap.put("contentType", msg.getContentType());
                msgMap.put("status", msg.getStatus());
                msgMap.put("createdAt", msg.getCreatedAt());
                messageList.add(msgMap);
            }
            
            // 构建响应
            ServerPayload response = new ServerPayload();
            response.setType("history");
            response.setId(payload.getId());
            
            Map<String, Object> responsePayload = new HashMap<>();
            responsePayload.put("messages", messageList);
            responsePayload.put("cursor", cursor);
            responsePayload.put("hasMore", hasMore);
            
            response.setPayload(responsePayload);
            response.setTimestamp(System.currentTimeMillis());
            
            ctx.writeAndFlush(response);
            
            logger.debug("History fetched: conversationId={}, count={}", conversationId, messageList.size());
            
        } catch (Exception e) {
            logger.error("Failed to handle history: {}", e.getMessage(), e);
            sendError(ctx, ErrorCode.HISTORY_FAILED);
        }
    }
    
    /**
     * 广播消息给会话中的其他用户
     * 
     * @param conversationId 会话 ID
     * @param senderId 发送者 ID
     * @param messageData 消息数据
     */
    private void broadcastToConversation(String conversationId, String senderId, Map<String, Object> messageData) {
        try {
            // 从 MongoDB 获取会话信息
            Optional<Conversation> convOpt = conversationRepository.findById(conversationId);
            if (!convOpt.isPresent()) {
                logger.warn("Conversation not found: {}", conversationId);
                return;
            }
            
            Conversation conversation = convOpt.get();
            List<String> members = conversation.getParticipants();
            
            // 构建消息
            ServerPayload message = new ServerPayload();
            message.setType("message");
            message.setPayload(messageData);
            message.setTimestamp(System.currentTimeMillis());
            
            // 遍历会话成员，推送消息
            for (String userId : members) {
                // 跳过发送者
                if (userId.equals(senderId)) {
                    continue;
                }
                
                // 检查用户是否在线
                if (sessionManager.isOnline(userId)) {
                    sessionManager.sendToUser(userId, message);
                    logger.debug("Message sent to user: {}", userId);
                }
            }
            
            logger.debug("Broadcast message to conversation: {}, members: {}", conversationId, members.size());
        } catch (Exception e) {
            logger.error("Failed to broadcast message to conversation: {}", conversationId, e);
        }
    }
    
    /**
     * 发送错误响应
     */
    private void sendError(ChannelHandlerContext ctx, ErrorCode errorCode) {
        ServerPayload error = new ServerPayload();
        error.setType("error");
        
        Map<String, String> errorPayload = new HashMap<>();
        errorPayload.put("code", errorCode.getCode());
        errorPayload.put("message", errorCode.getMessage());
        
        error.setPayload(errorPayload);
        error.setTimestamp(System.currentTimeMillis());
        
        ctx.writeAndFlush(error);
    }
    
    /**
     * 生成消息 ID (nanoid 格式)
     * 
     * @return 随机 ID
     */
    private String generateId() {
        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 21; i++) {
            int index = (int) (Math.random() * chars.length());
            sb.append(chars.charAt(index));
        }
        return sb.toString();
    }
}