package com.chat.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class RedisPinService {
    
    private static final Logger logger = LoggerFactory.getLogger(RedisPinService.class);
    
    private static final String USER_PIN_KEY = "chat:user:pin:";
    
    @Autowired
    private RedisTemplate<String, Object> redisTemplate;
    
    /**
     * 设置用户对会话的置顶状态
     */
    public void setPinned(String userId, String conversationId, boolean pinned) {
        String key = USER_PIN_KEY + userId;
        String field = conversationId;
        
        if (pinned) {
            redisTemplate.opsForHash().put(key, field, "1");
            logger.debug("User {} pinned conversation {}", userId, conversationId);
        } else {
            redisTemplate.opsForHash().delete(key, field);
            logger.debug("User {} unpinned conversation {}", userId, conversationId);
        }
    }
    
    /**
     * 获取用户对会话的置顶状态
     */
    public boolean isPinned(String userId, String conversationId) {
        String key = USER_PIN_KEY + userId;
        Object value = redisTemplate.opsForHash().get(key, conversationId);
        return "1".equals(value);
    }
    
    /**
     * 批量获取用户的置顶状态
     */
    public Map<String, Boolean> batchIsPinned(String userId, List<String> conversationIds) {
        if (conversationIds == null || conversationIds.isEmpty()) {
            return new HashMap<>();
        }
        
        Map<String, Boolean> result = new HashMap<>();
        for (String convId : conversationIds) {
            result.put(convId, isPinned(userId, convId));
        }
        
        return result;
    }
    
    /**
     * 切换置顶状态
     */
    public boolean togglePin(String userId, String conversationId) {
        boolean current = isPinned(userId, conversationId);
        setPinned(userId, conversationId, !current);
        return !current;
    }
    
    /**
     * 获取用户所有置顶的会话
     */
    public List<String> getUserPinnedConversations(String userId) {
        String key = USER_PIN_KEY + userId;
        Map<Object, Object> entries = redisTemplate.opsForHash().entries(key);
        return entries.keySet().stream()
            .map(Object::toString)
            .collect(java.util.stream.Collectors.toList());
    }
}