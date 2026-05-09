package com.chat.controller.admin;

import com.chat.service.AdminService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * 统计与管理员账户控制器
 * 路径: /api/admin/stats, /api/admin/admins, /api/admin/admin
 */
@RestController
@RequestMapping("/api/admin")
public class AdminStatsController {

    @Autowired
    private AdminService adminService;

    /**
     * 获取统计数据
     * GET /api/admin/stats
     */
    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> getStats() {
        return ResponseEntity.ok(adminService.getStats());
    }

    /**
     * 获取管理员列表
     * GET /api/admin/admins
     */
    @GetMapping("/admins")
    public ResponseEntity<Map<String, Object>> getAdmins() {
        Map<String, Object> result = adminService.getAdmins();
        return ResponseEntity.ok(result);
    }

    /**
     * 创建管理员
     * POST /api/admin/admin
     */
    @PostMapping("/admin")
    public ResponseEntity<Map<String, Object>> createAdmin(@RequestBody Map<String, String> request) {
        String username = request.get("username");
        String password = request.get("password");
        String nickname = request.get("nickname");

        if (username == null || password == null) {
            return error("INVALID_PARAM", "用户名和密码不能为空");
        }

        Map<String, Object> result = adminService.createAdmin(username, password, nickname);
        return ResponseEntity.ok(result);
    }

    /**
     * 删除管理员
     * DELETE /api/admin/admin/{id}
     */
    @DeleteMapping("/admin/{id}")
    public ResponseEntity<Map<String, Object>> deleteAdmin(@PathVariable String id) {
        Map<String, Object> result = adminService.deleteAdmin(id);
        return ResponseEntity.ok(result);
    }

    private ResponseEntity<Map<String, Object>> error(String code, String message) {
        Map<String, Object> error = new HashMap<>();
        error.put("code", code);
        error.put("message", message);
        return ResponseEntity.badRequest().body(error);
    }
}