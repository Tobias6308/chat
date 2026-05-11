package com.chat.controller.web;

import com.chat.document.Message;
import com.chat.service.ServiceSessionService;
import com.chat.util.RedisCacheUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/chat/service")
public class ServiceController {

    private static final Logger logger = LoggerFactory.getLogger(ServiceController.class);

    @Autowired
    private RedisCacheUtil redisCacheUtil;

    @Autowired
    private ServiceSessionService serviceSessionService;

    private Map<String, Object> res(boolean success) {
        Map<String, Object> map = new HashMap<>();
        map.put("success", success);
        return map;
    }

    private Map<String, Object> res(boolean success, String message) {
        Map<String, Object> map = new HashMap<>();
        map.put("success", success);
        if (message != null) map.put("message", message);
        return map;
    }

    /**
     * 获取客服状态
     * GET /api/chat/service/status
     */
    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getStatus() {
        Map<String, Object> result = serviceSessionService.getServiceStatus();
        return ResponseEntity.ok(result);
    }

    /**
     * 获取所有客服列表
     * GET /api/chat/service/list
     */
    @GetMapping("/list")
    public ResponseEntity<Map<String, Object>> getServiceList() {
        Map<String, Object> result = new HashMap<>();
        result.put("services", serviceSessionService.getAllServices());
        return ResponseEntity.ok(result);
    }

    /**
     * 加入等待队列
     * POST /api/chat/service/join
     */
    @PostMapping("/join")
    public ResponseEntity<Map<String, Object>> joinQueue(
            @RequestAttribute String userId) {

        Map<String, String> userInfo = redisCacheUtil.getUserInfoWithCache(userId);
        String userName = userInfo.get("nickname");
        String userAvatar = userInfo.get("avatar");

        Map<String, Object> result = serviceSessionService.joinQueue(userId, userName, userAvatar);

        if ((boolean) result.getOrDefault("success", false)) {
            return ResponseEntity.ok(result);
        } else {
            return ResponseEntity.badRequest().body(result);
        }
    }

    /**
     * 离开等待队列
     * POST /api/chat/service/leave
     */
    @PostMapping("/leave")
    public ResponseEntity<Map<String, Object>> leaveQueue(@RequestAttribute String userId) {
        boolean success = serviceSessionService.leaveQueue(userId);
        return ResponseEntity.ok(res(success));
    }

    /**
     * 获取排队状态
     * GET /api/chat/service/queue
     */
    @GetMapping("/queue")
    public ResponseEntity<Map<String, Object>> getQueueStatus(@RequestAttribute String userId) {
        Map<String, Object> result = serviceSessionService.getQueueInfo(userId);
        return ResponseEntity.ok(result);
    }

    /**
     * 获取客服会话
     * GET /api/chat/service/conversation
     */
    @GetMapping("/conversation")
    public ResponseEntity<Map<String, Object>> getConversation(
            @RequestAttribute String userId,
            @RequestParam(defaultValue = "50") int limit,
            @RequestParam(defaultValue = "0") int skip) {

        Map<String, Object> result = serviceSessionService.getQueueInfo(userId);
        if ("chatting".equals(result.get("status"))) {
            String sessionId = (String) result.get("sessionId");
            result.put("messages", serviceSessionService.getSessionMessages(sessionId, limit, skip));
        }
        return ResponseEntity.ok(result);
    }

    /**
     * 发送消息
     * POST /api/chat/service/message
     */
    @PostMapping("/message")
    public ResponseEntity<Map<String, Object>> sendMessage(
            @RequestAttribute String userId,
            @RequestBody Map<String, String> request) {

        String content = request.get("content");
        String contentType = request.get("contentType");
        String fileUrl = request.get("fileUrl");
        String fileName = request.get("fileName");

        Map<String, Object> queueInfo = serviceSessionService.getQueueInfo(userId);
        if (!"chatting".equals(queueInfo.get("status"))) {
            return ResponseEntity.badRequest().body(res(false, "未在会话中"));
        }

        String sessionId = (String) queueInfo.get("sessionId");
        Message message = serviceSessionService.sendMessage(sessionId, userId, content, contentType, fileUrl, fileName);

        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("message", message);
        return ResponseEntity.ok(result);
    }

    /**
     * 结束会话
     * POST /api/chat/service/end
     */
    @PostMapping("/end")
    public ResponseEntity<Map<String, Object>> endSession(@RequestAttribute String userId) {
        Map<String, Object> queueInfo = serviceSessionService.getQueueInfo(userId);
        if (!"chatting".equals(queueInfo.get("status"))) {
            return ResponseEntity.badRequest().body(res(false, "未在会话中"));
        }

        String sessionId = (String) queueInfo.get("sessionId");
        boolean success = serviceSessionService.endSession(sessionId, null);

        return ResponseEntity.ok(res(success));
    }

    /**
     * 评价会话
     * POST /api/chat/service/rate
     */
    @PostMapping("/rate")
    public ResponseEntity<Map<String, Object>> rateSession(
            @RequestAttribute String userId,
            @RequestBody Map<String, Object> request) {

        try {
            Object ratingObj = request.get("rating");
            Integer rating = ratingObj != null ? (ratingObj instanceof Number ? ((Number) ratingObj).intValue() : Integer.parseInt(ratingObj.toString())) : null;
            String comment = (String) request.get("comment");

            if (rating == null || rating < 1 || rating > 5) {
                return ResponseEntity.badRequest().body(res(false, "评分必须是1-5之间的整数"));
            }

            boolean success = serviceSessionService.rateSession(userId, rating, comment);
            if (!success) {
                return ResponseEntity.ok(res(false, "会话不存在或已完成评价"));
            }
            return ResponseEntity.ok(res(true));
        } catch (Exception e) {
            logger.error("评价会话失败", e);
            return ResponseEntity.status(500).body(res(false, "评价失败: " + e.getMessage()));
        }
    }

    /**
     * 标记消息已读
     * POST /api/chat/service/read
     */
    @PostMapping("/read")
    public ResponseEntity<Map<String, Object>> markRead(@RequestAttribute String userId) {
        serviceSessionService.markUserRead(userId);
        return ResponseEntity.ok(res(true));
    }

    /**
     * 重置会话状态（清理旧数据）
     * POST /api/chat/service/reset
     */
    @PostMapping("/reset")
    public ResponseEntity<Map<String, Object>> resetSession(@RequestAttribute String userId) {
        boolean success = serviceSessionService.resetUserSession(userId);
        return ResponseEntity.ok(res(success));
    }

    /**
     * 获取用户历史会话列表
     * GET /api/chat/service/history
     */
    @GetMapping("/history")
    public ResponseEntity<Map<String, Object>> getHistory(
            @RequestAttribute String userId,
            @RequestParam(defaultValue = "20") int limit,
            @RequestParam(defaultValue = "0") int skip) {
        List<Map<String, Object>> sessions = serviceSessionService.getUserHistorySessions(userId, limit, skip);
        Map<String, Object> result = new HashMap<>();
        result.put("sessions", sessions);
        return ResponseEntity.ok(result);
    }

    /**
     * 获取历史会话消息
     * GET /api/chat/service/history/{sessionId}/messages
     */
    @GetMapping("/history/{sessionId}/messages")
    public ResponseEntity<Map<String, Object>> getHistoryMessages(
            @RequestAttribute String userId,
            @PathVariable String sessionId,
            @RequestParam(defaultValue = "50") int limit,
            @RequestParam(defaultValue = "0") int skip) {
        Map<String, Object> result = serviceSessionService.getHistorySessionMessages(userId, sessionId, limit, skip);
        return ResponseEntity.ok(result);
    }
}