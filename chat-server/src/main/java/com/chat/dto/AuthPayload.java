package com.chat.dto;

import java.io.Serializable;

import lombok.Data;

/**
 * 认证载荷
 * 
 * 用于用户登录认证
 * 
 * 字段说明:
 * - token: JWT 令牌
 * - deviceId: 设备 ID
 * - clientInfo: 客户端信息
 */
@Data
public class AuthPayload implements Serializable {
    
    private static final long serialVersionUID = 1L;

	/**
     * JWT 令牌
     */
    private String token;
    
    /**
     * 设备 ID
     */
    private String deviceId;
    
    /**
     * 客户端信息
     */
    private ClientInfo clientInfo;
    
    
    
    /**
     * 客户端信息
     */
    @Data
    public static class ClientInfo implements Serializable {
        private static final long serialVersionUID = 1L;

		/**
         * 平台 (web, ios, android)
         */
        private String platform;
        
        /**
         * 客户端版本
         */
        private String version;
        
        /**
         * 语言
         */
        private String language;
        
    }
}