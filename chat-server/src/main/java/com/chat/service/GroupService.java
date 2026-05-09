package com.chat.service;

import com.chat.document.Group;
import com.chat.repository.GroupRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class GroupService {
    
    private static final Logger logger = LoggerFactory.getLogger(GroupService.class);
    
    @Autowired
    private GroupRepository groupRepository;
    
    /**
     * 创建群组
     * 
     * @param name        群名称
     * @param description 群描述
     * @param creatorId   创建者 ID
     * @return 群组实体
     */
    public Group create(String name, String description, String creatorId) {
        Group.GroupMember owner = Group.GroupMember.builder()
            .userId(creatorId)
            .nickname("我")
            .role("owner")
            .joinedAt(System.currentTimeMillis())
            .muted(false)
            .build();
        
        Group group = Group.builder()
            .id(generateId())
            .name(name)
            .description(description)
            .ownerId(creatorId)
            .members(Arrays.asList(owner))
            .memberCount(1)
            .type("group")
            .allMuted(false)
            .createdAt(System.currentTimeMillis())
            .build();
        
        return groupRepository.save(group);
    }
    
    /**
     * 获取群组
     * 
     * @param groupId 群 ID
     * @return 群组实体
     */
    public Optional<Group> getById(String groupId) {
        return groupRepository.findById(groupId);
    }
    
    /**
     * 获取用户加入的群组列表
     * 
     * @param userId 用户 ID
     * @return 群组列表
     */
    public List<Group> getUserGroups(String userId) {
        return groupRepository.findByUserId(userId);
    }
    
    /**
     * 获取用户管理的群组列表 (群主或管理员)
     * 
     * @param userId 用户 ID
     * @return 群组列表
     */
    public List<Group> getAdminGroups(String userId) {
        return groupRepository.findByUserId(userId).stream()
            .filter(group -> {
                return group.getMembers().stream()
                    .anyMatch(m -> m.getUserId().equals(userId) && 
                        ("owner".equals(m.getRole()) || "admin".equals(m.getRole())));
            })
            .collect(Collectors.toList());
    }
    
    /**
     * 添加成员
     * 
     * @param groupId  群 ID
     * @param userId   用户 ID
     * @param nickname 昵称
     * @return 群组实体
     */
    public Group addMember(String groupId, String userId, String nickname) {
        return groupRepository.findById(groupId).map(group -> {
            // 检查是否已是成员
            boolean exists = group.getMembers().stream()
                .anyMatch(m -> m.getUserId().equals(userId));
            
            if (exists) {
                return group;
            }
            
            // 添加成员
            Group.GroupMember member = Group.GroupMember.builder()
                .userId(userId)
                .nickname(nickname)
                .role("member")
                .joinedAt(System.currentTimeMillis())
                .muted(false)
                .build();
            
            group.getMembers().add(member);
            group.setMemberCount(group.getMemberCount() + 1);
            group.setUpdatedAt(System.currentTimeMillis());
            
            return groupRepository.save(group);
        }).orElse(null);
    }
    
    /**
     * 移除成员
     * 
     * @param groupId 群 ID
     * @param userId  用户 ID
     * @return 群组实体
     */
    public Group removeMember(String groupId, String userId) {
        return groupRepository.findById(groupId).map(group -> {
            // 不能移除群主
            if (group.getOwnerId().equals(userId)) {
                return group;
            }
            
            group.getMembers().removeIf(m -> m.getUserId().equals(userId));
            group.setMemberCount(group.getMemberCount() - 1);
            group.setUpdatedAt(System.currentTimeMillis());
            
            return groupRepository.save(group);
        }).orElse(null);
    }
    
    /**
     * 设置成员角色
     * 
     * @param groupId 群 ID
     * @param userId  用户 ID
     * @param role   角色 (admin/member)
     * @return 群组实体
     */
    public Group setMemberRole(String groupId, String userId, String role) {
        return groupRepository.findById(groupId).map(group -> {
            group.getMembers().forEach(member -> {
                if (member.getUserId().equals(userId)) {
                    member.setRole(role);
                }
            });
            group.setUpdatedAt(System.currentTimeMillis());
            
            return groupRepository.save(group);
        }).orElse(null);
    }
    
    /**
     * 禁言/解禁成员
     * 
     * @param groupId 群 ID
     * @param userId  用户 ID
     * @return 群组实体
     */
    public Group toggleMemberMute(String groupId, String userId) {
        return groupRepository.findById(groupId).map(group -> {
            group.getMembers().forEach(member -> {
                if (member.getUserId().equals(userId)) {
                    member.setMuted(!member.getMuted());
                }
            });
            group.setUpdatedAt(System.currentTimeMillis());
            
            return groupRepository.save(group);
        }).orElse(null);
    }
    
    /**
     * 更新群信息
     * 
     * @param groupId     群 ID
     * @param name        群名称
     * @param description 群描述
     * @return 群组实体
     */
    public Group updateInfo(String groupId, String name, String description) {
        return groupRepository.findById(groupId).map(group -> {
            if (name != null) {
                group.setName(name);
            }
            if (description != null) {
                group.setDescription(description);
            }
            group.setUpdatedAt(System.currentTimeMillis());
            
            return groupRepository.save(group);
        }).orElse(null);
    }
    
    /**
     * 解散群组
     * 
     * @param groupId 群 ID
     */
    public void dissolve(String groupId) {
        groupRepository.deleteById(groupId);
    }
    
    /**
     * 退出群组
     * 
     * @param groupId 群 ID
     * @param userId  用户 ID
     * @return 是否成功
     */
    public boolean leave(String groupId, String userId) {
        return groupRepository.findById(groupId).map(group -> {
            // 群主不能退出
            if (group.getOwnerId().equals(userId)) {
                return false;
            }
            
            boolean removed = group.getMembers().removeIf(m -> m.getUserId().equals(userId));
            if (removed) {
                group.setMemberCount(group.getMemberCount() - 1);
                group.setUpdatedAt(System.currentTimeMillis());
                groupRepository.save(group);
            }
            
            return removed;
        }).orElse(false);
    }
    
    /**
     * 生成群 ID
     */
    private String generateId() {
        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 21; i++) {
            int index = (int) (Math.random() * chars.length());
            sb.append(chars.charAt(index));
        }
        return "group_" + sb.toString();
    }
}