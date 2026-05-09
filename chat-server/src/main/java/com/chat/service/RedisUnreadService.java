package com.chat.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Service
public class RedisUnreadService {
    
    private static final Logger logger = LoggerFactory.getLogger(RedisUnreadService.class);
    
    private static final String CONV_LAST_MSG_KEY = "chat:conv:last_msg:";
    private static final String USER_READ_KEY = "chat:user:read:";
    private static final long DEFAULT_EXPIRE_DAYS = 7;
    
    @Autowired
    private RedisTemplate<String, Object> redisTemplate;
    
    /**
     * 设置会话的最新消息时间 (发送消息时调用)
     */
    public void setConversationLastMessageTime(String conversationId, long timestamp) {
        String key = CONV_LAST_MSG_KEY + conversationId;
        redisTemplate.opsForValue().set(key, timestamp, DEFAULT_EXPIRE_DAYS, TimeUnit.DAYS);
        logger.debug("Set conversation last message time: {} = {}", key, timestamp);
    }
    
    /**
     * 获取会话的最新消息时间
     */
    public Long getConversationLastMessageTime(String conversationId) {
        String key = CONV_LAST_MSG_KEY + conversationId;
        Object value = redisTemplate.opsForValue().get(key);
        return value != null ? Long.parseLong(value.toString()) : null;
    }
    
    /**
     * 设置用户已读时间 (用户查看会话时调用)
     */
    public void setUserReadTime(String userId, String conversationId, long timestamp) {
        String key = USER_READ_KEY + userId + ":" + conversationId;
        redisTemplate.opsForValue().set(key, timestamp, DEFAULT_EXPIRE_DAYS, TimeUnit.DAYS);
        logger.debug("Set user read time: {} = {}", key, timestamp);
    }
    
    /**
     * 获取用户已读时间
     */
    public Long getUserReadTime(String userId, String conversationId) {
        String key = USER_READ_KEY + userId + ":" + conversationId;
        Object value = redisTemplate.opsForValue().get(key);
        return value != null ? Long.parseLong(value.toString()) : null;
    }
    
    /**
     * 判断用户是否有新消息
     * hasNew = 最新消息时间 > 用户已读时间
     */
    public boolean hasNewMessages(String userId, String conversationId) {
        Long lastMsgTime = getConversationLastMessageTime(conversationId);
        Long readTime = getUserReadTime(userId, conversationId);
        
        if (lastMsgTime == null || lastMsgTime == 0) {
            return false;
        }
        
        if (readTime == null || readTime == 0) {
            return true;
        }
        
        return lastMsgTime > readTime;
    }
    
    /**
     * 批量检查多个会话是否有新消息
     */
    public Map<String, Boolean> batchHasNewMessages(String userId, List<String> conversationIds) {
        Map<String, Boolean> result = new HashMap<>();
        
        if (conversationIds == null || conversationIds.isEmpty()) {
            return result;
        }
        
        String userReadKey = USER_READ_KEY + userId + ":";
        
        Map<String, Object> userReadMap = new HashMap<>();
        for (String convId : conversationIds) {
            Object val = redisTemplate.opsForValue().get(userReadKey + convId);
            if (val != null) {
                userReadMap.put(convId, Long.parseLong(val.toString()));
            }
        }
        
        Map<String, Object> convMsgMap = new HashMap<>();
        for (String convId : conversationIds) {
            Object val = redisTemplate.opsForValue().get(CONV_LAST_MSG_KEY + convId);
            if (val != null) {
                convMsgMap.put(convId, Long.parseLong(val.toString()));
            }
        }
        
        for (String convId : conversationIds) {
            Long lastMsgTime = (Long) convMsgMap.get(convId);
            Long readTime = (Long) userReadMap.get(convId);
            
            if (lastMsgTime == null || lastMsgTime == 0) {
                result.put(convId, false);
            } else if (readTime == null || readTime == 0) {
                result.put(convId, true);
            } else {
                result.put(convId, lastMsgTime > readTime);
            }
        }
        
        return result;
    }
    
    /**
     * 删除会话的所有标记 (会话删除时)
     */
    public void deleteConversation(String conversationId) {
        String convKey = CONV_LAST_MSG_KEY + conversationId;
        redisTemplate.delete(convKey);
        logger.info("Deleted conversation unread data: {}", conversationId);
    }
}