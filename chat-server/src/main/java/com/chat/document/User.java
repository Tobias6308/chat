package com.chat.document;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.Map;

/**
 * 用户文档
 * 
 * MongoDB collection: users
 * 
 * 索引:
 * - username: 唯一索引 (用于登录)
 * - createdAt: 普通索引
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "users")
public class User {
    
    /**
     * 用户 ID (nanoid 格式)
     */
    @Id
    private String id;
    
    /**
     * 用户名 (登录账号)
     * 唯一索引
     */
    @Indexed(unique = true)
    private String username;
    
    /**
     * 昵称
     */
    private String nickname;
    
    /**
     * 头像 URL
     */
    private String avatar;
    
    /**
     * 密码 (加密存储)
     * 仅在本地认证时使用
     */
    private String password;
    
    /**
     * 创建时间
     * 毫秒时间戳
     */
    @Indexed
    private Long createdAt;
    
    /**
     * 最后登录时间
     */
    private Long lastLoginAt;
    
    /**
     * 在线状态
     * true: 在线
     * false: 离线
     */
    private Boolean online;
    
    /**
     * 扩展信息
     */
    private Map<String, Object> extra;
    
    /**
     * 是否启用
     */
    private Boolean enabled;
}