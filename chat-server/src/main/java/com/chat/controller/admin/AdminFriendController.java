package com.chat.controller.admin;

import com.chat.service.AdminService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 好友管理控制器
 * 路径: /api/admin/friends, /api/admin/friend
 */
@RestController
@RequestMapping("/api/admin")
public class AdminFriendController {

    @Autowired
    private AdminService adminService;

    /**
     * 获取好友列表
     * GET /api/admin/friends?userId=xxx&limit=50&skip=0
     */
    @GetMapping("/friends")
    public ResponseEntity<Map<String, Object>> getFriends(
            @RequestParam(required = false) String userId,
            @RequestParam(defaultValue = "50") int limit,
            @RequestParam(defaultValue = "0") int skip) {
        Map<String, Object> result = adminService.getFriends(userId, limit, skip);
        return ResponseEntity.ok(result);
    }

    /**
     * 获取好友详情
     * GET /api/admin/friend/{id}
     */
    @GetMapping("/friend/{id}")
    public ResponseEntity<Map<String, Object>> getFriendDetail(@PathVariable String id) {
        Map<String, Object> result = adminService.getFriendDetail(id);
        if (result.containsKey("error")) {
            return ResponseEntity.badRequest().body(result);
        }
        return ResponseEntity.ok(result);
    }

    /**
     * 删除好友关系
     * DELETE /api/admin/friend/{id}
     */
    @DeleteMapping("/friend/{id}")
    public ResponseEntity<Map<String, Object>> deleteFriend(@PathVariable String id) {
        Map<String, Object> result = adminService.deleteFriend(id);
        return ResponseEntity.ok(result);
    }

    /**
     * 搜索好友
     * GET /api/admin/friends/search?keyword=xxx
     */
    @GetMapping("/friends/search")
    public ResponseEntity<Map<String, Object>> searchFriends(@RequestParam String keyword) {
        Map<String, Object> result = adminService.searchFriends(keyword);
        return ResponseEntity.ok(result);
    }
}