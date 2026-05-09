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
public class RedisMuteService {
    
    private static final Logger logger = LoggerFactory.getLogger(RedisMuteService.class);
    
    private static final String USER_MUTE_KEY = "chat:user:mute:";
    
    @Autowired
    private RedisTemplate<String, Object> redisTemplate;
    
    public void setMuted(String userId, String conversationId, boolean muted) {
        String key = USER_MUTE_KEY + userId;
        
        if (muted) {
            redisTemplate.opsForHash().put(key, conversationId, "1");
            logger.debug("User {} muted conversation {}", userId, conversationId);
        } else {
            redisTemplate.opsForHash().delete(key, conversationId);
            logger.debug("User {} unmuted conversation {}", userId, conversationId);
        }
    }
    
    public boolean isMuted(String userId, String conversationId) {
        String key = USER_MUTE_KEY + userId;
        Object value = redisTemplate.opsForHash().get(key, conversationId);
        return "1".equals(value);
    }
    
    public Map<String, Boolean> batchIsMuted(String userId, List<String> conversationIds) {
        if (conversationIds == null || conversationIds.isEmpty()) {
            return new HashMap<>();
        }
        
        Map<String, Boolean> result = new HashMap<>();
        for (String convId : conversationIds) {
            result.put(convId, isMuted(userId, convId));
        }
        
        return result;
    }
    
    public boolean toggleMute(String userId, String conversationId) {
        boolean current = isMuted(userId, conversationId);
        setMuted(userId, conversationId, !current);
        return !current;
    }
}