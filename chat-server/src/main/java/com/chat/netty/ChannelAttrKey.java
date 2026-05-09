package com.chat.netty;

import io.netty.util.AttributeKey;

/**
 * Channel 属性键
 * 
 * 用于在 Channel 中存储自定义属性
 */
public class ChannelAttrKey {
    
    /**
     * 用户 ID 属性键
     * 用于存储已认证用户的 ID
     */
    public static final AttributeKey<String> USER_ID = AttributeKey.valueOf("userId");
    
    /**
     * 认证状态属性键
     * 用于标识 Channel 是否已完成认证
     */
    public static final AttributeKey<Boolean> AUTHENTICATED = AttributeKey.valueOf("authenticated");
}