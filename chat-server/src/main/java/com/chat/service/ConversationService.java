package com.chat.service;

import com.chat.document.Conversation;
import com.chat.document.Friend;
import com.chat.document.Message;
import com.chat.document.User;
import com.chat.repository.ConversationRepository;
import com.chat.repository.FriendRepository;
import com.chat.repository.MessageRepository;
import com.chat.repository.UserRepository;
import com.chat.util.RedisCacheUtil;

import lombok.extern.slf4j.Slf4j;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class ConversationService {
    
    private static final Logger logger = LoggerFactory.getLogger(ConversationService.class);
    
    @Autowired
    private ConversationRepository conversationRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private FriendRepository friendRepository;

    @Autowired
    private MessageRepository messageRepository;

    @Autowired
    private RedisCacheUtil redisCacheUtil;
    
    /**
     * 获取或创建私聊会话
     * 
     * @param userId1 用户1 ID
     * @param userId2 用户2 ID
     * @return 会话实体
     */
    public Conversation getOrCreatePrivate(String userId1, String userId2) {
        // 确保两个用户都存在
        User user1 = redisCacheUtil.getFullUserById(userId1);
        User user2 = redisCacheUtil.getFullUserById(userId2);

        if (user1 == null || user2 == null) {
            return null;
        }

        // 先查找已存在的私聊 (检查两个用户是否都在参与者列表中)
        List<Conversation> existing = conversationRepository.findByParticipantsContainingAndType(userId1, "private");
        for (Conversation conv : existing) {
            List<String> participants = conv.getParticipants();
            if (participants != null && participants.contains(userId1) && participants.contains(userId2) && participants.size() == 2) {
                return conv;
            }
        }

        String name1 = user1.getNickname() != null ? user1.getNickname() : "用户";
        String name2 = user2.getNickname() != null ? user2.getNickname() : "用户";

        // 私聊时，userId1 是当前用户，userId2 是对方，使用对方的头像
        String avatar = user2.getAvatar();
        
        // 查找好友关系记录作为 relateId (使用新的单记录查询)
        String relateId = null;
        Optional<Friend> friendOpt = friendRepository.findByUserIdOrRelatedUserId(userId1, userId2);
        if (friendOpt.isPresent()) {
            relateId = friendOpt.get().getId();
        }
        
        Conversation conversation = Conversation.builder()
            .id(generateId())
            .type("private")
            .relateId(relateId)
            .participants(Arrays.asList(userId1, userId2))
            .name(name2) // 私聊显示对方名称
            .avatar(avatar) // 使用对方头像
            .muted(false)
            .createdAt(System.currentTimeMillis())
            .build();
        
        return conversationRepository.save(conversation);
    }
    
    /**
     * 根据ID获取或创建会话 (用于已知conversationId的情况)
     * 
     * @param conversationId 会话 ID
     * @param userId 当前用户 ID
     * @return 会话实体
     */
    public Conversation getOrCreateById(String conversationId, String userId) {
        // 先查找已存在的会话
        Optional<Conversation> existing = conversationRepository.findById(conversationId);
        
        if (existing.isPresent()) {
            return existing.get();
        }
        
        // 尝试解析friendId - 支持 conv_friend_xxx 格式
        if (conversationId.startsWith("conv_friend_")) {
            String friendId = conversationId.substring("conv_friend_".length());
            
            // 获取用户信息
            User user = redisCacheUtil.getFullUserById(userId);
            User friend = redisCacheUtil.getFullUserById(friendId);

            if (user == null || friend == null) {
                return null;
            }

            String name = friend.getNickname() != null ? friend.getNickname() : friend.getUsername();
            
            Conversation conversation = Conversation.builder()
                    .id(generateId())
                    .type("private")
                    .participants(Arrays.asList(userId, friendId))
                    .name(name)
                    .muted(false)
                    .createdAt(System.currentTimeMillis())
                    .build();
            
            return conversationRepository.save(conversation);
        }
        
        // 其他类型会话暂不支持自动创建
        return null;
    }
    
    /**
     * 获取用户的会话列表
     * 获取用户全部会话，再去拉取会话的最新消息，以此消息的时间来排序
     *
     * @param userId 用户 ID
     * @param limit  数量限制
     * @return 会话列表
     */
    public List<Conversation> getUserConversations(String userId, int limit) {
        List<Conversation> conversations = conversationRepository.findUserConversationsDistinct(userId);

        List<Conversation> sorted = conversations.stream()
            .map(conv -> {
                Optional<Message> latestMsg = messageRepository.findTopByConversationIdOrderByCreatedAtDesc(conv.getId());
                if (latestMsg.isPresent()) {
                    conv.setUpdatedAt(latestMsg.get().getCreatedAt());
                    conv.setLastMessage(latestMsg.get());
                }
                return conv;
            })
            .sorted(Comparator.comparing(Conversation::getUpdatedAt, Comparator.nullsLast(Comparator.reverseOrder())))
            .collect(Collectors.toList());

        if (sorted.size() > limit) {
            return sorted.subList(0, limit);
        }
        return sorted;
    }

    /**
     * 获取用户信息
     *
     * @param userId 用户 ID
     * @return 用户信息
     */
    public User getUserById(String userId) {
        return redisCacheUtil.getFullUserById(userId);
    }

    /**
     * 根据 ID 获取会话
     *
     * @param conversationId 会话 ID
     * @return 会话实体
     */
    public Optional<Conversation> getById(String conversationId) {
        return conversationRepository.findById(conversationId);
    }
    
    /**
     * 获取会话成员列表
     * 
     * @param conversationId 会话 ID
     * @return 成员 ID 列表
     */
    public List<String> getParticipants(String conversationId) {
        return conversationRepository.findById(conversationId)
            .map(Conversation::getParticipants)
            .orElse(new ArrayList<>());
    }
    
    /**
     * 更新会话
     * 
     * @param conversation 会话实体
     * @return 更新后的会话
     */
    public Conversation update(Conversation conversation) {
        conversation.setUpdatedAt(System.currentTimeMillis());
        return conversationRepository.save(conversation);
    }
    
    /**
     * 生成会话 ID
     * 
     * @return 会话 ID
     */
    private String generateId() {
        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 21; i++) {
            int index = (int) (Math.random() * chars.length());
            sb.append(chars.charAt(index));
        }
        return sb.toString();
    }
}