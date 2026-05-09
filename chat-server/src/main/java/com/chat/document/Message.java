package com.chat.document;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.Map;

/**
 * 消息文档
 * 
 * MongoDB collection: messages
 * 
 * 索引:
 * - conversationId: 会话索引
 * - senderId: 发送者索引
 * - createdAt: 时间索引
 * - 复合索引: {conversationId: 1, createdAt: -1} 用于分页查询
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "messages")
@CompoundIndex(name = "conv_time_idx", def = "{'conversationId': 1, 'createdAt': -1}")
public class Message {
    
    /**
     * 消息 ID (nanoid 格式)
     */
    @Id
    private String id;
    
    /**
     * 会话 ID
     * 索引
     */
    @Indexed
    private String conversationId;
    
    /**
     * 会话类型
     * - private: 私聊
     * - group: 群聊
     */
    @Indexed
    private String conversationType;
    
    /**
     * 发送者 ID
     * 索引
     */
    @Indexed
    private String senderId;
    
    /**
     * 消息内容
     */
    private String content;
    
    /**
     * 内容类型
     * - text: 文本
     * - image: 图片
     * - file: 文件
     * - audio: 语音
     * - video: 视频
     */
    @Builder.Default
    private String contentType = "text";
    
    /**
     * 消息状态
     * - sending: 发送中
     * - sent: 已发送
     * - delivered: 已送达
     * - read: 已读
     * - failed: 发送失败
     */
    @Builder.Default
    private String status = "sending";
    
    /**
     * 创建时间
     * 毫秒时间戳
     * 索引
     */
    @Indexed
    private Long createdAt;
    
    /**
     * 更新时间
     */
    private Long updatedAt;
    
    /**
     * 引用消息 ID (回复某条消息)
     */
    private String replyTo;
    
    /**
     * 扩展数据
     */
    private Map<String, Object> metadata;
}