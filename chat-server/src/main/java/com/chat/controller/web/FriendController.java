package com.chat.controller.web;

import com.chat.common.ErrorCode;
import com.chat.document.Conversation;
import com.chat.document.Friend;
import com.chat.document.User;
import com.chat.repository.FriendRepository;
import com.chat.repository.UserRepository;
import com.chat.service.ConversationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * 好友 Controller
 *
 * 提供好友管理 HTTP 接口
 */
@RestController
@RequestMapping("/api/friend")
public class FriendController {

    @Autowired
    private FriendRepository friendRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ConversationService conversationService;

    /**
     * 搜索用户
     *
     * GET /api/friend/search?keyword=xxx
     *
     * @param keyword 关键词
     * @param userId 当前用户 ID
     * @return 用户列表
     */
    @GetMapping("/search")
    public ResponseEntity<Map<String, Object>> search(
            @RequestParam String keyword,
            @RequestAttribute String userId) {

        if (keyword == null || keyword.trim().isEmpty()) {
            return error(ErrorCode.INVALID_PARAM, "关键词不能为空");
        }

        List<User> users = userRepository.findByUsernameContainingOrNicknameContaining(
            keyword.trim(), keyword.trim(), PageRequest.of(0, 50)).getContent();

        List<Map<String, Object>> results = new ArrayList<>();
        for (User user : users) {
            if (!user.getId().equals(userId)) {
                Map<String, Object> item = new HashMap<>();
                item.put("userId", user.getId());
                item.put("username", user.getUsername());
                item.put("nickname", user.getNickname());
                item.put("avatar", user.getAvatar());

                Optional<Friend> friend = friendRepository.isFriend(userId, user.getId());
                item.put("isFriend", friend.isPresent());

                Optional<Friend> pending = friendRepository.findPendingRequest(userId, user.getId());
                item.put("hasPendingRequest", pending.isPresent());

                results.add(item);
            }
        }

        Map<String, Object> result = new HashMap<>();
        result.put("users", results);

        return ResponseEntity.ok(result);
    }

    /**
     * 发送好友请求
     *
     * POST /api/friend/request
     *
     * @param request 请求体 {userId}
     * @param userId 当前用户 ID
     * @return 结果
     */
    @PostMapping("/request")
    public ResponseEntity<Map<String, Object>> sendRequest(
            @RequestBody Map<String, String> request,
            @RequestAttribute String userId) {

        String targetUserId = request.get("userId");
        if (targetUserId == null || targetUserId.trim().isEmpty()) {
            return error(ErrorCode.INVALID_PARAM, "用户 ID 不能为空");
        }

        if (targetUserId.equals(userId)) {
            return error(ErrorCode.INVALID_PARAM, "不能添加自己为好友");
        }

        Optional<User> targetUserOpt = userRepository.findById(targetUserId);
        if (!targetUserOpt.isPresent()) {
            return error(ErrorCode.USER_NOT_FOUND, "用户不存在");
        }

        Optional<Friend> existingFriend = friendRepository.findByUserIdOrRelatedUserId(userId, targetUserId);
        if (existingFriend.isPresent() && "accepted".equals(existingFriend.get().getStatus())) {
            return error(ErrorCode.FRIEND_REQUEST_EXISTS, "已经是好友");
        }

        Optional<Friend> existingRequest = friendRepository.findByUserIdOrRelatedUserId(userId, targetUserId);
        if (existingRequest.isPresent() && "pending".equals(existingRequest.get().getStatus())) {
            return error(ErrorCode.FRIEND_REQUEST_EXISTS, "好友请求已发送");
        }

        Friend friend = Friend.builder()
            .userId(userId)
            .friendId(targetUserId)
            .relatedUserId(targetUserId)
            .status("pending")
            .source("search")
            .createdAt(System.currentTimeMillis())
            .build();

        friendRepository.save(friend);

        return ResponseEntity.ok(new HashMap<String, Object>() {{
            put("success", true);
            put("requestId", friend.getId());
        }});
    }

    /**
     * 接受好友请求
     *
     * POST /api/friend/request/:requestId/accept
     *
     * @param requestId 请求 ID
     * @param userId 当前用户 ID
     * @return 结果
     */
    @PostMapping("/request/{requestId}/accept")
    public ResponseEntity<Map<String, Object>> acceptRequest(
            @PathVariable String requestId,
            @RequestAttribute String userId) {

        Optional<Friend> requestOpt = friendRepository.findById(requestId);
        if (!requestOpt.isPresent()) {
            return error(ErrorCode.FRIEND_NOT_FOUND, "好友请求不存在");
        }

        Friend request = requestOpt.get();

        String senderId = request.getUserId();
        boolean isReceiver = userId.equals(request.getFriendId()) || userId.equals(request.getRelatedUserId());

        if (!isReceiver) {
            return error(ErrorCode.FORBIDDEN, "无权限操作");
        }

        if (!"pending".equals(request.getStatus())) {
            return error(ErrorCode.INVALID_PARAM, "请求已被处理");
        }

        request.setStatus("accepted");
        request.setUpdatedAt(System.currentTimeMillis());

        if (request.getRelatedUserId() == null) {
            request.setRelatedUserId(senderId);
        }

        friendRepository.save(request);

        Conversation conv = conversationService.getOrCreatePrivate(userId, senderId);

        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        if (conv != null) {
            result.put("conversationId", conv.getId());
        }

        return ResponseEntity.ok(result);
    }

    /**
     * 拒绝好友请求
     *
     * POST /api/friend/request/:requestId/reject
     *
     * @param requestId 请求 ID
     * @param userId 当前用户 ID
     * @return 结果
     */
    @PostMapping("/request/{requestId}/reject")
    public ResponseEntity<Map<String, Object>> rejectRequest(
            @PathVariable String requestId,
            @RequestAttribute String userId) {

        Optional<Friend> requestOpt = friendRepository.findById(requestId);
        if (!requestOpt.isPresent()) {
            return error(ErrorCode.FRIEND_NOT_FOUND, "好友请求不存在");
        }

        Friend request = requestOpt.get();

        if (!request.getFriendId().equals(userId)) {
            return error(ErrorCode.FORBIDDEN, "无权限操作");
        }

        if (!"pending".equals(request.getStatus())) {
            return error(ErrorCode.INVALID_PARAM, "请求已被处理");
        }

        request.setStatus("rejected");
        request.setUpdatedAt(System.currentTimeMillis());
        friendRepository.save(request);

        return ResponseEntity.ok(new HashMap<String, Object>() {{
            put("success", true);
        }});
    }

    /**
     * 删除好友
     *
     * DELETE /api/friend/:friendId
     *
     * @param friendId 好友 ID
     * @param userId 当前用户 ID
     * @return 结果
     */
    @DeleteMapping("/{friendId}")
    public ResponseEntity<Map<String, Object>> removeFriend(
            @PathVariable String friendId,
            @RequestAttribute String userId) {

        Optional<Friend> friendOpt = friendRepository.findByUserIdOrRelatedUserId(userId, friendId);
        if (friendOpt.isPresent()) {
            friendRepository.delete(friendOpt.get());
        }

        return ResponseEntity.ok(new HashMap<String, Object>() {{
            put("success", true);
        }});
    }

    /**
     * 获取好友列表
     *
     * GET /api/friend/list
     *
     * @param userId 当前用户 ID
     * @return 好友列表
     */
    @GetMapping("/list")
    public ResponseEntity<Map<String, Object>> getList(@RequestAttribute String userId) {
        List<Friend> friendsAsRequester = friendRepository.findAcceptedFriends(userId);
        List<Friend> friendsAsReceiver = friendRepository.findAcceptedFriendsByRelatedUserId(userId);

        Set<String> addedUserIds = new HashSet<>();
        List<Map<String, Object>> list = new ArrayList<>();

        for (Friend friend : friendsAsRequester) {
            String friendUserId = friend.getFriendId() != null ? friend.getFriendId() : friend.getRelatedUserId();
            if (addedUserIds.contains(friendUserId)) continue;
            addedUserIds.add(friendUserId);

            Optional<User> userOpt = userRepository.findById(friendUserId);
            if (userOpt.isPresent()) {
                User user = userOpt.get();
                Map<String, Object> item = new HashMap<>();
                item.put("userId", user.getId());
                item.put("username", user.getUsername());
                item.put("nickname", user.getNickname());
                item.put("avatar", user.getAvatar());
                item.put("online", user.getOnline());
                item.put("remark", friend.getRemark());
                item.put("addedAt", friend.getCreatedAt());
                list.add(item);
            }
        }

        for (Friend friend : friendsAsReceiver) {
            String friendUserId = friend.getUserId();
            if (addedUserIds.contains(friendUserId)) continue;
            addedUserIds.add(friendUserId);

            Optional<User> userOpt = userRepository.findById(friendUserId);
            if (userOpt.isPresent()) {
                User user = userOpt.get();
                Map<String, Object> item = new HashMap<>();
                item.put("userId", user.getId());
                item.put("username", user.getUsername());
                item.put("nickname", user.getNickname());
                item.put("avatar", user.getAvatar());
                item.put("online", user.getOnline());
                item.put("remark", friend.getRemark());
                item.put("addedAt", friend.getCreatedAt());
                list.add(item);
            }
        }

        Map<String, Object> result = new HashMap<>();
        result.put("friends", list);

        return ResponseEntity.ok(result);
    }

    /**
     * 获取好友请求
     *
     * GET /api/friend/requests
     *
     * @param userId 当前用户 ID
     * @return 好友请求列表
     */
    @GetMapping("/requests")
    public ResponseEntity<Map<String, Object>> getRequests(@RequestAttribute String userId) {
        List<Friend> receivedPending = friendRepository.findByRelatedUserIdAndStatusOrderByCreatedAtDesc(userId, "pending");
        List<Friend> sentPending = friendRepository.findByUserIdAndStatusOrderByCreatedAtDesc(userId, "pending");
        List<Friend> receivedProcessed = friendRepository.findByRelatedUserIdAndStatusNotPending(userId);
        List<Friend> sentProcessed = friendRepository.findByUserIdAndStatusNotPending(userId);

        List<Map<String, Object>> received = new ArrayList<>();
        for (Friend request : receivedPending) {
            Optional<User> userOpt = userRepository.findById(request.getUserId());
            if (userOpt.isPresent()) {
                User user = userOpt.get();
                Map<String, Object> item = new HashMap<>();
                item.put("requestId", request.getId());
                item.put("userId", user.getId());
                item.put("username", user.getUsername());
                item.put("nickname", user.getNickname());
                item.put("avatar", user.getAvatar());
                item.put("createdAt", request.getCreatedAt());
                item.put("status", request.getStatus());
                received.add(item);
            }
        }

        for (Friend request : receivedProcessed) {
            Optional<User> userOpt = userRepository.findById(request.getUserId());
            if (userOpt.isPresent()) {
                User user = userOpt.get();
                Map<String, Object> item = new HashMap<>();
                item.put("requestId", request.getId());
                item.put("userId", user.getId());
                item.put("username", user.getUsername());
                item.put("nickname", user.getNickname());
                item.put("avatar", user.getAvatar());
                item.put("createdAt", request.getCreatedAt());
                item.put("status", request.getStatus());
                received.add(item);
            }
        }

        List<Map<String, Object>> sent = new ArrayList<>();
        for (Friend request : sentPending) {
            Optional<User> userOpt = userRepository.findById(request.getFriendId());
            if (userOpt.isPresent()) {
                User user = userOpt.get();
                Map<String, Object> item = new HashMap<>();
                item.put("requestId", request.getId());
                item.put("userId", user.getId());
                item.put("username", user.getUsername());
                item.put("nickname", user.getNickname());
                item.put("avatar", user.getAvatar());
                item.put("createdAt", request.getCreatedAt());
                item.put("status", request.getStatus());
                sent.add(item);
            }
        }

        for (Friend request : sentProcessed) {
            Optional<User> userOpt = userRepository.findById(request.getFriendId());
            if (userOpt.isPresent()) {
                User user = userOpt.get();
                Map<String, Object> item = new HashMap<>();
                item.put("requestId", request.getId());
                item.put("userId", user.getId());
                item.put("username", user.getUsername());
                item.put("nickname", user.getNickname());
                item.put("avatar", user.getAvatar());
                item.put("createdAt", request.getCreatedAt());
                item.put("status", request.getStatus());
                sent.add(item);
            }
        }

        Map<String, Object> result = new HashMap<>();
        result.put("received", received);
        result.put("sent", sent);

        return ResponseEntity.ok(result);
    }

    private ResponseEntity<Map<String, Object>> error(ErrorCode code, String message) {
        Map<String, Object> error = new HashMap<>();
        error.put("code", code.getCode());
        error.put("message", message);
        return ResponseEntity.badRequest().body(error);
    }
}