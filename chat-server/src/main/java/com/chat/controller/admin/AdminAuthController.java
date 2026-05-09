package com.chat.controller.admin;

import com.chat.document.AdminUser;
import com.chat.security.AdminJwtUtil;
import com.chat.service.AdminService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * 管理员登录与账号控制器
 */
@RestController
@RequestMapping("/api/admin")
public class AdminAuthController {

    @Autowired
    private AdminService adminService;

    @Autowired
    private AdminJwtUtil adminJwtUtil;

    /**
     * 管理员登录
     * POST /api/admin/login
     */
    @PostMapping("/login")
    public ResponseEntity<Map<String, Object>> login(@RequestBody Map<String, String> request) {
        String username = request.get("username");
        String password = request.get("password");

        if (username == null || password == null) {
            return error("INVALID_PARAM", "用户名和密码不能为空");
        }

        AdminUser admin = adminService.login(username, password);
        if (admin == null) {
            return error("UNAUTHORIZED", "用户名或密码错误");
        }

        String token = adminJwtUtil.generateToken(admin.getId());

        Map<String, Object> result = new HashMap<>();
        result.put("adminId", admin.getId());
        result.put("username", admin.getUsername());
        result.put("nickname", admin.getNickname());
        result.put("roles", admin.getRoles());
        result.put("token", token);

        return ResponseEntity.ok(result);
    }

    //    /**
//     * 初始化默认管理员
//     * POST /api/admin/init-admin
//     */
//    @PostMapping("/init-admin")
//    public ResponseEntity<Map<String, Object>> initAdmin() {
//        boolean created = adminService.initAdminUser();
//        Map<String, Object> result = new HashMap<>();
//        result.put("created", created);
//        return ResponseEntity.ok(result);
//    }

    /**
     * 获取当前管理员信息
     * GET /api/admin/profile
     */
    @GetMapping("/profile")
    public ResponseEntity<Map<String, Object>> getProfile(@RequestHeader(value = "Authorization", required = false) String authHeader) {
        String token = authHeader != null && authHeader.startsWith("Bearer ") ? authHeader.substring(7) : null;
        String adminId = adminJwtUtil.parseToken(token);
        if (adminId == null) {
            return error("UNAUTHORIZED", "未登录");
        }

        Map<String, Object> result = adminService.getAdminProfile(adminId);
        return ResponseEntity.ok(result);
    }

    /**
     * 修改管理员信息
     * PUT /api/admin/profile
     */
    @PutMapping("/profile")
    public ResponseEntity<Map<String, Object>> updateProfile(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @RequestBody Map<String, String> request) {
        String token = authHeader != null && authHeader.startsWith("Bearer ") ? authHeader.substring(7) : null;
        String adminId = adminJwtUtil.parseToken(token);
        if (adminId == null) {
            return error("UNAUTHORIZED", "未登录");
        }

        String nickname = request.get("nickname");
        Map<String, Object> result = adminService.updateAdminProfile(adminId, nickname);
        return ResponseEntity.ok(result);
    }

    /**
     * 修改密码
     * PUT /api/admin/password
     */
    @PutMapping("/password")
    public ResponseEntity<Map<String, Object>> updatePassword(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @RequestBody Map<String, String> request) {
        String token = authHeader != null && authHeader.startsWith("Bearer ") ? authHeader.substring(7) : null;
        String adminId = adminJwtUtil.parseToken(token);
        if (adminId == null) {
            return error("UNAUTHORIZED", "未登录");
        }

        String oldPassword = request.get("oldPassword");
        String newPassword = request.get("newPassword");
        if (oldPassword == null || newPassword == null) {
            return error("INVALID_PARAM", "密码不能为空");
        }

        Map<String, Object> result = adminService.updateAdminPassword(adminId, oldPassword, newPassword);
        if (result.containsKey("success") && !(Boolean) result.get("success")) {
            return error("PASSWORD_ERROR", (String) result.get("message"));
        }
        return ResponseEntity.ok(result);
    }

    private ResponseEntity<Map<String, Object>> error(String code, String message) {
        Map<String, Object> error = new HashMap<>();
        error.put("code", code);
        error.put("message", message);
        return ResponseEntity.badRequest().body(error);
    }
}