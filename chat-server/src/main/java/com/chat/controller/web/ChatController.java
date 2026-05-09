package com.chat.controller.web;

import com.chat.common.ErrorCode;
import com.chat.document.Conversation;
import com.chat.document.Message;
import com.chat.document.User;
import com.chat.repository.ConversationRepository;
import com.chat.repository.MessageRepository;
import com.chat.repository.UserRepository;
import com.chat.service.ConversationService;
import com.chat.service.MessageService;
import com.chat.service.RedisMuteService;
import com.chat.service.RedisPinService;
import com.chat.service.RedisUnreadService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 聊天 Controller
 *
 * 提供会话管理、消息历史等 HTTP 接口
 */
@RestController
@RequestMapping("/api/chat")
public class ChatController {

    @Autowired
    private ConversationService conversationService;

    @Autowired
    private MessageService messageService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private MessageRepository messageRepository;

    @Autowired
    private ConversationRepository conversationRepository;
    
    @Autowired
    private RedisUnreadService redisUnreadService;

    @Autowired
    private RedisPinService redisPinService;

    @Autowired
    private RedisMuteService redisMuteService;

    /**
     * 获取用户的会话列表
     *
     * GET /api/chat/conversations
     *
     * @param userId 用户 ID
     * @return 会话列表
     */
    @GetMapping("/conversations")
    public ResponseEntity<Map<String, Object>> getConversations(
            @RequestAttribute String userId,
            @RequestParam(defaultValue = "500") int limit) {

        // 获取用户的总会话数
        long totalCount = conversationRepository.countByParticipantsContaining(userId);
        
        List<Conversation> conversations = conversationService.getUserConversations(userId, limit);

        // 收集会话ID用于批量查询
        List<String> convIds = conversations.stream().map(Conversation::getId).collect(Collectors.toList());
        
        // 批量查询有新消息的状态
        Map<String, Boolean> newStatusMap = redisUnreadService.batchHasNewMessages(userId, convIds);
        
        // 批量查询置顶状态
        Map<String, Boolean> pinnedMap = redisPinService.batchIsPinned(userId, convIds);
        
        // 批量查询静音状态
        Map<String, Boolean> mutedMap = redisMuteService.batchIsMuted(userId, convIds);
        
        List<Map<String, Object>> list = new java.util.ArrayList<>();
        for (Conversation conv : conversations) {
            Map<String, Object> item = new HashMap<>();
            item.put("id", conv.getId());
            item.put("type", conv.getType());
            item.put("name", conv.getName());
            item.put("avatar", conv.getAvatar());
            item.put("hasNewMessages", newStatusMap.getOrDefault(conv.getId(), false));
            item.put("pinned", pinnedMap.getOrDefault(conv.getId(), false));
            item.put("muted", mutedMap.getOrDefault(conv.getId(), false));
            item.put("createdAt", conv.getCreatedAt());
            item.put("updatedAt", conv.getUpdatedAt());

            if (conv.getLastMessage() != null) {
                Message msg = conv.getLastMessage();
                Map<String, Object> lastMessage = new HashMap<>();
                lastMessage.put("id", msg.getId());
                lastMessage.put("content", msg.getContent());
                lastMessage.put("contentType", msg.getContentType());
                lastMessage.put("senderId", msg.getSenderId());
                lastMessage.put("createdAt", msg.getCreatedAt());
                item.put("lastMessage", lastMessage);
            }

            // 返回参与者详情（包含昵称和头像）
            List<Map<String, Object>> participants = new ArrayList<>();
            for (String pid : conv.getParticipants()) {
                Map<String, Object> p = new HashMap<>();
                p.put("userId", pid);
                User u = conversationService.getUserById(pid);
                if (u != null) {
                    p.put("nickname", u.getNickname() != null ? u.getNickname() : u.getUsername());
                    p.put("avatar", u.getAvatar());
                } else {
                    p.put("nickname", "用户");
                    p.put("avatar", "");
                }
                participants.add(p);
            }
            item.put("participants", participants);

            list.add(item);
        }

        Map<String, Object> result = new HashMap<>();
        result.put("conversations", list);
        result.put("totalCount", totalCount);

        return ResponseEntity.ok(result);
    }

    /**
     * 获取会话详情
     *
     * GET /api/chat/conversation/:id
     *
     * @param conversationId 会话 ID
     * @return 会话详情
     */
    @GetMapping("/conversation/{conversationId}")
    public ResponseEntity<Map<String, Object>> getConversation(
            @PathVariable String conversationId,
            @RequestAttribute String userId) {

        return conversationService.getById(conversationId)
            .<ResponseEntity<Map<String, Object>>>map(conv -> {
                // 检查用户是否是参与者
                if (!conv.getParticipants().contains(userId)) {
                    Map<String, Object> error = new HashMap<>();
                    error.put("code", ErrorCode.FORBIDDEN.getCode());
                    error.put("message", ErrorCode.FORBIDDEN.getMessage());
                    return ResponseEntity.status(403).body(error);
                }

                Map<String, Object> result = new HashMap<>();
                result.put("id", conv.getId());
                result.put("type", conv.getType());
                result.put("name", conv.getName());
                result.put("avatar", conv.getAvatar());
                result.put("participants", conv.getParticipants());
                result.put("pinned", redisPinService.isPinned(userId, conversationId));
                result.put("muted", redisMuteService.isMuted(userId, conversationId));
                result.put("createdAt", conv.getCreatedAt());

                return ResponseEntity.ok(result);
            })
            .orElse(ResponseEntity.notFound().build());
    }

    /**
     * 获取历史消息
     *
     * GET /api/chat/conversation/:id/messages
     *
     * @param conversationId 会话 ID
     * @param cursor        游标 (时间戳)
     * @param limit         数量限制
     * @return 消息列表和游标
     */
    @GetMapping("/conversation/{conversationId}/messages")
    public ResponseEntity<Map<String, Object>> getMessages(
            @PathVariable String conversationId,
            @RequestParam(required = false) Long cursor,
            @RequestParam(defaultValue = "50") int limit,
            @RequestAttribute String userId) {

        // 验证用户权限
        List<String> participants = conversationService.getParticipants(conversationId);
        if (!participants.contains(userId)) {
            Map<String, Object> error = new HashMap<>();
            error.put("code", ErrorCode.FORBIDDEN.getCode());
            error.put("message", ErrorCode.FORBIDDEN.getMessage());
            return ResponseEntity.status(403).body(error);
        }

        // 获取消息
        Map<String, Object> result = messageService.getHistoryWithCursor(
            conversationId, cursor, limit);

        return ResponseEntity.ok(result);
    }

    /**
     * 标记消息已读
     *
     * POST /api/chat/conversation/:id/read
     *
     * @param conversationId 会话 ID
     * @param userId         用户 ID
     * @return 结果
     */
    @PostMapping("/conversation/{conversationId}/read")
    public ResponseEntity<Map<String, Object>> markAsRead(
            @PathVariable String conversationId,
            @RequestAttribute String userId) {

        redisUnreadService.setUserReadTime(userId, conversationId, System.currentTimeMillis());

        return ResponseEntity.ok(new HashMap<String, Object>() {{
                put("success", true);
            }});
    }

    /**
     * 切换置顶
     *
     * POST /api/chat/conversation/:id/pin
     *
     * @param conversationId 会话 ID
     * @param userId          用户 ID
     * @return 结果
     */
    @PostMapping("/conversation/{conversationId}/pin")
    public ResponseEntity<Map<String, Object>> togglePin(
            @PathVariable String conversationId,
            @RequestAttribute String userId) {

        boolean pinned = redisPinService.togglePin(userId, conversationId);

        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("pinned", pinned);
        return ResponseEntity.ok(result);
    }

    /**
     * 切换静音
     *
     * POST /api/chat/conversation/:id/mute
     *
     * @param conversationId 会话 ID
     * @param userId          用户 ID
     * @return 结果
     */
    @PostMapping("/conversation/{conversationId}/mute")
    public ResponseEntity<Map<String, Object>> toggleMute(
            @PathVariable String conversationId,
            @RequestAttribute String userId) {

        boolean muted = redisMuteService.toggleMute(userId, conversationId);

        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("muted", muted);
        return ResponseEntity.ok(result);
    }

    /**
     * 创建私聊会话
     *
     * POST /api/chat/private
     *
     * @param request 请求体 {friendId}
     * @param userId  当前用户 ID
     * @return 会话
     */
    @PostMapping("/private")
    public ResponseEntity<Map<String, Object>> createPrivate(
            @RequestBody Map<String, String> request,
            @RequestAttribute String userId) {

        String friendId = request.get("friendId");

        Conversation conversation = conversationService.getOrCreatePrivate(userId, friendId);

        Map<String, Object> result = new HashMap<>();
        result.put("id", conversation.getId());
        result.put("type", conversation.getType());
        result.put("name", conversation.getName());
        result.put("avatar", conversation.getAvatar());

        return ResponseEntity.ok(result);
    }
}