package com.chat.dto;

import java.io.Serializable;

import lombok.Data;

/**
 * 服务端消息载荷
 * 
 * WebSocket 服务端发送到客户端的消息格式
 * 
 * 字段说明:
 * - type: 消息类型 (auth_ok, pong, message, ack_ok, history, error)
 * - id: 关联的客户端消息 ID
 * - payload: 消息载荷
 * - timestamp: 时间戳
 * - cursor: 游标 (用于分页)
 */
@Data
public class ServerPayload implements Serializable {
    
    private static final long serialVersionUID = 1L;

	/**
     * 消息类型
     * - auth_ok: 认证成功
     * - pong: 心跳响应
     * - message: 新消息
     * - ack_ok: 消息确认
     * - history: 历史消息
     * - error: 错误
     */
    private String type;
    
    /**
     * 关联的客户端消息 ID
     */
    private String id;
    
    /**
     * 消息载荷
     */
    private Object payload;
    
    /**
     * 时间戳
     * UTC 毫秒
     */
    private Long timestamp;
    
    /**
     * 游标
     * 用于分页查询
     */
    private String cursor;
    
    
}