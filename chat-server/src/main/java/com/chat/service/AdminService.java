package com.chat.service;

import com.chat.common.Md5Util;
import com.chat.document.AdminUser;
import com.chat.document.Conversation;
import com.chat.document.Friend;
import com.chat.document.Group;
import com.chat.document.Message;
import com.chat.document.User;
import com.chat.repository.AdminUserRepository;
import com.chat.repository.ConversationRepository;
import com.chat.repository.FriendRepository;
import com.chat.repository.GroupRepository;
import com.chat.repository.MessageRepository;
import com.chat.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class AdminService {
    
    private static final Logger logger = LoggerFactory.getLogger(AdminService.class);
    
    @Autowired
    private AdminUserRepository adminUserRepository;
    
    @Autowired
    private UserRepository userRepository;
    
    @Autowired
    private GroupRepository groupRepository;
    
    @Autowired
    private ConversationRepository conversationRepository;
    
    @Autowired
    private MessageRepository messageRepository;
    
    @Autowired
    private FriendRepository friendRepository;
    
    @Autowired
    private MongoTemplate mongoTemplate;
    
    public AdminUser login(String username, String password) {
        String md5Password = Md5Util.encrypt(password);
        String doubleMd5Password = Md5Util.encrypt(md5Password);
        return adminUserRepository.findByUsername(username)
            .filter(user -> {
                String storedPassword = user.getPassword();
                // 支持单MD5和双MD5密码
                return md5Password.equals(storedPassword) || doubleMd5Password.equals(storedPassword);
            })
            .filter(AdminUser::getEnabled)
            .orElse(null);
    }
    
    public Map<String, Object> getUsers(int limit, int skip) {
        int page = skip / limit;
        PageRequest pageRequest = PageRequest.of(page, limit, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<User> userPage = userRepository.findAll(pageRequest);

        List<Map<String, Object>> users = new ArrayList<>();
        for (User user : userPage.getContent()) {
            Map<String, Object> map = new HashMap<>();
            map.put("id", user.getId());
            map.put("username", user.getUsername());
            map.put("nickname", user.getNickname());
            map.put("avatar", user.getAvatar());
            map.put("enabled", user.getEnabled());
            map.put("online", user.getOnline());
            map.put("createdAt", user.getCreatedAt());
            map.put("lastLoginAt", user.getLastLoginAt());
            users.add(map);
        }

        Map<String, Object> result = new HashMap<>();
        result.put("users", users);
        result.put("total", userPage.getTotalElements());
        return result;
    }
    
    public boolean deleteUser(String userId) {
        if (userRepository.existsById(userId)) {
            userRepository.deleteById(userId);
            return true;
        }
        return false;
    }
    
    public boolean updateUserEnabled(String userId, boolean enabled) {
        return userRepository.findById(userId)
            .map(user -> {
                user.setEnabled(enabled);
                userRepository.save(user);
                return true;
            })
            .orElse(false);
    }
    
    public Map<String, Object> getGroups(int limit, int skip) {
        int page = skip / limit;
        PageRequest pageRequest = PageRequest.of(page, limit, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<Group> groupPage = groupRepository.findAll(pageRequest);

        List<Map<String, Object>> groups = new ArrayList<>();
        for (Group group : groupPage.getContent()) {
            Map<String, Object> map = new HashMap<>();
            map.put("id", group.getId());
            map.put("name", group.getName());
            map.put("avatar", group.getAvatar());
            map.put("description", group.getDescription());
            map.put("ownerId", group.getOwnerId());
            map.put("memberCount", group.getMembers() != null ? group.getMembers().size() : 0);
            map.put("createdAt", group.getCreatedAt());
            groups.add(map);
        }

        Map<String, Object> result = new HashMap<>();
        result.put("groups", groups);
        result.put("total", groupPage.getTotalElements());
        return result;
    }
    
    public boolean deleteGroup(String groupId) {
        if (groupRepository.existsById(groupId)) {
            groupRepository.deleteById(groupId);
            return true;
        }
        return false;
    }
    
    public Map<String, Object> getConversationList(int page, int size, String keyword) {
        return getConversations(null, size, page * size);
    }
    
    public boolean deleteConversation(String conversationId) {
        if (conversationRepository.existsById(conversationId)) {
            conversationRepository.deleteById(conversationId);
            messageRepository.deleteByConversationId(conversationId);
            return true;
        }
        return false;
    }
    
    public Map<String, Object> getMessages(String conversationId, String senderId, int limit, int skip) {
        int page = skip / limit;
        PageRequest pageRequest = PageRequest.of(page, limit, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<Message> messagePage;

        if (conversationId != null && !conversationId.isEmpty()) {
            messagePage = messageRepository.findByConversationId(conversationId, pageRequest);
        } else if (senderId != null && !senderId.isEmpty()) {
            messagePage = messageRepository.findBySenderId(senderId, pageRequest);
        } else {
            messagePage = messageRepository.findAll(pageRequest);
        }

        List<Map<String, Object>> messages = new ArrayList<>();
        for (Message msg : messagePage.getContent()) {
            Map<String, Object> map = new HashMap<>();
            map.put("id", msg.getId());
            map.put("conversationId", msg.getConversationId());
            map.put("senderId", msg.getSenderId());
            map.put("content", msg.getContent());
            map.put("contentType", msg.getContentType());
            map.put("type", msg.getContentType() != null && msg.getContentType().equals("image") ? 2 : 1);
            map.put("status", msg.getStatus());
            map.put("createdAt", msg.getCreatedAt());
            messages.add(map);
        }

        Map<String, Object> result = new HashMap<>();
        result.put("messages", messages);
        result.put("total", messagePage.getTotalElements());
        return result;
    }
    
    public boolean deleteMessage(String messageId) {
        if (messageRepository.existsById(messageId)) {
            messageRepository.deleteById(messageId);
            return true;
        }
        return false;
    }
    
    public Map<String, Object> getStats() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("userCount", userRepository.count());
        stats.put("groupCount", groupRepository.count());
        stats.put("conversationCount", conversationRepository.count());
        stats.put("messageCount", messageRepository.count());
        return stats;
    }
    
    /**
     * 清空所有数据
     */
    public void clearAllData() {
        logger.info("清空所有数据...");
        mongoTemplate.dropCollection("users");
        mongoTemplate.dropCollection("friends");
        mongoTemplate.dropCollection("groups");
        mongoTemplate.dropCollection("conversations");
        mongoTemplate.dropCollection("messages");
        mongoTemplate.dropCollection("adminUsers");
        logger.info("数据已清空");
    }
    
    /**
     * 检查是否已有数据
     */
    public boolean hasData() {
        return userRepository.count() > 0;
    }
    
    public boolean initAdminUser() {
        if (!adminUserRepository.findByUsername("admin").isPresent()) {
            AdminUser admin = AdminUser.builder()
                .username("admin")
                .password(Md5Util.encrypt("admin123"))
                .nickname("管理员")
                .avatar("")
                .createdAt(System.currentTimeMillis())
                .enabled(true)
                .roles(new ArrayList<>())
                .build();
            adminUserRepository.save(admin);
            logger.info("Admin user initialized: admin / admin123 (MD5 encrypted)");
            return true;
        }
        return false;
    }
    
    private String[] chineseSurnames = {"张", "李", "王", "刘", "陈", "杨", "赵", "黄", "周", "吴", "徐", "孙", "胡", "朱", "高", "林", "何", "郭", "马", "罗", "梁", "宋", "郑", "谢", "韩", "唐", "冯", "于", "董", "萧", "程", "曹", "袁", "邓", "许", "傅", "沈", "曾", "彭", "吕"};
    private String[] givenNames = {"伟", "芳", "娜", "秀英", "敏", "静", "丽", "强", "磊", "军", "洋", "勇", "艳", "杰", "娟", "涛", "明", "超", "秀兰", "霞", "平", "刚", "桂英", "建华", "建国", "建军", "丽丽", "秀珍", "桂兰", "阿", "龙", "凤", "飞", "玉", "华", "辉", "鑫", "宇", "晨", "雨", "雪", "梅", "兰", "菊", "琴"};
    private String[] companyNames = {"阿里巴巴", "腾讯科技", "百度", "字节跳动", "美团", "京东", "拼多多", "网易", "滴滴出行", "小米科技", "华为技术", "中兴通讯", "海尔集团", "格力电器", "比亚迪", "万科集团", "中国平安", "招商银行", "中国石油", "中国石化"};
    private String[] projectNames = {"项目群", "技术部", "产品组", "运营部", "市场部", "财务部", "人力资源", "行政部", "客服部", "研发组", "测试组", "设计部", "市场推广", "销售部", "采购部", "仓储部", "物流部", "质量管理", "法务部", "公共关系"};
    private String[] greetings = {"早上好", "下午好", "晚上好", "你好", "在吗", "忙吗", "有个事", "收到", "好的", "没问题", "谢谢", "辛苦了", "辛苦了", "好的", "收到", "明白", "了解", "可以", "没问题", "OK"};
    private String[] messageContents = {"今天天气不错", "明天开会", "周末加班", "这个需求", "那个问题", "我查一下", "等会回复", "好的", "收到", "明白", "在处理", "已经完成", "稍等", "看一下", "没问题", "可以", "OK", "好的", "谢谢", "不客气", "辛苦了", "休息一下", "喝杯咖啡", "下班了吗", "吃饭了吗", "路上小心", "注意安全", "晚安", "早安", "再见"};

    /**
     * 初始化测试数据
     */
    public Map<String, Object> initTestData() {
        Map<String, Object> result = new HashMap<>();
        
        try {
            // 清空现有数据
            messageRepository.deleteAll();
            conversationRepository.deleteAll();
            friendRepository.deleteAll();
            groupRepository.deleteAll();
            userRepository.deleteAll();
            
            long now = System.currentTimeMillis();
            
            // 创建100个测试用户
            List<User> users = new ArrayList<>();
            for (int i = 1; i <= 100; i++) {
                String surname = chineseSurnames[(i - 1) % chineseSurnames.length];
                String givenName = givenNames[(i - 1) % givenNames.length];
                String nickname = surname + givenName + (i > 40 ? (i - 40) : "");
                
                User user = User.builder()
                    .id("user_" + String.format("%03d", i))
                    .username("user" + String.format("%03d", i))
                    .nickname(nickname)
                    .avatar("https://api.dicebear.com/7.x/avataaars/svg?seed=" + nickname)
                    .password(Md5Util.encrypt(Md5Util.encrypt("123456")))
                    .online(false)
                    .createdAt(now - (long) (Math.random() * 90) * 86400000L)
                    .build();
                users.add(user);
            }
            users = userRepository.saveAll(users);
            
            // 创建15个群组
            List<Group> groups = new ArrayList<>();
            for (int i = 1; i <= 15; i++) {
                String company = companyNames[(i - 1) % companyNames.length];
                String project = projectNames[(i - 1) % projectNames.length];
                int ownerIndex = (i - 1) * 6 % 100;
                
                List<Group.GroupMember> members = new ArrayList<>();
                int memberCount = 5 + (int) (Math.random() * 10);
                for (int j = 0; j < memberCount; j++) {
                    int userIndex = (ownerIndex + j) % 100;
                    User u = users.get(userIndex);
                    String role = j == 0 ? "owner" : "member";
                    members.add(Group.GroupMember.builder()
                        .userId(u.getId())
                        .nickname(u.getNickname())
                        .avatar(u.getAvatar())
                        .role(role)
                        .joinedAt(now - (long) (Math.random() * 30) * 86400000L)
                        .build());
                }
                
                Group group = Group.builder()
                    .id("group_" + String.format("%03d", i))
                    .name(company + project)
                    .description(company + " " + project + " 交流群")
                    .avatar("https://api.dicebear.com/7.x/initials/svg?seed=" + company + project)
                    .ownerId(users.get(ownerIndex).getId())
                    .members(members)
                    .memberCount(memberCount)
                    .type("group")
                    .allMuted(false)
                    .createdAt(now - (long) (Math.random() * 60) * 86400000L)
                    .build();
                groups.add(group);
            }
            groups = groupRepository.saveAll(groups);
            
            // 为每个群组创建对应的会话
            for (Group group : groups) {
                List<String> participantIds = new ArrayList<>();
                for (Group.GroupMember member : group.getMembers()) {
                    participantIds.add(member.getUserId());
                }
                
                Conversation groupConv = Conversation.builder()
                    .id("conv_group_" + group.getId())
                    .type("group")
                    .relateId(group.getId())
                    .name(group.getName())
                    .avatar(group.getAvatar())
                    .participants(participantIds)
                    .muted(Math.random() > 0.9)
                    .createdAt(group.getCreatedAt())
                    .updatedAt(now - (long) (Math.random() * 7) * 86400000L)
                    .build();
                conversationRepository.save(groupConv);
                
                // 为每个群组创建多条历史消息
                int msgCount = 5 + (int) (Math.random() * 15);
                for (int m = 0; m < msgCount; m++) {
                    int senderIndex = (int) (Math.random() * group.getMembers().size());
                    String senderId = group.getMembers().get(senderIndex).getUserId();
                    Message msg = Message.builder()
                        .id("msg_g_" + group.getId() + "_" + m)
                        .conversationId(groupConv.getId())
                        .conversationType("group")
                        .senderId(senderId)
                        .content(messageContents[(int) (Math.random() * messageContents.length)])
                        .contentType("text")
                        .status("read")
                        .createdAt(now - (long) (msgCount - m) * 3600000L - (long) (Math.random() * 86400000L))
                        .build();
                    messageRepository.save(msg);
                }
            }
            
            // 创建200对好友关系和私聊会话
            List<Conversation> privateConvs = new ArrayList<>();
            for (int i = 0; i < 200; i++) {
                int userIdx1 = (int) (Math.random() * 100);
                int userIdx2 = (userIdx1 + 1 + (int) (Math.random() * 99)) % 100;
                
                String uid1 = users.get(userIdx1).getId();
                String uid2 = users.get(userIdx2).getId();
                
                // 创建好友关系
                createFriendRelationship(uid1, uid2, now);
                
                // 创建私聊会话
                Conversation conv = Conversation.builder()
                    .id("conv_p_" + uid1 + "_" + uid2)
                    .type("private")
                    .relateId(uid1 + "_" + uid2)
                    .name(users.get(userIdx2).getNickname())
                    .avatar(users.get(userIdx2).getAvatar())
                    .participants(Arrays.asList(uid1, uid2))
                    .muted(Math.random() > 0.95)
                    .createdAt(now - (long) (Math.random() * 60) * 86400000L)
                    .updatedAt(now - (long) (Math.random() * 7) * 86400000L)
                    .build();
                conversationRepository.save(conv);
                privateConvs.add(conv);
            }
            
            // 为每个私聊会话创建多条消息
            for (Conversation conv : privateConvs) {
                List<String> participants = conv.getParticipants();
                int msgCount = 2 + (int) (Math.random() * 8);
                for (int m = 0; m < msgCount; m++) {
                    String senderId = participants.get(m % 2);
                    Message msg = Message.builder()
                        .id("msg_p_" + conv.getId() + "_" + m)
                        .conversationId(conv.getId())
                        .conversationType("private")
                        .senderId(senderId)
                        .content(messageContents[(int) (Math.random() * messageContents.length)])
                        .contentType("text")
                        .status("read")
                        .createdAt(now - (long) (msgCount - m) * 1800000L - (long) (Math.random() * 86400000L))
                        .build();
                    messageRepository.save(msg);
                }
            }
            
            result.put("success", true);
            result.put("message", "测试数据初始化完成");
            result.put("userCount", users.size());
            result.put("groupCount", groups.size());
            result.put("conversationCount", conversationRepository.count());
            result.put("messageCount", messageRepository.count());
            
            logger.info("测试数据初始化完成: {} 用户, {} 群组", users.size(), groups.size());
            
        } catch (Exception e) {
            result.put("success", false);
            result.put("message", "初始化失败: " + e.getMessage());
            logger.error("测试数据初始化失败", e);
        }
        
        return result;
    }
    
    /**
     * 创建好友关系（如果不存在）- 使用单记录
     */
    private void createFriendRelationship(String userId, String friendId, long timestamp) {
        // 检查是否已存在 (双向查询)
        Optional<Friend> existing = friendRepository.findByUserIdOrRelatedUserId(userId, friendId);
        if (existing.isPresent()) {
            return; // 已存在
        }
        
        // 创建好友关系 (单记录，设置两个字段保持兼容)
        Friend friend = Friend.builder()
            .userId(userId)
            .friendId(friendId)
            .relatedUserId(friendId)
            .status("accepted")
            .source("search")
            .createdAt(timestamp)
            .updatedAt(timestamp)
            .build();
        friendRepository.save(friend);
    }
    
    /**
     * 生成随机ID
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

    // ============================================
    // 管理人员管理
    // ============================================

    /**
     * 获取管理员列表
     */
    public Map<String, Object> getAdmins() {
        List<AdminUser> admins = adminUserRepository.findAll();
        List<Map<String, Object>> list = new ArrayList<>();
        for (AdminUser admin : admins) {
            Map<String, Object> item = new HashMap<>();
            item.put("id", admin.getId());
            item.put("username", admin.getUsername());
            item.put("nickname", admin.getNickname());
            item.put("roles", admin.getRoles());
            item.put("createdAt", admin.getCreatedAt());
            item.put("lastLoginAt", admin.getLastLoginAt());
            list.add(item);
        }
        Map<String, Object> result = new HashMap<>();
        result.put("admins", list);
        result.put("total", list.size());
        return result;
    }

    /**
     * 创建管理员
     */
    public Map<String, Object> createAdmin(String username, String password, String nickname) {
        // 检查用户名是否已存在
        Optional<AdminUser> existing = adminUserRepository.findByUsername(username);
        if (existing.isPresent()) {
            Map<String, Object> result = new HashMap<>();
            result.put("success", false);
            result.put("message", "用户名已存在");
            return result;
        }

        AdminUser admin = AdminUser.builder()
            .username(username)
            .password(Md5Util.encrypt(password))
            .nickname(nickname != null ? nickname : username)
            .roles(Arrays.asList("admin"))
            .createdAt(System.currentTimeMillis())
            .build();
        adminUserRepository.save(admin);

        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("message", "管理员创建成功");
        return result;
    }

    /**
     * 删除管理员
     */
    public Map<String, Object> deleteAdmin(String id) {
        Map<String, Object> result = new HashMap<>();
        Optional<AdminUser> admin = adminUserRepository.findById(id);
        if (!admin.isPresent()) {
            result.put("success", false);
            result.put("message", "管理员不存在");
            return result;
        }
        adminUserRepository.deleteById(id);
        result.put("success", true);
        result.put("message", "管理员已删除");
        return result;
    }

    // ============================================
    // 会话管理
    // ============================================

    /**
     * 获取会话列表
     */
    public Map<String, Object> getConversations(String type, int limit, int skip) {
        Page<Conversation> page;
        if (type != null && !type.isEmpty()) {
            page = conversationRepository.findByType(type, PageRequest.of(skip / limit, limit, Sort.by(Sort.Direction.DESC, "updatedAt")));
        } else {
            page = conversationRepository.findAll(PageRequest.of(skip / limit, limit, Sort.by(Sort.Direction.DESC, "updatedAt")));
        }

        List<Map<String, Object>> list = new ArrayList<>();
        for (Conversation conv : page.getContent()) {
            Map<String, Object> item = new HashMap<>();
            item.put("id", conv.getId());
            item.put("type", conv.getType());
            item.put("name", conv.getName());
            item.put("participants", conv.getParticipants());
            item.put("createdAt", conv.getCreatedAt());
            item.put("updatedAt", conv.getUpdatedAt());
            list.add(item);
        }

        Map<String, Object> result = new HashMap<>();
        result.put("conversations", list);
        result.put("total", conversationRepository.count());
        return result;
    }

    /**
     * 获取会话详情
     */
    public Map<String, Object> getConversationDetail(String id) {
        Map<String, Object> result = new HashMap<>();
        Optional<Conversation> conv = conversationRepository.findById(id);
        if (!conv.isPresent()) {
            result.put("error", "会话不存在");
            return result;
        }

        Conversation c = conv.get();
        result.put("id", c.getId());
        result.put("type", c.getType());
        result.put("name", c.getName());
        result.put("avatar", c.getAvatar());
        result.put("participants", c.getParticipants());
        result.put("muted", c.getMuted());
        result.put("createdAt", c.getCreatedAt());
        result.put("updatedAt", c.getUpdatedAt());

        // 获取最近消息
        List<Message> recentMessages = messageRepository.findTop10ByConversationIdOrderByCreatedAtDesc(
            c.getId(), PageRequest.of(0, 10));
        List<Map<String, Object>> msgs = new ArrayList<>();
        for (Message msg : recentMessages) {
            Map<String, Object> m = new HashMap<>();
            m.put("id", msg.getId());
            m.put("senderId", msg.getSenderId());
            m.put("content", msg.getContent());
            m.put("contentType", msg.getContentType());
            m.put("createdAt", msg.getCreatedAt());
            msgs.add(m);
        }
        result.put("recentMessages", msgs);

        return result;
    }

    /**
     * 搜索会话
     */
    public Map<String, Object> searchConversations(String keyword) {
        Page<Conversation> page = conversationRepository.findByNameContaining(keyword, PageRequest.of(0, 50));
        List<Map<String, Object>> list = new ArrayList<>();
        for (Conversation conv : page.getContent()) {
            Map<String, Object> item = new HashMap<>();
            item.put("id", conv.getId());
            item.put("type", conv.getType());
            item.put("name", conv.getName());
            item.put("participants", conv.getParticipants());
            list.add(item);
        }

        Map<String, Object> result = new HashMap<>();
        result.put("conversations", list);
        result.put("total", list.size());
        return result;
    }

    // ============================================
    // 好友管理
    // ============================================

    /**
     * 获取好友列表
     */
    public Map<String, Object> getFriends(String userId, int limit, int skip) {
        List<Friend> friends;
        if (userId != null && !userId.isEmpty()) {
            Optional<Friend> friendOpt = friendRepository.findByUserIdOrRelatedUserId(userId, userId);
            friends = friendOpt.map(Arrays::asList).orElse(new ArrayList<>());
        } else {
            Page<Friend> page = friendRepository.findAll(PageRequest.of(skip / limit, limit));
            friends = page.getContent();
        }

        List<Map<String, Object>> list = new ArrayList<>();
        for (Friend friend : friends) {
            Map<String, Object> item = new HashMap<>();
            item.put("id", friend.getId());
            item.put("userId", friend.getUserId());
            item.put("friendId", friend.getFriendId() != null ? friend.getFriendId() : friend.getRelatedUserId());
            item.put("status", friend.getStatus());
            item.put("createdAt", friend.getCreatedAt());
            list.add(item);
        }

        Map<String, Object> result = new HashMap<>();
        result.put("friends", list);
        result.put("total", friendRepository.count());
        return result;
    }

    /**
     * 获取好友详情
     */
    public Map<String, Object> getFriendDetail(String id) {
        Map<String, Object> result = new HashMap<>();
        Optional<Friend> friend = friendRepository.findById(id);
        if (!friend.isPresent()) {
            result.put("error", "好友关系不存在");
            return result;
        }

        Friend f = friend.get();
        result.put("id", f.getId());
        result.put("userId", f.getUserId());
        result.put("friendId", f.getFriendId() != null ? f.getFriendId() : f.getRelatedUserId());
        result.put("status", f.getStatus());
        result.put("source", f.getSource());
        result.put("createdAt", f.getCreatedAt());
        result.put("updatedAt", f.getUpdatedAt());

        return result;
    }

    /**
     * 删除好友关系
     */
    public Map<String, Object> deleteFriend(String id) {
        Map<String, Object> result = new HashMap<>();
        Optional<Friend> friend = friendRepository.findById(id);
        if (!friend.isPresent()) {
            result.put("success", false);
            result.put("message", "好友关系不存在");
            return result;
        }
        friendRepository.deleteById(id);
        result.put("success", true);
        result.put("message", "好友关系已删除");
        return result;
    }

    /**
     * 搜索好友
     */
    public Map<String, Object> searchFriends(String keyword) {
        // 搜索用户
        List<User> users = userRepository.findByUsernameContainingOrNicknameContaining(keyword);
        List<Map<String, Object>> list = new ArrayList<>();
        for (User user : users) {
            Optional<Friend> friendOpt = friendRepository.findByUserIdOrRelatedUserId(user.getId(), user.getId());
            int friendCount = friendOpt.isPresent() ? 1 : 0;
            Map<String, Object> item = new HashMap<>();
            item.put("userId", user.getId());
            item.put("username", user.getUsername());
            item.put("nickname", user.getNickname());
            item.put("avatar", user.getAvatar());
            item.put("friendCount", friendCount);
            list.add(item);
        }

        Map<String, Object> result = new HashMap<>();
        result.put("users", list);
        result.put("total", list.size());
        return result;
    }

    // ============================================
    // 账号设置
    // ============================================

    /**
     * 获取管理员信息
     */
    public Map<String, Object> getAdminProfile(String adminId) {
        Map<String, Object> result = new HashMap<>();
        Optional<AdminUser> adminOpt = adminUserRepository.findById(adminId);
        if (!adminOpt.isPresent()) {
            result.put("success", false);
            result.put("message", "管理员不存在");
            return result;
        }

        AdminUser admin = adminOpt.get();
        result.put("adminId", admin.getId());
        result.put("username", admin.getUsername());
        result.put("nickname", admin.getNickname());
        result.put("roles", admin.getRoles());
        result.put("createdAt", admin.getCreatedAt());
        result.put("lastLoginAt", admin.getLastLoginAt());
        return result;
    }

    /**
     * 修改管理员信息
     */
    public Map<String, Object> updateAdminProfile(String adminId, String nickname) {
        Map<String, Object> result = new HashMap<>();
        Optional<AdminUser> adminOpt = adminUserRepository.findById(adminId);
        if (!adminOpt.isPresent()) {
            result.put("success", false);
            result.put("message", "管理员不存在");
            return result;
        }

        AdminUser admin = adminOpt.get();
        if (nickname != null && !nickname.trim().isEmpty()) {
            admin.setNickname(nickname.trim());
        }
        adminUserRepository.save(admin);

        result.put("success", true);
        result.put("message", "信息修改成功");
        result.put("nickname", admin.getNickname());
        return result;
    }

    /**
     * 修改密码
     */
    public Map<String, Object> updateAdminPassword(String adminId, String oldPassword, String newPassword) {
        Map<String, Object> result = new HashMap<>();
        Optional<AdminUser> adminOpt = adminUserRepository.findById(adminId);
        if (!adminOpt.isPresent()) {
            result.put("success", false);
            result.put("message", "管理员不存在");
            return result;
        }

        AdminUser admin = adminOpt.get();
        String inputMd5 = Md5Util.encrypt(oldPassword);
        String storedMd5 = admin.getPassword();
        String inputDoubleMd5 = Md5Util.encrypt(inputMd5);

        if (!inputMd5.equals(storedMd5) && !inputDoubleMd5.equals(storedMd5)) {
            result.put("success", false);
            result.put("message", "原密码错误");
            return result;
        }

        admin.setPassword(Md5Util.encrypt(newPassword));
        adminUserRepository.save(admin);

        result.put("success", true);
        result.put("message", "密码修改成功");
        return result;
    }
}