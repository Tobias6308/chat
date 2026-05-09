package com.chat.common;

public enum ErrorCode {
    
    // Auth errors
    USERNAME_EXISTS("USERNAME_EXISTS", "用户名已存在"),
    USER_NOT_FOUND("USER_NOT_FOUND", "用户不存在"),
    PASSWORD_ERROR("PASSWORD_ERROR", "密码错误"),
    INVALID_TOKEN("INVALID_TOKEN", "无效的令牌"),
    AUTH_EXPIRED("AUTH_EXPIRED", "认证已过期"),
    INVALID_AUTH("INVALID_AUTH", "认证信息不完整"),
    AUTH_FAILED("AUTH_FAILED", "认证失败"),
    NOT_AUTHENTICATED("NOT_AUTHENTICATED", "未认证"),
    
    // Permission errors
    FORBIDDEN("FORBIDDEN", "无权限访问"),
    
    // Message errors
    SEND_FAILED("SEND_FAILED", "发送失败"),
    HISTORY_FAILED("HISTORY_FAILED", "获取历史消息失败"),
    
    // Group errors
    GROUP_NOT_FOUND("GROUP_NOT_FOUND", "群组不存在"),
    CONVERSATION_NOT_FOUND("CONVERSATION_NOT_FOUND", "会话不存在"),
    
    // Friend errors
    FRIEND_NOT_FOUND("FRIEND_NOT_FOUND", "好友不存在"),
    FRIEND_REQUEST_EXISTS("FRIEND_REQUEST_EXISTS", "好友请求已存在"),
    
    // Data errors
    INVALID_JSON("INVALID_JSON", "无效的JSON格式"),
    INVALID_PARAM("INVALID_PARAM", "参数错误"),
    
    // System errors
    INTERNAL_ERROR("INTERNAL_ERROR", "服务器内部错误");
    
    private final String code;
    private final String message;
    
    ErrorCode(String code, String message) {
        this.code = code;
        this.message = message;
    }
    
    public String getCode() {
        return code;
    }
    
    public String getMessage() {
        return message;
    }
}