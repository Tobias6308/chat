package com.chat.netty;

import com.chat.common.ErrorCode;
import com.chat.dto.ClientPayload;
import com.chat.dto.ServerPayload;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToMessageDecoder;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
@ChannelHandler.Sharable
public class JsonDecoder extends MessageToMessageDecoder<TextWebSocketFrame> {
    
    private static final Logger logger = LoggerFactory.getLogger(JsonDecoder.class);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    
    /**
     * 解码方法
     * 
     * 将 TextWebSocketFrame 解码为 ClientPayload
     * 
     * @param ctx Channel 上下文
     * @param frame WebSocket 文本帧
     * @param out 输出列表
     * @throws Exception 解码异常
     */
    @Override
    protected void decode(ChannelHandlerContext ctx, TextWebSocketFrame frame, List<Object> out) throws Exception {
        try {
            // 获取文本内容
            String text = frame.text();
            
            // 解析 JSON 为 ClientPayload
            ClientPayload payload = OBJECT_MAPPER.readValue(text, ClientPayload.class);
            
            // 添加到输出列表，传递给下一个 Handler
            out.add(payload);
            
            logger.debug("JSON decoded successfully: type={}", payload.getType());
            
        } catch (Exception e) {
            logger.error("JSON decode error: {}", e.getMessage());
            
            // 发送错误响应
            ServerPayload error = new ServerPayload();
            error.setType("error");
            
            Map<String, String> errorPayload = new HashMap<>();
            errorPayload.put("code", ErrorCode.INVALID_JSON.getCode());
            errorPayload.put("message", ErrorCode.INVALID_JSON.getMessage());
            
            error.setPayload(errorPayload);
            error.setTimestamp(System.currentTimeMillis());
            
            ctx.writeAndFlush(error);
        }
    }
}