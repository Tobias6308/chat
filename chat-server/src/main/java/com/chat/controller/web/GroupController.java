package com.chat.controller.web;

import com.chat.common.ErrorCode;
import com.chat.document.Conversation;
import com.chat.document.Group;
import com.chat.document.User;
import com.chat.repository.ConversationRepository;
import com.chat.repository.GroupRepository;
import com.chat.repository.UserRepository;
import com.chat.service.RedisMuteService;
import com.chat.service.RedisPinService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * 群组 Controller
 *
 * 提供群组管理 HTTP 接口
 */
@RestController
@RequestMapping("/api/group")
public class GroupController {

    @Autowired
    private GroupRepository groupRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ConversationRepository conversationRepository;

    @Autowired
    private RedisPinService redisPinService;

    @Autowired
    private RedisMuteService redisMuteService;

    /**
     * 创建群组
     *
     * POST /api/group
     *
     * @param request 请求体 {name, description, memberIds}
     * @param userId 当前用户 ID
     * @return 群组信息
     */
    @PostMapping
    public ResponseEntity<Map<String, Object>> create(
            @RequestBody Map<String, Object> request,
            @RequestAttribute String userId) {

        String name = (String) request.get("name");
        String description = (String) request.get("description");
        List<String> memberIds = (List<String>) request.get("memberIds");

        if (name == null || name.trim().isEmpty()) {
            return error(ErrorCode.INVALID_PARAM, "群名称不能为空");
        }

        List<Group.GroupMember> members = new ArrayList<>();

        Optional<User> ownerOpt = userRepository.findById(userId);
        if (ownerOpt.isPresent()) {
            User owner = ownerOpt.get();
            members.add(Group.GroupMember.builder()
                .userId(userId)
                .nickname(owner.getNickname())
                .avatar(owner.getAvatar())
                .role("owner")
                .joinedAt(System.currentTimeMillis())
                .build());
        }

        if (memberIds != null) {
            for (String memberId : memberIds) {
                if (!memberId.equals(userId)) {
                    Optional<User> userOpt = userRepository.findById(memberId);
                    if (userOpt.isPresent()) {
                        User user = userOpt.get();
                        members.add(Group.GroupMember.builder()
                            .userId(memberId)
                            .nickname(user.getNickname())
                            .avatar(user.getAvatar())
                            .role("member")
                            .joinedAt(System.currentTimeMillis())
                            .build());
                    }
                }
            }
        }

        Group group = Group.builder()
            .name(name.trim())
            .description(description)
            .ownerId(userId)
            .members(members)
            .memberCount(members.size())
            .type("group")
            .allMuted(false)
            .createdAt(System.currentTimeMillis())
            .build();

        group = groupRepository.save(group);

        List<String> participantIds = new ArrayList<>();
        for (Group.GroupMember member : members) {
            participantIds.add(member.getUserId());
        }
        
        Conversation conversation = Conversation.builder()
            .id(group.getId())
            .type("group")
            .relateId(group.getId())
            .name(group.getName())
            .avatar(group.getAvatar())
            .participants(participantIds)
            .muted(false)
            .createdAt(group.getCreatedAt())
            .updatedAt(System.currentTimeMillis())
            .build();
        conversationRepository.save(conversation);

        Map<String, Object> result = new HashMap<>();
        result.put("id", group.getId());
        result.put("name", group.getName());
        result.put("avatar", group.getAvatar());
        result.put("description", group.getDescription());
        result.put("ownerId", group.getOwnerId());
        result.put("memberCount", group.getMemberCount());
        result.put("createdAt", group.getCreatedAt());

        return ResponseEntity.ok(result);
    }

    /**
     * 获取群组列表
     *
     * GET /api/group/list
     *
     * @param userId 当前用户 ID
     * @return 群组列表
     */
    @GetMapping("/list")
    public ResponseEntity<Map<String, Object>> list(@RequestAttribute String userId) {
        List<Group> groups = groupRepository.findByUserId(userId);

        List<Map<String, Object>> list = new ArrayList<>();
        for (Group group : groups) {
            Map<String, Object> item = new HashMap<>();
            item.put("id", group.getId());
            item.put("name", group.getName());
            item.put("avatar", group.getAvatar());
            item.put("description", group.getDescription());
            item.put("ownerId", group.getOwnerId());
            item.put("memberCount", group.getMemberCount());
            item.put("createdAt", group.getCreatedAt());

            Optional<Conversation> convOpt = conversationRepository.findByRelateId(group.getId());
            if (convOpt.isPresent()) {
                item.put("conversationId", convOpt.get().getId());
            } else {
                item.put("conversationId", group.getId());
            }

            String myRole = "member";
            if (group.getOwnerId().equals(userId)) {
                myRole = "owner";
            } else if (group.getMembers() != null) {
                for (Group.GroupMember member : group.getMembers()) {
                    if (member.getUserId().equals(userId)) {
                        myRole = member.getRole();
                        break;
                    }
                }
            }
            item.put("myRole", myRole);

            if (convOpt.isPresent()) {
                Conversation conv = convOpt.get();
                item.put("pinned", redisPinService.isPinned(userId, conv.getId()));
                item.put("muted", redisMuteService.isMuted(userId, conv.getId()));
                item.put("updatedAt", conv.getUpdatedAt());
            } else {
                item.put("pinned", false);
                item.put("muted", false);
                item.put("updatedAt", group.getCreatedAt());
            }

            list.add(item);
        }

        Map<String, Object> result = new HashMap<>();
        result.put("groups", list);

        return ResponseEntity.ok(result);
    }

    /**
     * 获取群组详情
     *
     * GET /api/group/:id
     *
     * @param groupId 群组 ID
     * @param userId 当前用户 ID
     * @return 群组详情
     */
    @GetMapping("/{groupId}")
    public ResponseEntity<Map<String, Object>> get(
            @PathVariable String groupId,
            @RequestAttribute String userId) {

        Optional<Group> groupOpt = groupRepository.findById(groupId);
        if (!groupOpt.isPresent()) {
            return error(ErrorCode.GROUP_NOT_FOUND, "群组不存在");
        }

        Group group = groupOpt.get();

        boolean isMember = group.getMembers().stream()
            .anyMatch(m -> m.getUserId().equals(userId));
        if (!isMember) {
            return error(ErrorCode.FORBIDDEN, "无权限访问");
        }

        Map<String, Object> result = new HashMap<>();
        result.put("id", group.getId());
        result.put("name", group.getName());
        result.put("avatar", group.getAvatar());
        result.put("description", group.getDescription());
        result.put("ownerId", group.getOwnerId());
        result.put("memberCount", group.getMemberCount());
        result.put("type", group.getType());
        result.put("allMuted", group.getAllMuted());
        result.put("createdAt", group.getCreatedAt());

        List<Map<String, Object>> members = new ArrayList<>();
        for (Group.GroupMember member : group.getMembers()) {
            Map<String, Object> m = new HashMap<>();
            m.put("userId", member.getUserId());
            m.put("nickname", member.getNickname());
            m.put("avatar", member.getAvatar());
            m.put("role", member.getRole());
            m.put("joinedAt", member.getJoinedAt());
            m.put("muted", member.getMuted());
            members.add(m);
        }
        result.put("members", members);

        return ResponseEntity.ok(result);
    }

    /**
     * 获取群成员
     *
     * GET /api/group/:id/members
     *
     * @param groupId 群组 ID
     * @param userId 当前用户 ID
     * @return 成员列表
     */
    @GetMapping("/{groupId}/members")
    public ResponseEntity<Map<String, Object>> getMembers(
            @PathVariable String groupId,
            @RequestAttribute String userId) {

        Optional<Group> groupOpt = groupRepository.findById(groupId);
        if (!groupOpt.isPresent()) {
            return error(ErrorCode.GROUP_NOT_FOUND, "群组不存在");
        }

        Group group = groupOpt.get();

        boolean isMember = group.getMembers().stream()
            .anyMatch(m -> m.getUserId().equals(userId));
        if (!isMember) {
            return error(ErrorCode.FORBIDDEN, "无权限访问");
        }

        List<Map<String, Object>> members = new ArrayList<>();
        for (Group.GroupMember member : group.getMembers()) {
            Map<String, Object> m = new HashMap<>();
            m.put("userId", member.getUserId());
            m.put("nickname", member.getNickname());
            m.put("avatar", member.getAvatar());
            m.put("role", member.getRole());
            m.put("joinedAt", member.getJoinedAt());
            m.put("muted", member.getMuted());
            members.add(m);
        }

        Map<String, Object> result = new HashMap<>();
        result.put("members", members);

        return ResponseEntity.ok(result);
    }

    /**
     * 添加群成员
     *
     * POST /api/group/:id/members
     *
     * @param groupId 群组 ID
     * @param request 请求体 {memberIds}
     * @param userId 当前用户 ID
     * @return 结果
     */
    @PostMapping("/{groupId}/members")
    public ResponseEntity<Map<String, Object>> addMembers(
            @PathVariable String groupId,
            @RequestBody Map<String, Object> request,
            @RequestAttribute String userId) {

        Optional<Group> groupOpt = groupRepository.findById(groupId);
        if (!groupOpt.isPresent()) {
            return error(ErrorCode.GROUP_NOT_FOUND, "群组不存在");
        }

        Group group = groupOpt.get();

        boolean isOwner = group.getOwnerId().equals(userId);
        boolean isAdmin = group.getMembers().stream()
            .anyMatch(m -> m.getUserId().equals(userId) && "admin".equals(m.getRole()));
        if (!isOwner && !isAdmin) {
            return error(ErrorCode.FORBIDDEN, "无权限操作");
        }

        List<String> memberIds = (List<String>) request.get("memberIds");
        if (memberIds == null || memberIds.isEmpty()) {
            return error(ErrorCode.INVALID_PARAM, "成员 ID 不能为空");
        }

        List<Group.GroupMember> newMembers = new ArrayList<>();
        Set<String> existingMemberIds = new HashSet<>();
        for (Group.GroupMember m : group.getMembers()) {
            existingMemberIds.add(m.getUserId());
        }

        for (String memberId : memberIds) {
            if (!existingMemberIds.contains(memberId)) {
                Optional<User> userOpt = userRepository.findById(memberId);
                if (userOpt.isPresent()) {
                    User user = userOpt.get();
                    newMembers.add(Group.GroupMember.builder()
                        .userId(memberId)
                        .nickname(user.getNickname())
                        .avatar(user.getAvatar())
                        .role("member")
                        .joinedAt(System.currentTimeMillis())
                        .build());
                }
            }
        }

        if (!newMembers.isEmpty()) {
            group.getMembers().addAll(newMembers);
            group.setMemberCount(group.getMembers().size());
            group.setUpdatedAt(System.currentTimeMillis());
            groupRepository.save(group);

            Optional<Conversation> convOpt = conversationRepository.findByRelateIdAndType(groupId, "group");
            if (convOpt.isPresent()) {
                Conversation conv = convOpt.get();
                List<String> participantIds = new ArrayList<>();
                for (Group.GroupMember m : group.getMembers()) {
                    participantIds.add(m.getUserId());
                }
                conv.setParticipants(participantIds);
                conv.setUpdatedAt(System.currentTimeMillis());
                conversationRepository.save(conv);
            }
        }

        return ResponseEntity.ok(new HashMap<String, Object>() {{
            put("success", true);
        }});
    }

    /**
     * 移除群成员
     *
     * DELETE /api/group/:id/members/:memberId
     *
     * @param groupId 群组 ID
     * @param memberId 成员 ID
     * @param userId 当前用户 ID
     * @return 结果
     */
    @DeleteMapping("/{groupId}/members/{memberId}")
    public ResponseEntity<Map<String, Object>> removeMember(
            @PathVariable String groupId,
            @PathVariable String memberId,
            @RequestAttribute String userId) {

        Optional<Group> groupOpt = groupRepository.findById(groupId);
        if (!groupOpt.isPresent()) {
            return error(ErrorCode.GROUP_NOT_FOUND, "群组不存在");
        }

        Group group = groupOpt.get();

        boolean isOwner = group.getOwnerId().equals(userId);
        boolean isSelf = memberId.equals(userId);

        if (!isOwner && !isSelf) {
            return error(ErrorCode.FORBIDDEN, "无权限操作");
        }

        if (memberId.equals(group.getOwnerId())) {
            return error(ErrorCode.INVALID_PARAM, "不能移除群主");
        }

        group.getMembers().removeIf(m -> m.getUserId().equals(memberId));
        group.setMemberCount(group.getMembers().size());
        group.setUpdatedAt(System.currentTimeMillis());
        groupRepository.save(group);

        Optional<Conversation> convOpt = conversationRepository.findByRelateIdAndType(groupId, "group");
        if (convOpt.isPresent()) {
            Conversation conv = convOpt.get();
            List<String> participantIds = new ArrayList<>();
            for (Group.GroupMember m : group.getMembers()) {
                participantIds.add(m.getUserId());
            }
            conv.setParticipants(participantIds);
            conv.setUpdatedAt(System.currentTimeMillis());
            conversationRepository.save(conv);
        }

        return ResponseEntity.ok(new HashMap<String, Object>() {{
            put("success", true);
        }});
    }

    /**
     * 设置成员角色
     *
     * PUT /api/group/:id/members/:memberId/role
     *
     * @param groupId 群组 ID
     * @param memberId 成员 ID
     * @param request 请求体 {role: 'admin'|'member'}
     * @param userId 当前用户 ID
     * @return 结果
     */
    @PutMapping("/{groupId}/members/{memberId}/role")
    public ResponseEntity<Map<String, Object>> setMemberRole(
            @PathVariable String groupId,
            @PathVariable String memberId,
            @RequestBody Map<String, String> request,
            @RequestAttribute String userId) {

        Optional<Group> groupOpt = groupRepository.findById(groupId);
        if (!groupOpt.isPresent()) {
            return error(ErrorCode.GROUP_NOT_FOUND, "群组不存在");
        }

        Group group = groupOpt.get();

        if (!group.getOwnerId().equals(userId)) {
            return error(ErrorCode.FORBIDDEN, "无权限操作");
        }

        String role = request.get("role");
        if (role == null || (!role.equals("admin") && !role.equals("member"))) {
            return error(ErrorCode.INVALID_PARAM, "角色无效");
        }

        for (Group.GroupMember member : group.getMembers()) {
            if (member.getUserId().equals(memberId)) {
                member.setRole(role);
                break;
            }
        }

        group.setUpdatedAt(System.currentTimeMillis());
        groupRepository.save(group);

        return ResponseEntity.ok(new HashMap<String, Object>() {{
            put("success", true);
        }});
    }

    /**
     * 禁言/解除禁言成员
     *
     * PUT /api/group/:id/members/:memberId/mute
     *
     * @param groupId 群组 ID
     * @param memberId 成员 ID
     * @param request 请求体 {muted: boolean}
     * @param userId 当前用户 ID
     * @return 结果
     */
    @PutMapping("/{groupId}/members/{memberId}/mute")
    public ResponseEntity<Map<String, Object>> setMemberMute(
            @PathVariable String groupId,
            @PathVariable String memberId,
            @RequestBody Map<String, Boolean> request,
            @RequestAttribute String userId) {

        Optional<Group> groupOpt = groupRepository.findById(groupId);
        if (!groupOpt.isPresent()) {
            return error(ErrorCode.GROUP_NOT_FOUND, "群组不存在");
        }

        Group group = groupOpt.get();

        boolean isOwner = group.getOwnerId().equals(userId);
        boolean isAdmin = group.getMembers().stream()
            .anyMatch(m -> m.getUserId().equals(userId) && "admin".equals(m.getRole()));

        if (!isOwner && !isAdmin) {
            return error(ErrorCode.FORBIDDEN, "无权限操作");
        }

        Boolean muted = request.get("muted");
        if (muted == null) {
            return error(ErrorCode.INVALID_PARAM, "参数无效");
        }

        for (Group.GroupMember member : group.getMembers()) {
            if (member.getUserId().equals(memberId)) {
                member.setMuted(muted);
                break;
            }
        }

        group.setUpdatedAt(System.currentTimeMillis());
        groupRepository.save(group);

        return ResponseEntity.ok(new HashMap<String, Object>() {{
            put("success", true);
        }});
    }

    /**
     * 更新群组
     *
     * PUT /api/group/:id
     *
     * @param groupId 群组 ID
     * @param request 请求体 {name, description}
     * @param userId 当前用户 ID
     * @return 结果
     */
    @PutMapping("/{groupId}")
    public ResponseEntity<Map<String, Object>> update(
            @PathVariable String groupId,
            @RequestBody Map<String, Object> request,
            @RequestAttribute String userId) {

        Optional<Group> groupOpt = groupRepository.findById(groupId);
        if (!groupOpt.isPresent()) {
            return error(ErrorCode.GROUP_NOT_FOUND, "群组不存在");
        }

        Group group = groupOpt.get();

        if (!group.getOwnerId().equals(userId)) {
            return error(ErrorCode.FORBIDDEN, "无权限操作");
        }

        String name = (String) request.get("name");
        String description = (String) request.get("description");
        String avatar = (String) request.get("avatar");

        if (name != null && !name.trim().isEmpty()) {
            group.setName(name.trim());
        }
        if (description != null) {
            group.setDescription(description);
        }
        if (avatar != null) {
            group.setAvatar(avatar);
        }

        group.setUpdatedAt(System.currentTimeMillis());
        groupRepository.save(group);

        // 通过 relateId 查找群组会话并更新
        Optional<Conversation> convOpt = conversationRepository.findByRelateId(groupId);
        if (convOpt.isPresent()) {
            Conversation conv = convOpt.get();
            if (name != null && !name.trim().isEmpty()) {
                conv.setName(name.trim());
            }
            if (avatar != null) {
                conv.setAvatar(avatar);
            }
            conv.setUpdatedAt(System.currentTimeMillis());
            conversationRepository.save(conv);
        }

        Map<String, Object> result = new HashMap<>();
        result.put("id", group.getId());
        result.put("name", group.getName());
        result.put("description", group.getDescription());
        result.put("avatar", group.getAvatar());

        return ResponseEntity.ok(result);
    }

    /**
     * 解散群组
     *
     * DELETE /api/group/:id
     *
     * @param groupId 群组 ID
     * @param userId 当前用户 ID
     * @return 结果
     */
    @DeleteMapping("/{groupId}")
    public ResponseEntity<Map<String, Object>> delete(
            @PathVariable String groupId,
            @RequestAttribute String userId) {

        Optional<Group> groupOpt = groupRepository.findById(groupId);
        if (!groupOpt.isPresent()) {
            return error(ErrorCode.GROUP_NOT_FOUND, "群组不存在");
        }

        Group group = groupOpt.get();

        if (!group.getOwnerId().equals(userId)) {
            return error(ErrorCode.FORBIDDEN, "无权限操作");
        }

        groupRepository.delete(group);

        conversationRepository.deleteById(groupId);

        return ResponseEntity.ok(new HashMap<String, Object>() {{
            put("success", true);
        }});
    }

    /**
     * 切换群组置顶
     *
     * POST /api/group/:id/pin
     */
    @PostMapping("/{groupId}/pin")
    public ResponseEntity<Map<String, Object>> togglePin(
            @PathVariable String groupId,
            @RequestAttribute String userId) {

        Optional<Conversation> convOpt = conversationRepository.findByRelateId(groupId);
        if (!convOpt.isPresent()) {
            return error(ErrorCode.CONVERSATION_NOT_FOUND, "会话不存在");
        }

        boolean pinned = redisPinService.togglePin(userId, convOpt.get().getId());

        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("pinned", pinned);
        return ResponseEntity.ok(result);
    }

    /**
     * 切换群组静音
     *
     * POST /api/group/:id/mute
     */
    @PostMapping("/{groupId}/mute")
    public ResponseEntity<Map<String, Object>> toggleMute(
            @PathVariable String groupId,
            @RequestAttribute String userId) {

        Optional<Conversation> convOpt = conversationRepository.findByRelateId(groupId);
        if (!convOpt.isPresent()) {
            return error(ErrorCode.CONVERSATION_NOT_FOUND, "会话不存在");
        }

        boolean muted = redisMuteService.toggleMute(userId, convOpt.get().getId());

        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("muted", muted);
        return ResponseEntity.ok(result);
    }

    private ResponseEntity<Map<String, Object>> error(ErrorCode code, String message) {
        Map<String, Object> error = new HashMap<>();
        error.put("code", code.getCode());
        error.put("message", message);
        return ResponseEntity.badRequest().body(error);
    }
}