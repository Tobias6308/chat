package com.chat.netty;

import com.chat.dto.ServerPayload;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToMessageEncoder;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@ChannelHandler.Sharable
public class JsonEncoder extends MessageToMessageEncoder<ServerPayload> {
    
    private static final Logger logger = LoggerFactory.getLogger(JsonEncoder.class);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    
    /**
     * 编码方法
     * 
     * 将 ServerPayload 编码为 TextWebSocketFrame
     * 
     * @param ctx Channel 上下文
     * @param msg ServerPayload
     * @param out 输出列表
     * @throws Exception 编码异常
     */
    @Override
    protected void encode(ChannelHandlerContext ctx, ServerPayload msg, List<Object> out) throws Exception {
        try {
            // 序列化为 JSON 字符串
            String json = OBJECT_MAPPER.writeValueAsString(msg);
            
            // 包装为 TextWebSocketFrame
            TextWebSocketFrame frame = new TextWebSocketFrame(json);
            
            // 添加到输出列表
            out.add(frame);
            
            logger.debug("JSON encoded successfully: type={}", msg.getType());
            
        } catch (Exception e) {
            logger.error("JSON encode error: {}", e.getMessage(), e);
        }
    }
}