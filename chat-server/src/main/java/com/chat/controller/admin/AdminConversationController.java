package com.chat.controller.admin;

import com.chat.service.AdminService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * 会话管理控制器
 * 路径: /api/admin/conversations, /api/admin/conversation
 */
@RestController
@RequestMapping("/api/admin")
public class AdminConversationController {

    @Autowired
    private AdminService adminService;

    /**
     * 获取会话列表
     * GET /api/admin/conversations?type=private|group&limit=50&skip=0
     */
    @GetMapping("/conversations")
    public ResponseEntity<Map<String, Object>> getConversations(
            @RequestParam(required = false) String type,
            @RequestParam(defaultValue = "50") int limit,
            @RequestParam(defaultValue = "0") int skip) {
        Map<String, Object> result = adminService.getConversations(type, limit, skip);
        return ResponseEntity.ok(result);
    }

    /**
     * 获取会话详情
     * GET /api/admin/conversation/{id}
     */
    @GetMapping("/conversation/{id}")
    public ResponseEntity<Map<String, Object>> getConversationDetail(@PathVariable String id) {
        Map<String, Object> result = adminService.getConversationDetail(id);
        if (result.containsKey("error")) {
            return ResponseEntity.badRequest().body(result);
        }
        return ResponseEntity.ok(result);
    }

    /**
     * 搜索会话
     * GET /api/admin/conversations/search?keyword=xxx
     */
    @GetMapping("/conversations/search")
    public ResponseEntity<Map<String, Object>> searchConversations(@RequestParam String keyword) {
        Map<String, Object> result = adminService.searchConversations(keyword);
        return ResponseEntity.ok(result);
    }

    /**
     * 删除会话
     * DELETE /api/admin/conversation/{id}
     */
    @DeleteMapping("/conversation/{id}")
    public ResponseEntity<Map<String, Object>> deleteConversation(@PathVariable String id) {
        boolean success = adminService.deleteConversation(id);
        if (success) {
            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            return ResponseEntity.ok(result);
        }
        return error("NOT_FOUND", "会话不存在");
    }

    private ResponseEntity<Map<String, Object>> error(String code, String message) {
        Map<String, Object> error = new HashMap<>();
        error.put("code", code);
        error.put("message", message);
        return ResponseEntity.badRequest().body(error);
    }
}