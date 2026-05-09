package com.chat.document;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.List;

/**
 * 群组文档
 * 
 * MongoDB collection: groups
 * 
 * 索引:
 * - ownerId: 群主索引
 * - createdAt: 创建时间索引
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "groups")
public class Group {
    
    /**
     * 群 ID
     */
    @Id
    private String id;
    
    /**
     * 群名称
     */
    private String name;
    
    /**
     * 群头像
     */
    private String avatar;
    
    /**
     * 群描述
     */
    private String description;
    
    /**
     * 群主 ID
     * 索引
     */
    @Indexed
    private String ownerId;
    
    /**
     * 群成员列表
     * 使用嵌入文档
     */
    private List<GroupMember> members;
    
    /**
     * 成员数量
     */
    private Integer memberCount;
    
    /**
     * 群类型
     * - group: 普通群
     * - channel: 频道
     */
    @Builder.Default
    private String type = "group";
    
    /**
     * 是否全员禁言
     */
    @Builder.Default
    private Boolean allMuted = false;
    
    /**
     * 创建时间
     * 索引
     */
    @Indexed
    private Long createdAt;
    
    /**
     * 更新时间
     */
    private Long updatedAt;
    
    /**
     * 群成员
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class GroupMember {
        /**
         * 用户 ID
         */
        private String userId;
        
        /**
         * 昵称
         */
        private String nickname;
        
        /**
         * 头像
         */
        private String avatar;
        
        /**
         * 角色
         * - owner: 群主
         * - admin: 管理员
         * - member: 普通成员
         */
        @Builder.Default
        private String role = "member";
        
        /**
         * 加入时间
         */
        private Long joinedAt;
        
        /**
         * 是否被禁言
         */
        @Builder.Default
        private Boolean muted = false;
        
        /**
         * 最后发言时间
         */
        private Long lastSpeakAt;
    }
}