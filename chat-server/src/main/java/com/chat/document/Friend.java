package com.chat.document;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * 好友关系文档
 *
 * MongoDB collection: friends
 *
 * 索引:
 * - 复合唯一索引: {userId: 1, friendId: 1} 确保好友关系唯一
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "friends")
@CompoundIndex(name = "user_friend_idx", def = "{'userId': 1, 'friendId': 1}", unique = true)
public class Friend {
    
    /**
     * 关系 ID
     */
    @Id
    private String id;
    
    /**
     * 用户 ID (发起者)
     */
    private String userId;
    
    /**
     * 好友 ID (对方) - 兼容旧代码
     */
    private String friendId;
    
    /**
     * 关联用户 ID (对方) - 用于快速查询好友关系
     */
    private String relatedUserId;
    
    /**
     * 好友备注
     */
    private String remark;
    
    /**
     * 好友状态
     * - pending: 待确认
     * - accepted: 已同意
     * - rejected: 已拒绝
     * - blocked: 已拉黑
     */
    @Builder.Default
    private String status = "accepted";
    
    /**
     * 来源
     * - search: 搜索添加
     * - invite: 邀请添加
     * - group: 群聊添加
     */
    private String source;
    
    /**
     * 创建时间
     */
    private Long createdAt;
    
    /**
     * 更新时间
     */
    private Long updatedAt;
}