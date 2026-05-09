package com.chat.controller.admin;

import com.chat.service.AdminService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * 群组管理控制器
 * 路径: /api/admin/groups, /api/admin/group
 */
@RestController
@RequestMapping("/api/admin")
public class AdminGroupController {

    @Autowired
    private AdminService adminService;

    /**
     * 获取群组列表
     * GET /api/admin/groups?limit=20&skip=0
     */
    @GetMapping("/groups")
    public ResponseEntity<Map<String, Object>> getGroups(
            @RequestParam(defaultValue = "20") int limit,
            @RequestParam(defaultValue = "0") int skip) {
        return ResponseEntity.ok(adminService.getGroups(limit, skip));
    }

    /**
     * 删除群组
     * DELETE /api/admin/group/{id}
     */
    @DeleteMapping("/group/{id}")
    public ResponseEntity<Map<String, Object>> deleteGroup(@PathVariable String id) {
        boolean success = adminService.deleteGroup(id);
        if (success) {
            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            return ResponseEntity.ok(result);
        }
        return error("NOT_FOUND", "群组不存在");
    }

    private ResponseEntity<Map<String, Object>> error(String code, String message) {
        Map<String, Object> error = new HashMap<>();
        error.put("code", code);
        error.put("message", message);
        return ResponseEntity.badRequest().body(error);
    }
}