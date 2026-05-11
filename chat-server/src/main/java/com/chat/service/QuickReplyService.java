package com.chat.service;

import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 快捷回复服务
 * 提供客服快捷回复的 CRUD 操作
 * 数据存储在内存中（ConcurrentHashMap）
 * 
 * 关联接口:
 * - GET /api/admin/service/quick-replies 获取列表
 * - POST /api/admin/service/quick-replies 添加
 * - DELETE /api/admin/service/quick-replies/:id 删除
 */
@Service
public class QuickReplyService {

    /**
     * 快捷回复缓存
     * Key: adminId (客服ID), Value: 快捷回复列表
     */
    private final Map<String, List<QuickReply>> quickReplies = new ConcurrentHashMap<>();

    /**
     * 快捷回复实体类
     */
    public static class QuickReply {
        private String id;
        private String title;
        private String content;
        private int sortOrder;

        public QuickReply() {}

        public QuickReply(String id, String title, String content, int sortOrder) {
            this.id = id;
            this.title = title;
            this.content = content;
            this.sortOrder = sortOrder;
        }

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public String getTitle() { return title; }
        public void setTitle(String title) { this.title = title; }
        public String getContent() { return content; }
        public void setContent(String content) { this.content = content; }
        public int getSortOrder() { return sortOrder; }
        public void setSortOrder(int sortOrder) { this.sortOrder = sortOrder; }
    }

    /**
     * 获取指定客服的快捷回复列表
     * @param serviceId 客服ID
     * @return 快捷回复列表
     */
    public List<QuickReply> getQuickReplies(String serviceId) {
        return quickReplies.getOrDefault(serviceId, getDefaultReplies());
    }

    /**
     * 添加快捷回复
     * @param serviceId 客服ID
     * @param title 标题
     * @param content 内容
     * @return 是否成功
     */
    public boolean addQuickReply(String serviceId, String title, String content) {
        List<QuickReply> replies = quickReplies.computeIfAbsent(serviceId, k -> new ArrayList<>());
        String id = "qr_" + System.currentTimeMillis();
        replies.add(new QuickReply(id, title, content, replies.size()));
        return true;
    }

    /**
     * 删除快捷回复
     * @param serviceId 客服ID
     * @param replyId 快捷回复ID
     * @return 是否成功
     */
    public boolean deleteQuickReply(String serviceId, String replyId) {
        List<QuickReply> replies = quickReplies.get(serviceId);
        if (replies == null) return false;
        return replies.removeIf(r -> r.getId().equals(replyId));
    }

    /**
     * 获取默认快捷回复
     * 当客服没有设置快捷回复时使用
     * @return 默认快捷回复列表
     */
    private List<QuickReply> getDefaultReplies() {
        return Arrays.asList(
            new QuickReply("qr1", "您好", "您好，请问有什么可以帮您？", 1),
            new QuickReply("qr2", "正在处理", "好的，请稍等，我正在为您处理。", 2),
            new QuickReply("qr3", "请稍等", "请您稍等片刻，我先查询一下相关信息。", 3),
            new QuickReply("qr4", "感谢耐心", "非常感谢您的耐心等待。", 4),
            new QuickReply("qr5", "满意评价", "如果您对我们的服务满意，请给予好评！", 5),
            new QuickReply("qr6", "结束对话", "感谢您的咨询，祝您生活愉快！再见。", 6)
        );
    }
}