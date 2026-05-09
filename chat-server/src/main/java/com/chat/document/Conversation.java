package com.chat.document;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Transient;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.List;

/**
 * 会话文档
 *
 * MongoDB collection: conversations
 *
 * 会话类型:
 * - private: 私聊
 * - group: 群聊
 *
 * 索引:
 * - participants: 参与者索引 (用于查询用户的会话列表)
 * - updatedAt: 更新时间索引
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "conversations")
@CompoundIndex(name = "relate_type_idx", def = "{'relateId': 1, 'type': 1}", unique = true)
public class Conversation {
    
    /**
     * 会话 ID (nanoid 格式)
     */
    @Id
    private String id;
    
    /**
     * 会话类型
     * - private: 私聊
     * - group: 群聊
     */
    private String type;
    
    /**
     * 关联 ID
     * 私聊: 好友记录 ID (friend 表的 _id)
     * 群聊: 群组 ID (groups 表的 _id)
     */
    private String relateId;
    
    /**
     * 参与者 ID 列表
     * 私聊: [userId1, userId2]
     * 群聊: [userId1, userId2, ...]
     */
    @Indexed
    private List<String> participants;
    
    /**
     * 会话名称
     * 私聊: 对方昵称
     * 群聊: 群名称
     */
    private String name;
    
    /**
     * 会话头像
     */
    private String avatar;

    /**
     * 是否免打扰
     */
    @Builder.Default
    private Boolean muted = false;
    
    /**
     * 创建时间
     */
    private Long createdAt;
    
    /**
     * 更新时间
     */
    private Long updatedAt;

    /**
     * 最新消息 (仅用于 API 响应，不存储到数据库)
     */
    @Transient
    private Message lastMessage;
}