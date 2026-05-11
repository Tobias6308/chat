package com.chat.util;

import com.chat.document.User;
import com.chat.repository.UserRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

@Component
public class RedisCacheUtil {

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ObjectMapper objectMapper;

    public static final String USER_CACHE_PREFIX = "user:cache:";
    public static final String USER_FULL_CACHE_PREFIX = "user:full:";
    public static final long USER_CACHE_TTL = 30;

    public Map<String, String> getUserInfo(String userId) {
        String cacheKey = USER_CACHE_PREFIX + userId;
        Map<String, String> cached = (Map<String, String>) redisTemplate.opsForValue().get(cacheKey);
        return cached;
    }

    public void setUserInfo(String userId, String nickname, String avatar) {
        String cacheKey = USER_CACHE_PREFIX + userId;
        Map<String, String> userMap = new HashMap<>();
        userMap.put("nickname", nickname != null ? nickname : "");
        userMap.put("avatar", avatar != null ? avatar : "");
        redisTemplate.opsForValue().set(cacheKey, userMap, USER_CACHE_TTL, TimeUnit.MINUTES);
    }

    public void invalidateUser(String userId) {
        redisTemplate.delete(USER_CACHE_PREFIX + userId);
        redisTemplate.delete(USER_FULL_CACHE_PREFIX + userId);
    }

    public Map<String, String> getUserInfoWithCache(String userId) {
        Map<String, String> cached = getUserInfo(userId);
        if (cached != null) {
            return cached;
        }

        Optional<User> userOpt = userRepository.findById(userId);
        if (userOpt.isPresent()) {
            User user = userOpt.get();
            Map<String, String> userMap = new HashMap<>();
            userMap.put("nickname", user.getNickname() != null ? user.getNickname() : "用户");
            userMap.put("avatar", user.getAvatar() != null ? user.getAvatar() : "");
            setUserInfo(userId, userMap.get("nickname"), userMap.get("avatar"));
            return userMap;
        }

        Map<String, String> defaultMap = new HashMap<>();
        defaultMap.put("nickname", "用户");
        defaultMap.put("avatar", "");
        return defaultMap;
    }

    public User getFullUserById(String userId) {
        String cacheKey = USER_FULL_CACHE_PREFIX + userId;
        String cached = (String) redisTemplate.opsForValue().get(cacheKey);
        if (cached != null) {
            try {
                return objectMapper.readValue(cached, User.class);
            } catch (JsonProcessingException e) {
                redisTemplate.delete(cacheKey);
            }
        }

        Optional<User> userOpt = userRepository.findById(userId);
        if (userOpt.isPresent()) {
            User user = userOpt.get();
            try {
                String json = objectMapper.writeValueAsString(user);
                redisTemplate.opsForValue().set(cacheKey, json, USER_CACHE_TTL, TimeUnit.MINUTES);
            } catch (JsonProcessingException ignored) {
            }
            return user;
        }
        return null;
    }

    public void invalidateFullUser(String userId) {
        redisTemplate.delete(USER_FULL_CACHE_PREFIX + userId);
    }
}