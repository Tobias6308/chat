package com.chat.dto;

import java.io.Serializable;

import lombok.Data;

/**
 * 客户端消息载荷
 * 
 * WebSocket 客户端发送到服务端的消息格式
 * 
 * 字段说明:
 * - type: 消息类型 (auth, ping, send, ack, fetch_history)
 * - id: 消息唯一 ID (nanoid 格式)
 * - payload: 消息载荷 (具体业务数据)
 * - timestamp: 时间戳 (UTC 毫秒)
 */
@Data
public class ClientPayload implements Serializable {
    
    private static final long serialVersionUID = 1L;

	/**
     * 消息类型
     * - auth: 认证
     * - ping: 心跳
     * - send: 发送消息
     * - ack: 消息确认
     * - fetch_history: 获取历史消息
     */
    private String type;
    
    /**
     * 消息唯一 ID
     * 使用 nanoid 算法生成
     */
    private String id;
    
    /**
     * 消息载荷
     * 具体业务数据，类型取决于 type
     */
    private Object payload;
    
    /**
     * 时间戳
     * UTC 毫秒
     */
    private Long timestamp;
    
    
}