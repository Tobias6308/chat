package com.chat.service;

import com.chat.document.Message;
import com.chat.dto.ServerPayload;
import com.chat.netty.SessionManager;
import com.chat.repository.ConversationRepository;
import com.chat.repository.MessageRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class MessageService {
    
    private static final Logger logger = LoggerFactory.getLogger(MessageService.class);
    
    /**
     * 每页默认消息数
     */
    private static final int DEFAULT_PAGE_SIZE = 50;
    
    @Autowired
    private MessageRepository messageRepository;
    
    @Autowired
    private ConversationRepository conversationRepository;
    
    @Autowired
    private SessionManager sessionManager;
    
    private final ObjectMapper objectMapper = new ObjectMapper();
    
    /**
     * 保存消息
     * 
     * @param message 消息实体
     * @return 保存后的消息
     */
    public Message save(Message message) {
        // 设置创建时间
        if (message.getCreatedAt() == null) {
            message.setCreatedAt(System.currentTimeMillis());
        }
        
        // 设置默认状态
        if (message.getStatus() == null) {
            message.setStatus("sent");
        }
        
        // 保存到 MongoDB
        Message saved = messageRepository.save(message);
        
        // 更新会话的最后消息
        updateConversationLastMessage(saved);
        
        return saved;
    }
    
    /**
     * 根据 ID 获取消息
     * 
     * @param messageId 消息 ID
     * @return 消息实体
     */
    public Optional<Message> getById(String messageId) {
        return messageRepository.findById(messageId);
    }
    
    /**
     * 获取会话历史消息 (分页)
     * 
     * @param conversationId 会话 ID
     * @param cursor        游标时间
     * @param limit         数量限制
     * @return 消息列表
     */
    public List<Message> getHistory(String conversationId, Long cursor, int limit) {
        List<Message> messages;
        
        if (cursor != null) {
            // 游标分页
            messages = messageRepository.findByConversationIdWithCursor(
                conversationId, 
                cursor, 
                limit
            );
        } else {
            // 首页 (按时间倒序)
            org.springframework.data.domain.Page<Message> page = messageRepository.findByConversationIdOrderByCreatedAtDesc(
                conversationId,
                PageRequest.of(0, limit, Sort.by(Sort.Direction.DESC, "createdAt"))
            );
            messages = page.getContent();
        }
        
        // 反转列表 (按时间正序返回)
        Collections.reverse(messages);
        
        return messages;
    }
    
    /**
     * 获取会话历史消息 (游标分页)
     * 
     * @param conversationId 会话 ID
     * @param cursor         游标 (消息创建时间)
     * @param limit          数量限制
     * @return 消息列表和游标
     */
    public Map<String, Object> getHistoryWithCursor(String conversationId, Long cursor, int limit) {
        List<Message> messages;
        
        if (cursor != null && cursor > 0) {
            // 游标分页: 查询比 cursor 早的消息
            messages = messageRepository.findByConversationIdWithCursor(
                conversationId,
                cursor,
                limit + 1 // 多查一条判断是否有更多
            );
        } else {
            // 首页
            messages = messageRepository.findByConversationIdWithCursor(
                conversationId,
                Long.MAX_VALUE,
                limit + 1
            );
        }
        
        // 判断是否有更多
        boolean hasMore = messages.size() > limit;
        if (hasMore) {
            messages = messages.subList(0, limit);
        }
        
        // 获取下一条消息的时间作为游标
        String nextCursor = null;
        if (hasMore && !messages.isEmpty()) {
            nextCursor = String.valueOf(messages.get(messages.size() - 1).getCreatedAt());
        }
        
        Map<String, Object> result = new HashMap<>();
        result.put("messages", messages);
        result.put("cursor", nextCursor);
        result.put("hasMore", hasMore);
        
        return result;
    }
    
    /**
     * 更新消息状态
     * 
     * @param messageId 消息 ID
     * @param status    状态
     */
    public void updateStatus(String messageId, String status) {
        messageRepository.findById(messageId).ifPresent(message -> {
            message.setStatus(status);
            message.setUpdatedAt(System.currentTimeMillis());
            messageRepository.save(message);
        });
    }
    
    /**
     * 发送消息给会话成员
     * 
     * @param conversationId 会话 ID
     * @param excludeUserId  排除的用户 ID (发送者)
     * @param payload       消息载荷
     */
    public void broadcastToConversation(String conversationId, String excludeUserId, ServerPayload payload) {
        // 获取会话成员
        conversationRepository.findById(conversationId).ifPresent(conversation -> {
            for (String userId : conversation.getParticipants()) {
                // 排除发送者
                if (!userId.equals(excludeUserId)) {
                    sessionManager.sendToUser(userId, payload);
                }
            }
        });
    }
    
    /**
     * 更新会话的最后消息
     *
     * @param message 消息
     */
    private void updateConversationLastMessage(Message message) {
        conversationRepository.findById(message.getConversationId()).ifPresent(conversation -> {
            conversation.setUpdatedAt(System.currentTimeMillis());

            // 如果发送者不是当前用户，未读数 +1
            // TODO: 实现未读数逻辑

            conversationRepository.save(conversation);
        });
    }
    
    /**
     * 删除会话所有消息
     * 
     * @param conversationId 会话 ID
     */
    public void deleteByConversation(String conversationId) {
        messageRepository.deleteByConversationId(conversationId);
    }
}