package com.chat.controller.admin;

import com.chat.document.Message;
import com.chat.security.AdminJwtUtil;
import com.chat.service.AdminService;
import com.chat.service.QuickReplyService;
import com.chat.service.ServiceSessionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 客服服务管理控制器
 * 提供客服会话管理、消息处理、快捷回复等功能
 * 
 * API 前缀: /api/admin/service
 * 认证方式: 通过 AdminAuthInterceptor 拦截器验证 JWT token
 */
@RestController
@RequestMapping("/api/admin/service")
public class AdminServiceController {

    private static final Logger logger = LoggerFactory.getLogger(AdminServiceController.class);

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

    @Autowired
    private ServiceSessionService serviceSessionService;

    @Autowired
    private AdminService adminService;

    @Autowired
    private AdminJwtUtil adminJwtUtil;

    @Autowired
    private QuickReplyService quickReplyService;

    /**
     * 获取客服账号列表
     * GET /api/admin/service/list
     * @return 客服列表 {services: [...]}
     */
    @GetMapping("/list")
    public ResponseEntity<Map<String, Object>> getServiceList(
            @RequestHeader(value = "Authorization", required = false) String authHeader) {

        String adminId = validateAuth(authHeader);
        if (adminId == null) {
            return unauthorized();
        }

        Map<String, Object> result = new HashMap<>();
        result.put("services", adminService.getServiceList());
        return ResponseEntity.ok(result);
    }

    /**
     * 创建客服账号
     * POST /api/admin/service/create
     * @param request {username, password, nickname}
     * @return 创建结果
     */
    @PostMapping("/create")
    public ResponseEntity<Map<String, Object>> createService(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @RequestBody Map<String, String> request) {

        String adminId = validateAuth(authHeader);
        if (adminId == null) {
            return unauthorized();
        }

        String username = request.get("username");
        String password = request.get("password");
        String nickname = request.get("nickname");

        if (username == null || password == null) {
            return ResponseEntity.badRequest().body(res(false, "Username and password required"));
        }

        Map<String, Object> result = adminService.createService(username, password, nickname);
        return ResponseEntity.ok(result);
    }

    /**
     * 更新客服在线状态
     * PUT /api/admin/service/status
     * @param request {status: "online"/"offline"/"busy"}
     * @return 更新结果
     */
    @PutMapping("/status")
    public ResponseEntity<Map<String, Object>> updateStatus(
            @RequestAttribute String adminId,
            @RequestBody Map<String, String> request) {

        String status = request.get("status");
        boolean success = adminService.updateServiceStatus(adminId, status);

        Map<String, Object> result = new HashMap<>();
        result.put("success", success);
        if (success) {
            result.put("status", status);
        }
        return ResponseEntity.ok(result);
    }

    /**
     * 接待下一个等待用户
     * POST /api/admin/service/next
     * @return 下一个用户信息 {userId, userName, sessionId, ...}
     */
    @PostMapping("/next")
    public ResponseEntity<Map<String, Object>> getNextUser(
            @RequestAttribute String adminId) {

        Map<String, Object> result = serviceSessionService.getNextUser(adminId);
        return ResponseEntity.ok(result);
    }

    /**
     * 获取会话列表（分页）
     * GET /api/admin/service/sessions
     * @param page 页码
     * @param limit 每页数量
     * @param status 状态筛选（waiting/chatting/ended）
     * @param serviceId 客服ID筛选（查看特定客服的会话）
     * @return 会话列表 {sessions: [...], total, page, limit}
     */
    @GetMapping("/sessions")
    public ResponseEntity<Map<String, Object>> getSessions(
            @RequestAttribute String adminId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int limit,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String serviceId) {

        logger.debug("GET /sessions adminId: {}, serviceId: {}", adminId, serviceId);
        Map<String, Object> result = serviceSessionService.getAllServiceSessions(page, limit, status, serviceId);
        return ResponseEntity.ok(result);
    }

    /**
     * 获取等待队列列表
     * GET /api/admin/service/queue
     * @return 排队用户列表 {total, users: [...]}
     */
    @GetMapping("/queue")
    public ResponseEntity<Map<String, Object>> getQueue() {
        return ResponseEntity.ok(serviceSessionService.getQueueList());
    }

    /**
     * 获取会话消息历史
     * GET /api/admin/service/session/{sessionId}/messages
     * @param sessionId 会话ID
     * @param limit 消息数量
     * @param skip 跳过数量
     * @return 消息列表 {messages: [...]}
     */
    @GetMapping("/session/{sessionId}/messages")
    public ResponseEntity<Map<String, Object>> getSessionMessages(
            @RequestAttribute String adminId,
            @PathVariable String sessionId,
            @RequestParam(defaultValue = "50") int limit,
            @RequestParam(defaultValue = "0") int skip) {

        Map<String, Object> result = new HashMap<>();
        result.put("messages", serviceSessionService.getSessionMessages(sessionId, limit, skip));
        return ResponseEntity.ok(result);
    }

    /**
     * 发送消息给用户
     * POST /api/admin/service/message
     * @param request {sessionId, content, contentType, fileUrl, fileName}
     * @return 发送结果 {success, message}
     */
    @PostMapping("/message")
    public ResponseEntity<Map<String, Object>> sendMessage(
            @RequestAttribute String adminId,
            @RequestBody Map<String, String> request) {

        String sessionId = request.get("sessionId");
        String content = request.get("content");
        String contentType = request.get("contentType");
        String fileUrl = request.get("fileUrl");
        String fileName = request.get("fileName");

        if (sessionId == null) {
            return ResponseEntity.badRequest().body(res(false, "sessionId required"));
        }

        Message message = serviceSessionService.sendMessage(sessionId, adminId, content, contentType, fileUrl, fileName);

        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("message", message);
        return ResponseEntity.ok(result);
    }

    /**
     * 发送正在输入状态
     * POST /api/admin/service/typing
     * 通知用户客服正在输入
     * @return 结果 {success}
     */
    @PostMapping("/typing")
    public ResponseEntity<Map<String, Object>> sendTyping(
            @RequestAttribute String adminId) {

        serviceSessionService.pushTypingToUser(adminId);
        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        return ResponseEntity.ok(result);
    }

    /**
     * 结束会话
     * POST /api/admin/service/end
     * @param request {sessionId}
     * @return 结束结果 {success}
     */
    @PostMapping("/end")
    public ResponseEntity<Map<String, Object>> endSession(
            @RequestAttribute String adminId,
            @RequestBody Map<String, String> request) {

        String sessionId = request.get("sessionId");
        boolean success = serviceSessionService.endSession(sessionId, adminId);

        return ResponseEntity.ok(res(success));
    }

    /**
     * 获取快捷回复列表
     * GET /api/admin/service/quick-replies
     * @return 快捷回复列表 {quickReplies: [...]}
     */
    @GetMapping("/quick-replies")
    public ResponseEntity<Map<String, Object>> getQuickReplies(
            @RequestAttribute String adminId) {

        Map<String, Object> result = new HashMap<>();
        result.put("quickReplies", quickReplyService.getQuickReplies(adminId));
        return ResponseEntity.ok(result);
    }

    /**
     * 添加快捷回复
     * POST /api/admin/service/quick-replies
     * @param request {title, content}
     * @return 添加结果 {success}
     */
    @PostMapping("/quick-replies")
    public ResponseEntity<Map<String, Object>> addQuickReply(
            @RequestAttribute String adminId,
            @RequestBody Map<String, String> request) {

        String title = request.get("title");
        String content = request.get("content");
        if (title == null || content == null) {
            return ResponseEntity.badRequest().body(res(false, "title and content required"));
        }

        boolean success = quickReplyService.addQuickReply(adminId, title, content);
        return ResponseEntity.ok(res(success));
    }

    /**
     * 删除快捷回复
     * DELETE /api/admin/service/quick-replies/{replyId}
     * @param replyId 快捷回复ID
     * @return 删除结果 {success}
     */
    @DeleteMapping("/quick-replies/{replyId}")
    public ResponseEntity<Map<String, Object>> deleteQuickReply(
            @RequestAttribute String adminId,
            @PathVariable String replyId) {

        boolean success = quickReplyService.deleteQuickReply(adminId, replyId);
        return ResponseEntity.ok(res(success));
    }

    /**
     * 转移会话给其他客服
     * POST /api/admin/service/transfer
     * @param request {sessionId, toServiceId}
     * @return 转移结果
     */
    @PostMapping("/transfer")
    public ResponseEntity<Map<String, Object>> transferSession(
            @RequestAttribute String adminId,
            @RequestBody Map<String, String> request) {

        String sessionId = request.get("sessionId");
        String toServiceId = request.get("toServiceId");
        if (sessionId == null || toServiceId == null) {
            return ResponseEntity.badRequest().body(res(false, "sessionId and toServiceId required"));
        }

        Map<String, Object> result = serviceSessionService.transferSession(sessionId, adminId, toServiceId);
        return ResponseEntity.ok(result);
    }

    /**
     * 获取服务统计信息
     * GET /api/admin/service/stats
     * @return 统计数据 {totalSessions, waitingCount, chattingCount, ...}
     */
    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> getServiceStats(
            @RequestAttribute String adminId) {

        Map<String, Object> stats = serviceSessionService.getServiceStats();
        return ResponseEntity.ok(stats);
    }

    /**
     * 获取客服绩效统计
     * GET /api/admin/service/performance
     * @return 绩效数据 {handledCount, avgResponseTime, avgRating, ...}
     */
    @GetMapping("/performance")
    public ResponseEntity<Map<String, Object>> getServicePerformance(
            @RequestAttribute String adminId) {

        Map<String, Object> performance = serviceSessionService.getServicePerformance(adminId);
        return ResponseEntity.ok(performance);
    }

    /**
     * 更新会话备注
     * PUT /api/admin/service/session/{sessionId}/note
     * @param sessionId 会话ID
     * @param request {note}
     * @return 更新结果 {success}
     */
    @PutMapping("/session/{sessionId}/note")
    public ResponseEntity<Map<String, Object>> updateSessionNote(
            @RequestAttribute String adminId,
            @PathVariable String sessionId,
            @RequestBody Map<String, String> request) {

        String note = request.get("note");
        boolean success = serviceSessionService.updateSessionNote(sessionId, note);

        return ResponseEntity.ok(res(success));
    }

    /**
     * 更新会话标签
     * PUT /api/admin/service/session/{sessionId}/tags
     * @param sessionId 会话ID
     * @param request {tags: [...]}
     * @return 更新结果 {success}
     */
    @PutMapping("/session/{sessionId}/tags")
    public ResponseEntity<Map<String, Object>> updateSessionTags(
            @RequestAttribute String adminId,
            @PathVariable String sessionId,
            @RequestBody Map<String, Object> request) {

        @SuppressWarnings("unchecked")
        List<String> tags = (List<String>) request.get("tags");
        boolean success = serviceSessionService.updateSessionTags(sessionId, tags);

        return ResponseEntity.ok(res(success));
    }

    /**
     * 重置客服密码
     * POST /api/admin/service/reset-password
     * @param request {serviceId}
     * @return 重置结果 {success}
     */
    @PostMapping("/reset-password")
    public ResponseEntity<Map<String, Object>> resetServicePassword(
            @RequestAttribute String adminId,
            @RequestBody Map<String, String> request) {

        String serviceId = request.get("serviceId");
        if (serviceId == null) {
            return ResponseEntity.badRequest().body(res(false, "serviceId required"));
        }

        boolean success = adminService.resetServicePassword(serviceId);
        return ResponseEntity.ok(res(success));
    }

    private String validateAuth(String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return null;
        }
        String token = authHeader.substring(7);
        return adminJwtUtil.parseToken(token);
    }

    private ResponseEntity<Map<String, Object>> unauthorized() {
        Map<String, Object> error = new HashMap<>();
        error.put("code", "UNAUTHORIZED");
        error.put("message", "Unauthorized");
        return ResponseEntity.status(401).body(error);
    }

    private ResponseEntity<Map<String, Object>> unauthorized(String reason) {
        logger.warn("Admin API unauthorized: {}", reason);
        Map<String, Object> error = new HashMap<>();
        error.put("code", "UNAUTHORIZED");
        error.put("message", "Unauthorized: " + reason);
        return ResponseEntity.status(401).body(error);
    }
}