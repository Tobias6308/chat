package com.chat.controller.admin;

import com.chat.service.AdminService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * 聊天用户管理控制器
 * 路径: /api/admin/users, /api/admin/user
 */
@RestController
@RequestMapping("/api/admin")
public class AdminUserController {

    @Autowired
    private AdminService adminService;

    /**
     * 获取用户列表
     * GET /api/admin/users?limit=20&skip=0
     */
    @GetMapping("/users")
    public ResponseEntity<Map<String, Object>> getUsers(
            @RequestParam(defaultValue = "20") int limit,
            @RequestParam(defaultValue = "0") int skip) {
        return ResponseEntity.ok(adminService.getUsers(limit, skip));
    }

    /**
     * 启用/禁用用户
     * PUT /api/admin/user/{id}/enable?enabled=true
     */
    @PutMapping("/user/{id}/enable")
    public ResponseEntity<Map<String, Object>> updateUserEnabled(
            @PathVariable String id,
            @RequestParam boolean enabled) {
        boolean success = adminService.updateUserEnabled(id, enabled);
        if (success) {
            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            return ResponseEntity.ok(result);
        }
        return error("NOT_FOUND", "用户不存在");
    }

    /**
     * 删除用户
     * DELETE /api/admin/user/{id}
     */
    @DeleteMapping("/user/{id}")
    public ResponseEntity<Map<String, Object>> deleteUser(@PathVariable String id) {
        boolean success = adminService.deleteUser(id);
        if (success) {
            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            return ResponseEntity.ok(result);
        }
        return error("NOT_FOUND", "用户不存在");
    }

    private ResponseEntity<Map<String, Object>> error(String code, String message) {
        Map<String, Object> error = new HashMap<>();
        error.put("code", code);
        error.put("message", message);
        return ResponseEntity.badRequest().body(error);
    }
}