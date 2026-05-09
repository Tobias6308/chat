package com.chat.controller.admin;

import com.chat.service.AdminService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * 消息管理控制器
 * 路径: /api/admin/messages, /api/admin/message
 */
@RestController
@RequestMapping("/api/admin")
public class AdminMessageController {

    @Autowired
    private AdminService adminService;

    /**
     * 获取消息列表
     * GET /api/admin/messages?conversationId=xxx&senderId=xxx&limit=50&skip=0
     */
    @GetMapping("/messages")
    public ResponseEntity<Map<String, Object>> getMessages(
            @RequestParam(required = false) String conversationId,
            @RequestParam(required = false) String senderId,
            @RequestParam(defaultValue = "50") int limit,
            @RequestParam(defaultValue = "0") int skip) {
        return ResponseEntity.ok(adminService.getMessages(conversationId, senderId, limit, skip));
    }

    /**
     * 删除消息
     * DELETE /api/admin/message/{id}
     */
    @DeleteMapping("/message/{id}")
    public ResponseEntity<Map<String, Object>> deleteMessage(@PathVariable String id) {
        boolean success = adminService.deleteMessage(id);
        if (success) {
            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            return ResponseEntity.ok(result);
        }
        return error("NOT_FOUND", "消息不存在");
    }

    private ResponseEntity<Map<String, Object>> error(String code, String message) {
        Map<String, Object> error = new HashMap<>();
        error.put("code", code);
        error.put("message", message);
        return ResponseEntity.badRequest().body(error);
    }
}