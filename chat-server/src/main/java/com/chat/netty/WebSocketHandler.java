package com.chat.netty;

import com.chat.dto.ClientPayload;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.timeout.IdleState;
import io.netty.handler.timeout.IdleStateEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
@ChannelHandler.Sharable
public class WebSocketHandler extends SimpleChannelInboundHandler<ClientPayload> {
    
    private static final Logger logger = LoggerFactory.getLogger(WebSocketHandler.class);
    
    @Autowired
    private AuthHandler authHandler;
    
    @Autowired
    private MessageHandler messageHandler;
    
    @Autowired
    private HeartbeatHandler heartbeatHandler;
    
    @Autowired
    private ServiceMessageHandler serviceMessageHandler;
    
    /**
     * 会话管理器
     */
    @Autowired
    private SessionManager sessionManager;
    
    /**
     * 通道激活时触发
     * 
     * @param ctx Channel 上下文
     * @throws Exception 异常
     */
    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        String remoteAddress = ctx.channel().remoteAddress().toString();
        logger.info("New connection established: {}", remoteAddress);
        super.channelActive(ctx);
    }
    
    /**
     * 通道非激活时触发 (连接断开)
     * 
     * @param ctx Channel 上下文
     * @throws Exception 异常
     */
    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        String remoteAddress = ctx.channel().remoteAddress().toString();
        logger.info("Connection closed: {}", remoteAddress);
        
        // 从会话管理器中移除
        sessionManager.remove(ctx.channel());
        
        super.channelInactive(ctx);
    }
    
    /**
     * 读取消息
     * 
     * 处理客户端发送的 JSON 消息
     * 
     * @param ctx Channel 上下文
     * @param msg 客户端消息载荷
     * @throws Exception 异常
     */
    @Override
    protected void channelRead0(ChannelHandlerContext ctx, ClientPayload msg) throws Exception {
        // 获取消息类型
        String type = msg.getType();
        
        // 获取用户 ID (如果已认证)
        String userId = sessionManager.getUserId(ctx.channel());
        
        logger.debug("Received message type: {}, userId: {}", type, userId);
        
        // 消息路由分发
        switch (type) {
            // 认证消息
            case "auth":
                authHandler.handle(ctx, msg);
                break;
            
            // 心跳 ping
            case "ping":
                heartbeatHandler.handlePing(ctx, msg);
                break;
            
            // 发送消息
            case "send":
                messageHandler.handleSend(ctx, msg);
                break;
            
            // 消息确认 (已读/已送达)
            case "ack":
                messageHandler.handleAck(ctx, msg);
                break;
            
// 获取历史消息
            case "fetch_history":
                messageHandler.handleHistory(ctx, msg);
                break;
            
            // 客服会话消息
            case "service":
                serviceMessageHandler.handleServiceMessage(ctx, msg);
                break;
             
            // 未知消息类型
            default:
                logger.warn("Unknown message type: {}", type);
                break;
        }
    }
    
    /**
     * 空闲状态事件处理
     * 
     * 当 IdleStateHandler 触发空闲状态时调用
     * 用于检测客户端是否还活着
     * 
     * @param ctx Channel 上下文
     * @param evt 空闲事件
     */
    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        if (evt instanceof IdleStateEvent) {
            IdleStateEvent idleEvent = (IdleStateEvent) evt;
            
            // 判断空闲类型
            if (idleEvent.state() == IdleState.READER_IDLE) {
                // 读空闲 (长时间没有收到客户端消息)
                logger.warn("Reader idle timeout, closing connection: {}", ctx.channel().remoteAddress());
                
                // 关闭连接
                ctx.close();
            }
        } else {
            super.userEventTriggered(ctx, evt);
        }
    }
    
    /**
     * 异常捕获处理
     * 
     * @param ctx Channel 上下文
     * @param cause 异常原因
     */
    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        logger.error("Exception caught in WebSocketHandler: {}", cause.getMessage(), cause);
        
        // 关闭连接
        ctx.close();
    }
}