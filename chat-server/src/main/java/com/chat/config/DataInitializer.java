//package com.chat.config;
//
//import com.chat.common.Md5Util;
//import com.chat.document.*;
//import com.chat.repository.*;
//import org.slf4j.Logger;
//import org.slf4j.LoggerFactory;
//import org.springframework.beans.factory.annotation.Autowired;
//import org.springframework.boot.CommandLineRunner;
//import org.springframework.data.mongodb.core.MongoTemplate;
//import org.springframework.stereotype.Component;
//
//import java.util.*;
//
///**
// * 数据初始化器
// * 服务器启动时自动创建测试数据
// */
//@Component
//public class DataInitializer implements CommandLineRunner {
//    
//    private static final Logger logger = LoggerFactory.getLogger(DataInitializer.class);
//    
//    @Autowired
//    private UserRepository userRepository;
//    
//    @Autowired
//    private GroupRepository groupRepository;
//    
//    @Autowired
//    private ConversationRepository conversationRepository;
//    
//    @Autowired
//    private MessageRepository messageRepository;
//    
//    @Autowired
//    private MongoTemplate mongoTemplate;
//    
//    @Override
//    public void run(String... args) {
//        logger.info("开始初始化测试数据...");
//        
//        // 检查是否已有数据
//        if (userRepository.count() > 0) {
//            logger.info("数据库已有数据，跳过初始化");
//            return;
//        }
//        
//        try {
//            // 创建测试用户
//            List<User> users = createUsers();
//            
//            // 创建群组
//            List<Group> groups = createGroups(users);
//            
//            // 创建私聊会话和消息
//            createPrivateConversations(users);
//            
//            // 创建群聊会话和消息
//            createGroupConversations(groups, users);
//            
//            logger.info("测试数据初始化完成！");
//            logger.info("  - 用户: {} 个", users.size());
//            logger.info("  - 群组: {} 个", groups.size());
//            logger.info("  - 会话: {} 个", conversationRepository.count());
//            logger.info("  - 消息: {} 条", messageRepository.count());
//            
//        } catch (Exception e) {
//            logger.error("数据初始化失败: {}", e.getMessage(), e);
//        }
//    }
//    
//    private List<User> createUsers() {
//        List<User> users = new ArrayList<>();
//        
//        String[][] userData = {
//            {"user_001", "zhangsan", "张三", "https://api.dicebear.com/7.x/avataaars/svg?seed=zhangsan"},
//            {"user_002", "lisi", "李四", "https://api.dicebear.com/7.x/avataaars/svg?seed=lisi"},
//            {"user_003", "wangwu", "王五", "https://api.dicebear.com/7.x/avataaars/svg?seed=wangwu"},
//            {"user_004", "zhaoliu", "赵六", "https://api.dicebear.com/7.x/avataaars/svg?seed=zhaoliu"},
//            {"user_005", "sunqi", "孙七", "https://api.dicebear.com/7.x/avataaars/svg?seed=sunqi"},
//            {"user_006", "zhouba", "周八", "https://api.dicebear.com/7.x/avataaars/svg?seed=zhouba"},
//            {"user_007", "wujiu", "吴九", "https://api.dicebear.com/7.x/avataaars/svg?seed=wujiu"},
//            {"user_008", "zhengshi", "郑十", "https://api.dicebear.com/7.x/avataaars/svg?seed=zhengshi"},
//        };
//        
//        long now = System.currentTimeMillis();
//        
//        for (String[] data : userData) {
//            User user = User.builder()
//                .id(data[0])
//                .username(data[1])
//                .nickname(data[2])
//                .avatar(data[3])
//                .password(Md5Util.encrypt("123456"))
//                .online(false)
//                .createdAt(now - (long) (Math.random() * 30) * 86400000)
//                .build();
//            users.add(user);
//        }
//        
//        users = userRepository.saveAll(users);
//        logger.info("创建了 {} 个用户", users.size());
//        
//        return users;
//    }
//    
//    private List<Group> createGroups(List<User> users) {
//        List<Group> groups = new ArrayList<>();
//        
//        // 前端开发群
//        Group group1 = createGroup("前端开发群", "前端技术交流群", users.get(0), 
//            Arrays.asList(users.get(0), users.get(1), users.get(2), users.get(3)));
//        groups.add(group1);
//        
//        // 后端技术群
//        Group group2 = createGroup("后端技术群", "后端开发交流", users.get(1),
//            Arrays.asList(users.get(1), users.get(2), users.get(4), users.get(5)));
//        groups.add(group2);
//        
//        // 产品经理群
//        Group group3 = createGroup("产品经理群", "产品需求讨论", users.get(2),
//            Arrays.asList(users.get(2), users.get(3), users.get(6)));
//        groups.add(group3);
//        
//        // 测试团队
//        Group group4 = createGroup("测试团队", "QA 交流群", users.get(3),
//            Arrays.asList(users.get(3), users.get(4), users.get(5), users.get(6)));
//        groups.add(group4);
//        
//        groups = groupRepository.saveAll(groups);
//        logger.info("创建了 {} 个群组", groups.size());
//        
//        return groups;
//    }
//    
//    private Group createGroup(String name, String description, User owner, List<User> members) {
//        List<Group.GroupMember> groupMembers = new ArrayList<>();
//        
//        // 添加群主
//        groupMembers.add(Group.GroupMember.builder()
//            .userId(owner.getId())
//            .nickname(owner.getNickname())
//            .avatar(owner.getAvatar())
//            .role("owner")
//            .joinedAt(System.currentTimeMillis())
//            .build());
//        
//        // 添加其他成员
//        for (User member : members) {
//            if (!member.getId().equals(owner.getId())) {
//                groupMembers.add(Group.GroupMember.builder()
//                    .userId(member.getId())
//                    .nickname(member.getNickname())
//                    .avatar(member.getAvatar())
//                    .role("member")
//                    .joinedAt(System.currentTimeMillis() - (long) (Math.random() * 7) * 86400000)
//                    .build());
//            }
//        }
//        
//        return Group.builder()
//            .name(name)
//            .description(description)
//            .avatar("https://api.dicebear.com/7.x/initials/svg?seed=" + name)
//            .ownerId(owner.getId())
//            .members(groupMembers)
//            .memberCount(groupMembers.size())
//            .type("group")
//            .allMuted(false)
//            .createdAt(System.currentTimeMillis() - (long) (Math.random() * 30) * 86400000)
//            .build();
//    }
//    
//    private void createPrivateConversations(List<User> users) {
//        Random random = new Random();
//        
//        // 用户 1 和用户 2 的私聊
//        createConversation(users.get(0), users.get(1), "private", 
//            Arrays.asList("今天天气不错", "是的，周末去爬山吗？", "好主意！", "那周六早上9点见"));
//        
//        // 用户 3 和用户 4 的私聊
//        createConversation(users.get(2), users.get(3), "private",
//            Arrays.asList("上次的需求文档你看了吗？", "看了，有些问题想和你确认", "说吧", "关于用户权限的部分..."));
//        
//        // 用户 5 和用户 6 的私聊
//        createConversation(users.get(4), users.get(5), "private",
//            Arrays.asList("新项目什么时候开始？", "下周一开始", "需要我配合什么吗？", "先把环境搭好"));
//        
//        logger.info("创建了私聊会话");
//    }
//    
//    private void createConversation(User user1, User user2, String type, List<String> messages) {
//        String conversationId = user1.getId().compareTo(user2.getId()) < 0 
//            ? user1.getId() + "_" + user2.getId() 
//            : user2.getId() + "_" + user1.getId();
//        
//        Conversation conversation = Conversation.builder()
//            .id("conv_" + conversationId)
//            .type(type)
//            .name(type.equals("private") ? user2.getNickname() : "群聊")
//            .avatar(user2.getAvatar())
//            .participants(Arrays.asList(user1.getId(), user2.getId()))
//            .unreadCount(0)
//            .pinned(false)
//            .muted(false)
//            .createdAt(System.currentTimeMillis() - 86400000)
//            .build();
//        
//        conversation = conversationRepository.save(conversation);
//        
//        // 创建消息
//        long baseTime = System.currentTimeMillis() - 86400000;
//        for (int i = 0; i < messages.size(); i++) {
//            Message message = Message.builder()
//                .id("msg_" + UUID.randomUUID().toString().substring(0, 8))
//                .conversationId(conversation.getId())
//                .senderId(i % 2 == 0 ? user1.getId() : user2.getId())
//                .content(messages.get(i))
//                .contentType("text")
//                .status("read")
//                .createdAt(baseTime + i * 60000)
//                .build();
//            messageRepository.save(message);
//        }
//        
//        // 更新会话的最后消息
//        conversation.setLastMessageContent(messages.get(messages.size() - 1));
//        conversation.setLastMessageAt(baseTime + (messages.size() - 1) * 60000);
//        conversationRepository.save(conversation);
//    }
//    
//    private void createGroupConversations(List<Group> groups, List<User> users) {
//        Random random = new Random();
//        
//        for (Group group : groups) {
//            Conversation conversation = Conversation.builder()
//                .id("conv_" + group.getId())
//                .type("group")
//                .name(group.getName())
//                .avatar(group.getAvatar())
//                .participants(new ArrayList<>())
//                .unreadCount(0)
//                .pinned(random.nextBoolean())
//                .muted(false)
//                .createdAt(group.getCreatedAt())
//                .build();
//            
//            // 添加所有群成员
//            for (Group.GroupMember member : group.getMembers()) {
//                conversation.getParticipants().add(member.getUserId());
//            }
//            
//            conversation = conversationRepository.save(conversation);
//            
//            // 生成群聊消息
//            String[][] groupMessages = {
//                {"大家好", "今天讨论什么问题？", "我有个想法", "说说看"},
//                {"这个功能怎么做？", "可以用 XXX 实现", "明白谢谢", "不客气"},
//                {"下周一交版有问题吗？", "应该没问题", "好的，辛苦了", "没事"},
//                {"产品需求有变化", "哪里？", "这里要改一下", "收到"},
//            };
//            
//            int msgIndex = random.nextInt(groupMessages.length);
//            String[] messages = groupMessages[msgIndex];
//            
//            long baseTime = conversation.getCreatedAt();
//            List<Message> msgList = new ArrayList<>();
//            
//            for (int i = 0; i < messages.length; i++) {
//                String senderId = group.getMembers().get(i % group.getMembers().size()).getUserId();
//                Message message = Message.builder()
//                    .id("msg_" + UUID.randomUUID().toString().substring(0, 8))
//                    .conversationId(conversation.getId())
//                    .senderId(senderId)
//                    .content(messages[i])
//                    .contentType("text")
//                    .status("read")
//                    .createdAt(baseTime + i * 3600000)
//                    .build();
//                msgList.add(message);
//            }
//            
//            messageRepository.saveAll(msgList);
//            
//            conversation.setLastMessageContent(messages[messages.length - 1]);
//            conversation.setLastMessageAt(baseTime + (messages.length - 1) * 3600000);
//            conversationRepository.save(conversation);
//        }
//        
//        logger.info("创建了群聊会话");
//    }
//}