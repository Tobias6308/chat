package com.chat.controller.web;

import com.chat.common.ErrorCode;
import com.chat.common.Md5Util;
import com.chat.document.User;
import com.chat.repository.UserRepository;
import com.chat.security.JwtUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * 认证 Controller
 *
 * 提供用户注册、登录等 HTTP 接口
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private static final Logger logger = LoggerFactory.getLogger(AuthController.class);

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JwtUtil jwtUtil;

    /**
     * 用户注册
     *
     * POST /api/auth/register
     *
     * @param request 请求体 {username, password, nickname}
     * @return 结果
     */
    @PostMapping("/register")
    public ResponseEntity<Map<String, Object>> register(@RequestBody Map<String, String> request) {
        String username = request.get("username");
        String password = request.get("password");
        String nickname = request.get("nickname");

        if (username == null || password == null) {
            Map<String, Object> error = new HashMap<>();
            error.put("code", ErrorCode.INVALID_PARAM.getCode());
            error.put("message", "用户名和密码不能为空");
            return ResponseEntity.badRequest().body(error);
        }

        // 检查用户名是否已存在
        if (userRepository.findByUsername(username).isPresent()) {
            Map<String, Object> error = new HashMap<>();
            error.put("code", ErrorCode.USERNAME_EXISTS.getCode());
            error.put("message", ErrorCode.USERNAME_EXISTS.getMessage());
            return ResponseEntity.badRequest().body(error);
        }

        // 创建用户 (密码 MD5 加密存储)
        User user = User.builder()
            .username(username)
            .nickname(nickname != null ? nickname : username)
            .password(Md5Util.encrypt(password))
            .online(false)
            .createdAt(System.currentTimeMillis())
            .build();

        userRepository.save(user);

        // 生成 token
        String token = jwtUtil.generateToken(user.getId());

        Map<String, Object> result = new HashMap<>();
        result.put("token", token);
        result.put("userId", user.getId());
        result.put("nickname", user.getNickname());

        return ResponseEntity.ok(result);
    }

    /**
     * 用户登录
     *
     * POST /api/auth/login
     *
     * @param request 请求体 {username, password}
     * @return 结果
     */
    @PostMapping("/login")
    public ResponseEntity<Map<String, Object>> login(@RequestBody Map<String, String> request) {
        String username = request.get("username");
        String password = request.get("password");

        logger.info("Login attempt - username: {}, password MD5: {}", username,
            password != null ? Md5Util.encrypt(password) : "null");

        // 查找用户
        User user = userRepository.findByUsername(username)
            .orElse(null);

        if (user == null) {
            logger.warn("Login failed - user not found: {}", username);
            Map<String, Object> error = new HashMap<>();
            error.put("code", ErrorCode.USER_NOT_FOUND.getCode());
            error.put("message", ErrorCode.USER_NOT_FOUND.getMessage());
            return ResponseEntity.badRequest().body(error);
        }

        // 验证密码 (MD5 比较)
        String inputMd5 = Md5Util.encrypt(password);
        String storedMd5 = user.getPassword();
        logger.info("Password verification - input: {}, stored: {}", inputMd5, storedMd5);

        boolean verifyResult = Md5Util.verify(password, user.getPassword());
        logger.info("Password verify result: {}", verifyResult);

        if (!verifyResult) {
            logger.warn("Login failed - password mismatch for user: {}", username);
            Map<String, Object> error = new HashMap<>();
            error.put("code", ErrorCode.PASSWORD_ERROR.getCode());
            error.put("message", ErrorCode.PASSWORD_ERROR.getMessage());
            return ResponseEntity.badRequest().body(error);
        }

        // 更新最后登录时间
        user.setLastLoginAt(System.currentTimeMillis());
        userRepository.save(user);

        // 生成 token
        String token = jwtUtil.generateToken(user.getId());

        Map<String, Object> result = new HashMap<>();
        result.put("token", token);
        result.put("userId", user.getId());
        result.put("nickname", user.getNickname());
        result.put("avatar", user.getAvatar());

        return ResponseEntity.ok(result);
    }

    /**
     * 获取用户信息
     *
     * GET /api/auth/user
     *
     * Header: Authorization: Bearer <token>
     * @param userId 用户 ID (从 token 解析)
     * @return 用户信息
     */
    @GetMapping("/user")
    public ResponseEntity<Map<String, Object>> getUserInfo(@RequestAttribute String userId) {
        return userRepository.findById(userId)
            .map(user -> {
                Map<String, Object> result = new HashMap<>();
                result.put("id", user.getId());
                result.put("username", user.getUsername());
                result.put("nickname", user.getNickname());
                result.put("avatar", user.getAvatar());
                result.put("createdAt", user.getCreatedAt());

                return ResponseEntity.ok(result);
            })
            .orElse(ResponseEntity.notFound().build());
    }
}