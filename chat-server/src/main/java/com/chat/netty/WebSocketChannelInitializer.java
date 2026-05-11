package com.chat.netty;

import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.codec.http.websocketx.WebSocketServerProtocolHandler;
import io.netty.handler.timeout.IdleStateHandler;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * WebSocket Channel 初始化器
 * 
 * 配置 Netty Channel 的责任链 (Pipeline)
 * 按顺序添加各种编解码器和处理器
 * 
 * 数据流:
 * 客户端 -> Http编解码 -> WebSocket协议 -> 帧编解码 -> JSON编解码 -> 业务处理器
 */
@Component
public class WebSocketChannelInitializer extends ChannelInitializer<SocketChannel> {
    
    /**
     * WebSocket 路径
     */
    private static final String WEBSOCKET_PATH = "/ws";
    
    /**
     * 最大帧长度 (字节)
     */
    private static final int MAX_FRAME_LENGTH = 65536;
    
    /**
     * 空闲检测超时时间 (秒)
     * 90秒内没有收到任何消息则触发 IdleStateEvent
     * 前端心跳30秒，设置3倍余量
     */
    private static final int READER_IDLE_TIME = 90;
    
    /**
     * WebSocket 处理器 (业务逻辑)
     */
    @Autowired
    private WebSocketHandler webSocketHandler;
    
    /**
     * JSON 解码器
     */
    @Autowired
    private JsonDecoder jsonDecoder;
    
    /**
     * JSON 编码器
     */
    @Autowired
    private JsonEncoder jsonEncoder;
    
    /**
     * 初始化 Channel
     * 
     * 添加编解码器和处理器到 Pipeline
     * 
     * @param ch 客户端 Channel
     * @throws Exception 初始化异常
     */
    @Override
    protected void initChannel(SocketChannel ch) throws Exception {
        ChannelPipeline pipeline = ch.pipeline();
        
        // ==================== HTTP 协议层 ====================
        
        // HTTP 编解码器 (将 HTTP 请求/响应编解码为 FullHttpRequest)
        pipeline.addLast("httpCodec", new HttpServerCodec());
        
        // HTTP 消息聚合器
        // 将多个 HTTP 消息聚合成单个 FullHttpRequest/FullHttpResponse
        // 用于处理 WebSocket 握手
        pipeline.addLast("aggregator", new HttpObjectAggregator(MAX_FRAME_LENGTH));
        
        // ==================== WebSocket 协议层 ====================
        
        // WebSocket 协议处理器
        // 处理 WebSocket 握手、关闭握手、 Ping/Pong 帧
        pipeline.addLast("webSocketProtocol", new WebSocketServerProtocolHandler(
            WEBSOCKET_PATH,
            null,
            true,
            MAX_FRAME_LENGTH
        ));
        
        // ==================== 应用层 ====================
        
        // JSON 解码器 (JSON -> ClientPayload)
        pipeline.addLast("jsonDecoder", jsonDecoder);
        
        // JSON 编码器 (ServerPayload -> JSON)
        pipeline.addLast("jsonEncoder", jsonEncoder);
        
        // ==================== 空闲检测 ====================
        
        // 空闲状态检测处理器
        // 触发 IdleStateHandler 时会传递给下一个 Handler
        // 参数: readerIdleTime, writerIdleTime, allIdleTime
        pipeline.addLast("idleStateHandler", new IdleStateHandler(
            READER_IDLE_TIME,  // 读超时 (秒)
            0,                // 写超时 (秒)
            0                 // 全部超时 (秒)
        ));
        
        // ==================== 业务处理 ====================
        
        // WebSocket 业务处理器
        pipeline.addLast("webSocketHandler", webSocketHandler);
    }
}