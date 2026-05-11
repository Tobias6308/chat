package com.chat.controller.web;

import com.chat.common.ErrorCode;
import com.chat.common.Md5Util;
import com.chat.document.User;
import com.chat.repository.UserRepository;
import com.chat.util.RedisCacheUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * 用户 Controller
 *
 * 提供用户信息管理 HTTP 接口
 */
@RestController
@RequestMapping("/api/user")
public class UserController {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RedisCacheUtil redisCacheUtil;

    /**
     * 获取当前用户信息
     *
     * GET /api/user/info
     *
     * @param userId 当前用户 ID
     * @return 用户信息
     */
    @GetMapping("/info")
    public ResponseEntity<Map<String, Object>> getInfo(@RequestAttribute String userId) {
        User user = redisCacheUtil.getFullUserById(userId);
        if (user == null) {
            return ResponseEntity.notFound().build();
        }
        Map<String, Object> result = new HashMap<>();
        result.put("userId", user.getId());
        result.put("username", user.getUsername());
        result.put("nickname", user.getNickname());
        result.put("avatar", user.getAvatar());
        result.put("createdAt", user.getCreatedAt());
        result.put("lastLoginAt", user.getLastLoginAt());
        result.put("online", user.getOnline());
        result.put("extra", user.getExtra());

        return ResponseEntity.ok(result);
    }

    /**
     * 获取指定用户信息
     *
     * GET /api/user/:id
     *
     * @param userId 用户 ID
     * @return 用户信息
     */
    @GetMapping("/{id}")
    public ResponseEntity<Map<String, Object>> getById(@PathVariable String id) {
        User user = redisCacheUtil.getFullUserById(id);
        if (user == null) {
            return ResponseEntity.notFound().build();
        }
        Map<String, Object> result = new HashMap<>();
        result.put("userId", user.getId());
        result.put("username", user.getUsername());
        result.put("nickname", user.getNickname());
        result.put("avatar", user.getAvatar());
        result.put("online", user.getOnline());

        return ResponseEntity.ok(result);
    }

    /**
     * 修改基本信息
     *
     * PUT /api/user/profile
     *
     * @param request 请求体 {nickname, avatar}
     * @param userId 当前用户 ID
     * @return 结果
     */
    @PutMapping("/profile")
    public ResponseEntity<Map<String, Object>> updateProfile(
            @RequestBody Map<String, String> request,
            @RequestAttribute String userId) {

        User user = redisCacheUtil.getFullUserById(userId);
        if (user == null) {
            return ResponseEntity.notFound().build();
        }

        String nickname = request.get("nickname");
        String avatar = request.get("avatar");

        if (nickname != null && !nickname.trim().isEmpty()) {
            user.setNickname(nickname.trim());
        }
        if (avatar != null) {
            user.setAvatar(avatar);
        }

        userRepository.save(user);
        redisCacheUtil.invalidateFullUser(userId);

        Map<String, Object> result = new HashMap<>();
        result.put("userId", user.getId());
        result.put("nickname", user.getNickname());
        result.put("avatar", user.getAvatar());

        return ResponseEntity.ok(result);
    }

    /**
     * 修改密码
     *
     * PUT /api/user/password
     *
     * @param request 请求体 {oldPassword, newPassword}
     * @param userId 当前用户 ID
     * @return 结果
     */
    @PutMapping("/password")
    public ResponseEntity<Map<String, Object>> updatePassword(
            @RequestBody Map<String, String> request,
            @RequestAttribute String userId) {

        String oldPassword = request.get("oldPassword");
        String newPassword = request.get("newPassword");

        if (oldPassword == null || newPassword == null) {
            return error(ErrorCode.INVALID_PARAM, "密码不能为空");
        }

        if (newPassword.length() < 6) {
            return error(ErrorCode.INVALID_PARAM, "新密码长度至少6位");
        }

        return userRepository.findById(userId)
            .<ResponseEntity<Map<String, Object>>>map(user -> {
                if (!Md5Util.verify(oldPassword, user.getPassword())) {
                    return error(ErrorCode.PASSWORD_ERROR, "原密码错误");
                }

                user.setPassword(Md5Util.encrypt(newPassword));
                userRepository.save(user);

                Map<String, Object> result = new HashMap<>();
                result.put("success", true);

                return ResponseEntity.ok(result);
            })
            .orElse(ResponseEntity.notFound().build());
    }

    /**
     * 批量获取用户基本信息
     *
     * GET /api/user/batch?userIds=user1,user2,user3
     *
     * @param userIds 用户ID列表（逗号分隔）
     * @return 用户信息列表
     */
    @GetMapping("/batch")
    public ResponseEntity<Map<String, Object>> getBatchInfo(@RequestParam String userIds) {
        if (userIds == null || userIds.trim().isEmpty()) {
            return ResponseEntity.ok(new HashMap<>());
        }

        String[] ids = userIds.split(",");
        Iterable<User> users = userRepository.findAllById(java.util.Arrays.asList(ids));

        Map<String, Map<String, Object>> userMap = new HashMap<>();
        for (User user : users) {
            Map<String, Object> info = new HashMap<>();
            info.put("userId", user.getId());
            info.put("username", user.getUsername());
            info.put("nickname", user.getNickname());
            info.put("avatar", user.getAvatar());
            userMap.put(user.getId(), info);
        }

        Map<String, Object> result = new HashMap<>();
        result.put("users", userMap);

        return ResponseEntity.ok(result);
    }

    private ResponseEntity<Map<String, Object>> error(ErrorCode code, String message) {
        Map<String, Object> error = new HashMap<>();
        error.put("code", code.getCode());
        error.put("message", message);
        return ResponseEntity.badRequest().body(error);
    }
}