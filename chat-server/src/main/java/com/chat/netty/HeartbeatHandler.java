package com.chat.netty;

import com.chat.dto.ClientPayload;
import com.chat.dto.ServerPayload;
import io.netty.channel.ChannelHandlerContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Component
public class HeartbeatHandler {
    
    private static final Logger logger = LoggerFactory.getLogger(HeartbeatHandler.class);
    private static final Logger heartbeatLogger = LoggerFactory.getLogger("HEARTBEAT");
    
    /**
     * 处理 ping 消息
     *
     * @param ctx Channel 上下文
     * @param payload 客户端消息载荷
     */
    public void handlePing(ChannelHandlerContext ctx, ClientPayload payload) {
        // 构建 pong 响应
        ServerPayload pong = new ServerPayload();
        pong.setType("pong");
        pong.setTimestamp(System.currentTimeMillis());

        // 发送 pong
        ctx.writeAndFlush(pong);

        // 从 payload 获取 timestamp - 支持两种格式：
        // 1. timestamp 在顶层 payload.timestamp
        // 2. timestamp 在内层 payload.payload.timestamp
        Long timestamp = payload.getTimestamp();
        if (timestamp == null && payload.getPayload() instanceof Map) {
            Object ts = ((Map<?, ?>) payload.getPayload()).get("timestamp");
            if (ts instanceof Number) {
                timestamp = ((Number) ts).longValue();
            }
        }

        String channelId = ctx.channel().id().asLongText();
        heartbeatLogger.info("PING | channelId={} | timestamp={}", channelId, timestamp);
        logger.debug("Received ping, sent pong");
    }
    
    /**
     * 处理 pong 消息 (服务端发送给客户端)
     * 
     * @param ctx Channel 上下文
     */
    public void sendPong(ChannelHandlerContext ctx) {
        ServerPayload pong = new ServerPayload();
        pong.setType("pong");
        pong.setTimestamp(System.currentTimeMillis());
        
        ctx.writeAndFlush(pong);
        
        String channelId = ctx.channel().id().asLongText();
        heartbeatLogger.info("PONG | channelId={}", channelId);
    }
}