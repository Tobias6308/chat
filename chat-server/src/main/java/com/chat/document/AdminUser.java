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
 * 管理员用户文档
 *
 * MongoDB collection: admin_users
 *
 * 角色说明:
 * - admin: 超级管理员
 * - service: 客服
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "admin_users")
public class AdminUser {

    @Id
    private String id;

    @Indexed(unique = true)
    private String username;

    private String password;

    private String nickname;

    private String avatar;

    private Long createdAt;

    private Long lastLoginAt;

    private Boolean enabled;

    private List<String> roles;

    // ============ 客服相关字段 ============

    /**
     * 客服状态: online(在线) / busy(忙碌) / offline(离线)
     */
    private String status;

    /**
     * 最大同时接待数
     */
    private Integer maxChats;

    /**
     * 当前接待数
     */
    private Integer currentChats;
}