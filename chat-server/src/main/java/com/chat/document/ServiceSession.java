package com.chat.document;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.ArrayList;
import java.util.List;

/**
 * 客服会话文档
 *
 * MongoDB collection: service_sessions
 *
 * 索引:
 * - userId: 用户查询
 * - serviceId: 客服查询
 * - status: 状态筛选
 * - 复合索引: {serviceId: 1, status: 1} 用于客服会话列表
 * - 复合索引: {userId: 1, status: 1} 用于用户状态查询
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "service_sessions")
@CompoundIndex(name = "service_status_idx", def = "{'serviceId': 1, 'status': 1}")
@CompoundIndex(name = "user_status_idx", def = "{'userId': 1, 'status': 1}")
public class ServiceSession {

    @Id
    private String id;

    /**
     * 用户ID
     */
    @Indexed
    private String userId;

    /**
     * 用户昵称
     */
    private String userName;

    /**
     * 用户头像
     */
    private String userAvatar;

    /**
     * 客服ID
     */
    @Indexed
    private String serviceId;

    /**
     * 客服昵称
     */
    private String serviceName;

    /**
     * 会话状态: waiting(等待中) / chatting(聊天中) / finished(已结束) / timeout(超时)
     */
    private String status;

    /**
     * 会话类型: service
     */
    private String type;

    /**
     * 优先级，数值越大优先级越高
     */
    private Integer priority;

    /**
     * 开始等待时间
     */
    private Long waitingStartAt;

    /**
     * 开始聊天时间
     */
    private Long chatStartAt;

    /**
     * 结束时间
     */
    private Long endedAt;

    /**
     * 最后消息时间
     */
    private Long lastMessageAt;

    /**
     * 未读消息数
     */
    private Integer unreadCount;

    /**
     * 消息历史（用于快速查询）
     */
    @Builder.Default
    private List<Message> messages = new ArrayList<>();

    /**
     * 创建时间
     */
    private Long createdAt;

    /**
     * 更新时间
     */
    private Long updatedAt;

    /**
     * 消息状态
     */
    private String messageStatus;

    /**
     * 满意度评分 (1-5)
     */
    private Integer rating;

    /**
     * 评价备注
     */
    private String ratingComment;

    /**
     * 用户未读数
     */
    private Integer userUnreadCount;

    /**
     * 客服未读数
     */
    private Integer serviceUnreadCount;

    /**
     * 内部备注
     */
    private String internalNote;

    /**
     * 标签
     */
    private List<String> tags;
}