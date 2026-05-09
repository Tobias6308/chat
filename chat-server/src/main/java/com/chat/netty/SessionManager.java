package com.chat.netty;

import com.chat.dto.ServerPayload;
import io.netty.channel.Channel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

@Component
public class SessionManager {
    
    private static final Logger logger = LoggerFactory.getLogger(SessionManager.class);
    private static final String REDIS_USER_SESSION = "chat:ws:session:";
    
    /**
     * 会话过期时间 (分钟)
     */
    private static final long SESSION_TIMEOUT = 30L;
    
    /**
     * Channel 映射 (Channel ID -> Channel)
     */
    private final Map<String, Channel> channels = new ConcurrentHashMap<>();
    
    /**
     * 用户-Channel 映射 (User ID -> Channel ID)
     */
    private final Map<String, String> userChannelMap = new ConcurrentHashMap<>();
    
    /**
     * Channel-用户映射 (Channel ID -> User ID)
     */
    private final Map<String, String> channelUserMap = new ConcurrentHashMap<>();
    
    /**
     * Redis 模板
     */
    @Autowired
    private RedisTemplate<String, String> redisTemplate;
    
    /**
     * 初始化方法
     */
    @PostConstruct
    public void init() {
        logger.info("SessionManager initialized");
    }
    
    /**
     * 注册用户会话
     * 
     * 将用户 ID 与 Channel 关联，并保存到 Redis
     * 
     * @param userId 用户 ID
     * @param channel Netty Channel
     */
    public void register(String userId, Channel channel) {
        // 获取 Channel 唯一标识
        String channelId = channel.id().asLongText();
        
        // 保存到内存映射
        channels.put(channelId, channel);
        userChannelMap.put(userId, channelId);
        channelUserMap.put(channelId, userId);
        
        // 保存到 Redis (设置过期时间)
        redisTemplate.opsForValue().set(
            REDIS_USER_SESSION + userId,
            channelId,
            SESSION_TIMEOUT,
            TimeUnit.MINUTES
        );
        
        // 刷新 Channel 的 lastWriteTime 以保持连接
        channel.attr(ChannelAttrKey.USER_ID).set(userId);
        
        logger.info("User registered: userId={}, channelId={}", userId, channelId);
    }
    
    /**
     * 移除会话
     * 
     * 当连接断开时调用
     * 
     * @param channel Netty Channel
     */
    public void remove(Channel channel) {
        String channelId = channel.id().asLongText();
        
        // 从内存映射中移除
        String userId = channelUserMap.remove(channelId);
        if (userId != null) {
            userChannelMap.remove(userId);
        }
        channels.remove(channelId);
        
        // 从 Redis 中移除
        if (userId != null) {
            redisTemplate.delete(REDIS_USER_SESSION + userId);
        }
        
        logger.info("Session removed: channelId={}, userId={}", channelId, userId);
    }
    
    /**
     * 发送消息给指定用户
     * 
     * @param userId 用户 ID
     * @param payload 消息载荷
     */
    public void sendToUser(String userId, ServerPayload payload) {
        String channelId = userChannelMap.get(userId);
        if (channelId == null) {
            logger.debug("User not online: {}", userId);
            return;
        }
        
        Channel channel = channels.get(channelId);
        if (channel != null && channel.isActive()) {
            channel.writeAndFlush(payload);
            logger.debug("Message sent to user: {}", userId);
        } else {
            // Channel 不活跃，从映射中移除
            userChannelMap.remove(userId);
            if (userId != null) {
                redisTemplate.delete(REDIS_USER_SESSION + userId);
            }
        }
    }
    
    /**
     * 获取用户 ID
     * 
     * @param channel Netty Channel
     * @return 用户 ID
     */
    public String getUserId(Channel channel) {
        return channel.attr(ChannelAttrKey.USER_ID).get();
    }
    
    /**
     * 发送消息给多个用户
     * 
     * @param userIds 用户 ID 列表
     * @param payload 消息载荷
     */
    public void sendToUsers(Iterable<String> userIds, ServerPayload payload) {
        for (String userId : userIds) {
            sendToUser(userId, payload);
        }
    }
    
    /**
     * 广播消息给所有在线用户
     * 
     * @param payload 消息载荷
     */
    public void broadcastAll(ServerPayload payload) {
        for (Channel channel : channels.values()) {
            if (channel.isActive()) {
                channel.writeAndFlush(payload);
            }
        }
        logger.debug("Broadcast to all users: count={}", channels.size());
    }
    
    /**
     * 检查用户是否在线
     * 
     * @param userId 用户 ID
     * @return 是否在线
     */
    public boolean isOnline(String userId) {
        String channelId = userChannelMap.get(userId);
        if (channelId == null) {
            return false;
        }
        
        Channel channel = channels.get(channelId);
        return channel != null && channel.isActive();
    }
    
    /**
     * 获取在线用户数量
     * 
     * @return 在线用户数
     */
    public int getOnlineCount() {
        return channels.size();
    }
    
    /**
     * 刷新会话过期时间
     * 
     * @param userId 用户 ID
     */
    public void refreshSession(String userId) {
        redisTemplate.expire(REDIS_USER_SESSION + userId, SESSION_TIMEOUT, TimeUnit.MINUTES);
    }
}