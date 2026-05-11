package com.chat.service;

import com.chat.document.AdminUser;
import com.chat.document.Message;
import com.chat.document.ServiceSession;
import com.chat.repository.AdminUserRepository;
import com.chat.repository.MessageRepository;
import com.chat.repository.ServiceSessionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

import com.chat.dto.ClientPayload;
import com.chat.dto.ServerPayload;
import com.chat.netty.SessionManager;
import io.netty.channel.Channel;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import com.fasterxml.jackson.databind.ObjectMapper;

@Service
public class ServiceSessionService {

    private static final Logger logger = LoggerFactory.getLogger(ServiceSessionService.class);
    private static final Logger serviceLogger = LoggerFactory.getLogger("SERVICE");
    private static final Logger servicePushLogger = LoggerFactory.getLogger("SERVICE_PUSH");
    private static final Logger userPushLogger = LoggerFactory.getLogger("USER_PUSH");

    private static final String REDIS_SERVICE_ONLINE = "chat:service:online";

    private static Map<String, Object> error(String message) {
        Map<String, Object> map = new HashMap<>();
        map.put("success", false);
        map.put("message", message);
        return map;
    }

    private static Map<String, Object> success() {
        Map<String, Object> map = new HashMap<>();
        map.put("success", true);
        return map;
    }

    private static Map<String, Object> success(Object... keyValues) {
        Map<String, Object> map = new HashMap<>();
        map.put("success", true);
        for (int i = 0; i < keyValues.length; i += 2) {
            map.put((String) keyValues[i], keyValues[i + 1]);
        }
        return map;
    }
    private static final String REDIS_SERVICE_STATUS = "chat:service:status";
    private static final String REDIS_SERVICE_QUEUE = "chat:service:queue";
    private static final String REDIS_SERVICE_QUEUE_ORDER = "chat:service:queue:order";
    private static final String REDIS_SERVICE_SESSION = "chat:service:session";
    private static final String REDIS_SERVICE_CONFIG = "chat:service:config";
    private static final String CACHE_SERVICE_LIST = "chat:service:cache:list";
    private static final String CACHE_SERVICE_STATUS = "chat:service:cache:status";
    private static final long CACHE_TTL_SECONDS = 30;

    @Autowired
    private ServiceSessionRepository sessionRepository;

    @Autowired
    private AdminUserRepository adminUserRepository;

    @Autowired
    private MessageRepository messageRepository;

    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    @Autowired
    private SessionManager sessionManager;

    // ============================================
    // 用户端接口
    // ============================================

    /**
     * 获取客服状态
     */
    public Map<String, Object> getServiceStatus() {
        Map<String, Object> result = new HashMap<>();

        // 获取可用客服数量
        List<Map<String, Object>> availableServices = getAvailableServices();
        long onlineCount = availableServices.size();

        // 获取等待队列长度
        Long queueSize = redisTemplate.opsForList().size(REDIS_SERVICE_QUEUE);
        long waitingCount = queueSize != null ? queueSize : 0;

        result.put("hasAvailableService", onlineCount > 0);
        result.put("onlineCount", onlineCount);
        result.put("waitingCount", waitingCount);
        result.put("availableServices", availableServices);

        return result;
    }

    /**
     * 获取用户历史会话列表
     * @param userId 用户ID
     * @param limit 限制数量
     * @param skip 跳过数量
     * @return 历史会话列表
     */
    public List<Map<String, Object>> getUserHistorySessions(String userId, int limit, int skip) {
        List<ServiceSession> sessions = sessionRepository.findByUserIdOrderByCreatedAtDesc(userId, PageRequest.of(skip / limit, limit));
        serviceLogger.info("[客服] 获取用户历史会话 userId={}, count={}", userId, sessions.size());

        return sessions.stream().map(session -> {
            Map<String, Object> map = new HashMap<>();
            map.put("id", session.getId());
            map.put("userId", session.getUserId());
            map.put("userName", session.getUserName());
            map.put("userAvatar", session.getUserAvatar());
            map.put("serviceId", session.getServiceId());
            map.put("serviceName", session.getServiceName());
            map.put("status", session.getStatus());
            map.put("createdAt", session.getCreatedAt());
            map.put("updatedAt", session.getUpdatedAt());
            map.put("chatStartAt", session.getChatStartAt());
            map.put("rating", session.getRating());
            return map;
        }).collect(Collectors.toList());
    }

    /**
     * 获取历史会话消息
     */
    public Map<String, Object> getHistorySessionMessages(String userId, String sessionId, int limit, int skip) {
        Optional<ServiceSession> sessionOpt = sessionRepository.findById(sessionId);
        if (!sessionOpt.isPresent() || !sessionOpt.get().getUserId().equals(userId)) {
            Map<String, Object> result = new HashMap<>();
            result.put("success", false);
            result.put("message", "会话不存在");
            return result;
        }

        ServiceSession session = sessionOpt.get();
        List<Message> messages = messageRepository.findByConversationIdOrderByCreatedAtAsc(sessionId,
            PageRequest.of(skip / limit, limit));

        List<Map<String, Object>> messageList = messages.stream().map(msg -> {
            Map<String, Object> map = new HashMap<>();
            map.put("id", msg.getId());
            map.put("senderId", msg.getSenderId());
            map.put("content", msg.getContent());
            map.put("contentType", msg.getContentType());
            map.put("status", msg.getStatus());
            map.put("createdAt", msg.getCreatedAt());
            return map;
        }).collect(Collectors.toList());

        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        Map<String, Object> sessionInfo = new HashMap<>();
        sessionInfo.put("id", session.getId());
        sessionInfo.put("serviceId", session.getServiceId());
        sessionInfo.put("serviceName", session.getServiceName());
        sessionInfo.put("status", session.getStatus());
        sessionInfo.put("createdAt", session.getCreatedAt());
        result.put("session", sessionInfo);
        result.put("messages", messageList);
        return result;
    }

    /**
     * 清除服务状态缓存
     */
    public void clearServiceStatusCache() {
        try {
            redisTemplate.delete(CACHE_SERVICE_STATUS);
        } catch (Exception e) {
            // Ignore
        }
    }

    /**
     * 客服列表（包括离线）
     */
    public List<Map<String, Object>> getAllServices() {
        List<AdminUser> services = adminUserRepository.findAll();
        return services.stream()
            .filter(s -> s.getRoles() != null && s.getRoles().contains("service"))
            .map(s -> {
                Map<String, Object> map = new HashMap<>();
                map.put("id", s.getId());
                map.put("nickname", s.getNickname());
                map.put("avatar", s.getAvatar());
                map.put("status", s.getStatus() != null ? s.getStatus() : "offline");
                map.put("maxChats", s.getMaxChats() != null ? s.getMaxChats() : 10);
                map.put("currentChats", s.getCurrentChats() != null ? s.getCurrentChats() : 0);
                return map;
            })
            .collect(Collectors.toList());
    }

    /**
     * 获取可用客服列表
     */
    public List<Map<String, Object>> getAvailableServices() {
        List<AdminUser> services = adminUserRepository.findAll();
        return services.stream()
            .filter(s -> s.getRoles() != null && s.getRoles().contains("service"))
            .filter(s -> "online".equals(s.getStatus()))
            .filter(s -> {
                int max = s.getMaxChats() != null ? s.getMaxChats() : 10;
                int current = s.getCurrentChats() != null ? s.getCurrentChats() : 0;
                return current < max;
            })
            .map(s -> {
                Map<String, Object> map = new HashMap<>();
                map.put("id", s.getId());
                map.put("nickname", s.getNickname());
                map.put("avatar", s.getAvatar());
                map.put("availableSlots", (s.getMaxChats() != null ? s.getMaxChats() : 10) - (s.getCurrentChats() != null ? s.getCurrentChats() : 0));
                return map;
            })
            .collect(Collectors.toList());
    }

/**
     * 用户加入排队队列
     * @param userId 用户ID
     * @param userName 用户昵称
     * @param userAvatar 用户头像
     * @return 排队信息 {success, status, position, queueSize, estimatedWait}
     */
    public Map<String, Object> joinQueue(String userId, String userName, String userAvatar) {
        serviceLogger.info("[客服] 用户加入队列 userId={}, userName={}", userId, userName);

        // 1. 检查是否有进行中的会话 (chatting) - 复用
        Optional<ServiceSession> chattingSession = sessionRepository.findByUserIdAndStatus(userId, "chatting");
        if (chattingSession.isPresent()) {
            serviceLogger.info("[客服] 用户已有进行中会话，复用 sessionId={}", chattingSession.get().getId());
            Map<String, Object> queueInfo = getQueueInfo(userId);
            pushToUser(userId, "service_queue_update", queueInfo);
            Map<String, Object> result = new HashMap<>(queueInfo);
            result.put("success", true);
            result.put("reuse", true);
            result.put("message", "继续当前会话");
            return result;
        }

        // 2. 检查是否在队列中 (waiting) - 复用
        Optional<ServiceSession> waitingSession = sessionRepository.findByUserIdAndStatus(userId, "waiting");
        if (waitingSession.isPresent()) {
            serviceLogger.info("[客服] 用户已在队列中，复用 sessionId={}", waitingSession.get().getId());
            // 刷新 Redis 队列时间戳
            redisTemplate.opsForZSet().remove(REDIS_SERVICE_QUEUE_ORDER, userId);
            redisTemplate.opsForZSet().add(REDIS_SERVICE_QUEUE_ORDER, userId, System.currentTimeMillis());
            // 更新用户信息
            ServiceSession session = waitingSession.get();
            if (userName != null) session.setUserName(userName);
            if (userAvatar != null) session.setUserAvatar(userAvatar);
            session.setUpdatedAt(System.currentTimeMillis());
            sessionRepository.save(session);
            // 推送状态
            Map<String, Object> queueInfo = getQueueInfo(userId);
            pushToUser(userId, "service_queue_update", queueInfo);
            Map<String, Object> result = new HashMap<>(queueInfo);
            result.put("success", true);
            result.put("reuse", true);
            result.put("message", "继续等待");
            return result;
        }

        // 3. 清理 Redis 旧队列数据（如果有的话）
        redisTemplate.opsForZSet().remove(REDIS_SERVICE_QUEUE_ORDER, userId);
        redisTemplate.opsForList().remove(REDIS_SERVICE_QUEUE, 0, userId + "|");

        // 4. 检查队列是否已满
        Long queueSize = redisTemplate.opsForZSet().zCard(REDIS_SERVICE_QUEUE_ORDER);
        if (queueSize != null && queueSize >= 100) {
            serviceLogger.warn("[客服] 队列已满 queueSize={}", queueSize);
            return error("客服忙碌，请稍后再试");
        }

        // 5. 加入队列 (Sorted Set: score = timestamp for ordering)
        long timestamp = System.currentTimeMillis();
        redisTemplate.opsForZSet().add(REDIS_SERVICE_QUEUE_ORDER, userId, timestamp);
        serviceLogger.debug("[客服] 用户加入Redis队列 userId={}, timestamp={}", userId, timestamp);

        // 6. 创建新会话记录（用户信息存储在 MongoDB 中）
        Long position = getQueuePosition(userId);
        // 使用时间戳+随机生成唯一会话ID，保留历史会话
        String sessionId = "service_" + userId + "_" + System.currentTimeMillis();
        ServiceSession session = ServiceSession.builder()
            .id(sessionId)
            .userId(userId)
            .userName(userName != null ? userName : "用户")
            .userAvatar(userAvatar)
            .status("waiting")
            .type("service")
            .waitingStartAt(System.currentTimeMillis())
            .unreadCount(0)
            .createdAt(System.currentTimeMillis())
            .updatedAt(System.currentTimeMillis())
            .build();
        sessionRepository.save(session);
        serviceLogger.debug("[客服] 创建新会话记录 sessionId={}, position={}", sessionId, position);

        // 清除缓存
        clearServiceStatusCache();

        // 推送排队状态给用户
        Map<String, Object> queueInfo = getQueueInfo(userId);
        pushToUser(userId, "service_queue_update", queueInfo);
        serviceLogger.info("[客服] 用户加入队列成功 userId={}, position={}, status={}", userId, position, queueInfo.get("status"));

        // 返回排队信息
        Map<String, Object> result = new HashMap<>(queueInfo);
        result.put("success", true);
        return result;
    }

    /**
     * 获取用户排队/会话状态信息
     * @param userId 用户ID
     * @return 状态信息 {status, position, queueSize, estimatedWait, sessionId, serviceId, serviceName}
     */
    public Map<String, Object> getQueueInfo(String userId) {
        // 检查是否有进行中的会话
        Optional<ServiceSession> chattingSession = sessionRepository.findByUserIdAndStatus(userId, "chatting");
        if (chattingSession.isPresent()) {
            ServiceSession session = chattingSession.get();
            logger.debug("[客服] 用户有进行中会话 userId={}, sessionId={}", userId, session.getId());
            Map<String, Object> result = new HashMap<>();
            result.put("status", "chatting");
            result.put("sessionId", session.getId());
            result.put("serviceId", session.getServiceId());
            result.put("serviceName", session.getServiceName());
            return result;
        }

        // 检查是否在队列中
        Long position = getQueuePosition(userId);
        if (position == null) {
            logger.debug("[客服] 用户不在线 userId={}", userId);
            Map<String, Object> offlineResult = new HashMap<>();
            offlineResult.put("status", "offline");
            return offlineResult;
        }

        // 等待队列中
        Long queueSize = redisTemplate.opsForZSet().zCard(REDIS_SERVICE_QUEUE_ORDER);
        int estimatedWait = (int) ((position + 1) * 3); // 假设每人3分钟

        logger.debug("[客服] 用户在等待队列 userId={}, position={}, queueSize={}", userId, position, queueSize);
        Map<String, Object> waitingMap = new HashMap<>();
        waitingMap.put("status", "waiting");
        waitingMap.put("position", position);
        waitingMap.put("queueSize", queueSize != null ? queueSize : 0);
        waitingMap.put("estimatedWait", estimatedWait + "分钟");
        return waitingMap;
    }

    /**
     * 离开等待队列
     */
    public boolean leaveQueue(String userId) {
        logger.info("[客服] 用户离开队列 userId={}", userId);

        // 从 Redis 队列中移除 (Sorted Set only)
        redisTemplate.opsForZSet().remove(REDIS_SERVICE_QUEUE_ORDER, userId);

        // 更新会话状态
        sessionRepository.findByUserIdAndStatus(userId, "waiting").ifPresent(session -> {
            session.setStatus("finished");
            session.setEndedAt(System.currentTimeMillis());
            sessionRepository.save(session);
            logger.debug("[客服] 更新会话状态为finished sessionId={}", session.getId());
        });

        // 推送排队状态
        Map<String, Object> queueInfo = getQueueInfo(userId);
        pushToUser(userId, "service_queue_update", queueInfo);

        logger.info("[客服] 用户离开队列成功 userId={}", userId);
        return true;
    }

    /**
     * 获取用户在队列中的位置（从1开始）
     * @param userId 用户ID
     * @return 位置/null（不在队列中）
     */
    private Long getQueuePosition(String userId) {
        Long rank = redisTemplate.opsForZSet().rank(REDIS_SERVICE_QUEUE_ORDER, userId);
        return rank != null ? rank + 1 : null; // 转换为从1开始
    }

    /**
     * 获取等待队列列表
     * @return 队列用户列表和统计信息
     */
    public Map<String, Object> getQueueList() {
        Map<String, Object> result = new HashMap<>();
        Set<String> userIds = redisTemplate.opsForZSet().range(REDIS_SERVICE_QUEUE_ORDER, 0, 9);
        Long totalSize = redisTemplate.opsForZSet().zCard(REDIS_SERVICE_QUEUE_ORDER);

        List<Map<String, Object>> users = new ArrayList<>();
        if (userIds != null && !userIds.isEmpty()) {
            // 批量查询用户信息
            List<ServiceSession> sessions = sessionRepository.findByUserIdInAndStatus(userIds, "waiting");
            // 构建 userId -> session 的映射
            Map<String, ServiceSession> sessionMap = sessions.stream()
                .collect(Collectors.toMap(ServiceSession::getUserId, s -> s));
            
            // 按原始顺序返回
            for (String userId : userIds) {
                ServiceSession session = sessionMap.get(userId);
                if (session != null) {
                    Map<String, Object> user = new HashMap<>();
                    user.put("userId", userId);
                    user.put("userName", session.getUserName());
                    user.put("userAvatar", session.getUserAvatar() != null ? session.getUserAvatar() : "");
                    users.add(user);
                }
            }
        }

        result.put("total", totalSize != null ? totalSize : 0);
        result.put("users", users);
        return result;
    }

    // ============================================
    // 客服端接口
    // ============================================

    /**
     * 客服获取下一个等待用户（自动分配）
     * @param serviceId 客服ID
     * @return 分配的用户信息 {success, userId, userName, userAvatar, sessionId}
     */
    public Map<String, Object> getNextUser(String serviceId) {
        logger.info("[客服] 客服获取下一个用户 serviceId={}", serviceId);

        // 检查客服状态
        AdminUser service = adminUserRepository.findById(serviceId).orElse(null);
        if (service == null || !"online".equals(service.getStatus())) {
            logger.warn("[客服] 客服不在线 serviceId={}", serviceId);
            return error("客服不在线");
        }

        // 检查是否已达接待上限
        int maxChats = service.getMaxChats() != null ? service.getMaxChats() : 10;
        int currentChats = service.getCurrentChats() != null ? service.getCurrentChats() : 0;
        if (currentChats >= maxChats) {
            logger.warn("[客服] 客服已达接待上限 serviceId={}, current={}, max={}", serviceId, currentChats, maxChats);
            return error("已达接待上限");
        }

        // 从队列中取用户 (Sorted Set: 取最早加入的)
        Set<ZSetOperations.TypedTuple<String>> result = redisTemplate.opsForZSet().popMin(REDIS_SERVICE_QUEUE_ORDER, 1);
        if (result == null || result.isEmpty()) {
            logger.debug("[客服] 暂无等待用户 serviceId={}", serviceId);
            return error("暂无等待用户");
        }
        String userId = result.iterator().next().getValue();
        logger.info("[客服] 分配用户 userId={} -> serviceId={}", userId, serviceId);

        // 从 MongoDB 获取用户详情
        Optional<ServiceSession> sessionOpt = sessionRepository.findByUserIdAndStatus(userId, "waiting");
        String userName = "用户";
        String userAvatar = "";
        ServiceSession session;
        if (sessionOpt.isPresent()) {
            // 使用现有会话
            session = sessionOpt.get();
            userName = session.getUserName() != null ? session.getUserName() : "用户";
            userAvatar = session.getUserAvatar() != null ? session.getUserAvatar() : "";
        } else {
            // 创建新会话（使用新格式 sessionId）
            String sessionId = "service_" + userId + "_" + System.currentTimeMillis();
            session = ServiceSession.builder()
                .id(sessionId)
                .userId(userId)
                .userName(userName)
                .userAvatar(userAvatar)
                .build();
        }

        session.setServiceId(serviceId);
        session.setServiceName(service.getNickname());
        session.setStatus("chatting");
        session.setChatStartAt(System.currentTimeMillis());
        session.setLastMessageAt(System.currentTimeMillis());
        session.setUpdatedAt(System.currentTimeMillis());
        sessionRepository.save(session);

        // 推送会话开始消息给用户
        Map<String, Object> startedData = new HashMap<>();
        startedData.put("sessionId", session.getId());
        startedData.put("serviceId", serviceId);
        startedData.put("serviceName", service.getNickname());
        pushToUser(userId, "service_session_started", startedData);

        // 更新客服接待数
        service.setCurrentChats(currentChats + 1);
        adminUserRepository.save(service);

        // 保存会话关系到 Redis
        redisTemplate.opsForHash().put(REDIS_SERVICE_SESSION, userId, serviceId);

        // 创建会话
        createServiceConversation(userId, serviceId);

        return success(
            "sessionId", session.getId(),
            "userId", userId,
            "userName", userName,
            "userAvatar", userAvatar
        );
    }

    /**
     * 获取所有客服会话列表（所有管理员都能查看）
     * @param serviceId 如果指定，则只返回该客服的会话
     */
    public Map<String, Object> getAllServiceSessions(int page, int limit, String status, String serviceId) {
        int maxLimit = 100;
        int effectiveLimit = Math.min(limit, maxLimit);
        
        // 按创建时间倒序排序（最新在前）
        Sort sort = Sort.by(Sort.Direction.DESC, "createdAt");
        PageRequest pageRequest = PageRequest.of(page, effectiveLimit, sort);

        Page<ServiceSession> sessionPage;
        
        // 优先按 serviceId 过滤
        if (serviceId != null && !serviceId.isEmpty()) {
            if (status != null && !status.isEmpty()) {
                sessionPage = sessionRepository.findByServiceIdAndStatus(serviceId, status, pageRequest);
            } else {
                sessionPage = sessionRepository.findByServiceId(serviceId, pageRequest);
            }
        } else if (status != null && !status.isEmpty()) {
            sessionPage = sessionRepository.findByStatusOrderByCreatedAtDesc(status, pageRequest);
        } else {
            // 默认查看所有会话
            sessionPage = sessionRepository.findAll(pageRequest);
        }

        List<Map<String, Object>> list = sessionPage.getContent().stream().map(s -> {
            Map<String, Object> map = new HashMap<>();
            map.put("id", s.getId());
            map.put("userId", s.getUserId());
            map.put("userName", s.getUserName());
            map.put("userAvatar", s.getUserAvatar());
            map.put("serviceId", s.getServiceId());
            map.put("serviceName", s.getServiceName());
            map.put("status", s.getStatus());
            map.put("createdAt", s.getCreatedAt());
            map.put("chatStartAt", s.getChatStartAt());
            map.put("endedAt", s.getEndedAt());
            map.put("lastMessageAt", s.getLastMessageAt());
            map.put("unreadCount", s.getUnreadCount() != null ? s.getUnreadCount() : 0);
            map.put("rating", s.getRating());
            map.put("ratingComment", s.getRatingComment());
            map.put("internalNote", s.getInternalNote());
            map.put("tags", s.getTags());
            return map;
        }).collect(Collectors.toList());

        Map<String, Object> result = new HashMap<>();
        result.put("sessions", list);
        result.put("total", sessionPage.getTotalElements());
        result.put("page", page);
        result.put("limit", effectiveLimit);
        return result;
    }

    /**
     * 获取会话消息
     */
    public List<Map<String, Object>> getSessionMessages(String sessionId, int limit, int skip) {
        // 直接使用 sessionId 作为 conversationId
        ServiceSession session = sessionRepository.findById(sessionId).orElse(null);

        // 升序排列（最旧的在前面，最新的在后面）
        PageRequest pageRequest = PageRequest.of(skip / limit, limit, Sort.by(Sort.Direction.ASC, "createdAt"));
        List<Message> messages = messageRepository.findByConversationId(sessionId, pageRequest).getContent();

        // 获取客服名称用于显示发送者名称
        final String serviceName = session != null ? session.getServiceName() : "客服";

        return messages.stream().map(m -> {
            Map<String, Object> map = new HashMap<>();
            map.put("id", m.getId());
            map.put("senderId", m.getSenderId());
            map.put("content", m.getContent());
            map.put("contentType", m.getContentType());
            map.put("status", m.getStatus());
            map.put("createdAt", m.getCreatedAt());
            // 判断是用户还是客服
            boolean isService = session != null && m.getSenderId().equals(session.getServiceId());
            map.put("isService", isService);
            map.put("senderName", isService ? serviceName : (session != null ? session.getUserName() : "用户"));
            // 用户头像
            if (!isService && session != null) {
                map.put("senderAvatar", session.getUserAvatar());
            }
            return map;
        }).collect(Collectors.toList());
    }

    /**
     * 获取客服离线消息（客服重新连接时调用）
     * 查询该客服所有进行中的会话，获取最近的消息
     * @param serviceId 客服ID
     * @return 离线消息列表
     */
    public List<Map<String, Object>> getOfflineMessages(String serviceId) {
        List<Map<String, Object>> offlineMessages = new ArrayList<>();

        // 查询该客服所有进行中的会话
        List<ServiceSession> activeSessions = sessionRepository.findByServiceIdAndStatus(serviceId, "chatting");
        serviceLogger.info("[客服] 获取离线消息 serviceId={}, activeSessions={}", serviceId, activeSessions.size());

        for (ServiceSession session : activeSessions) {
            String sessionId = session.getId();

            // 获取会话开始后的所有消息（未读消息）
            Long chatStartAt = session.getChatStartAt();
            if (chatStartAt == null) {
                chatStartAt = 0L;
            }

            // 查询会话开始后的所有消息（使用 sessionId）
            List<Message> messages = messageRepository.findByConversationIdAndCreatedAtGreaterThan(
                sessionId, chatStartAt);

            for (Message msg : messages) {
                Map<String, Object> msgMap = new HashMap<>();
                msgMap.put("sessionId", sessionId);
                msgMap.put("message", convertMessageToMap(msg, session));
                offlineMessages.add(msgMap);
            }
        }

        serviceLogger.info("[客服] 离线消息数量 serviceId={}, count={}", serviceId, offlineMessages.size());
        return offlineMessages;
    }

    /**
     * 发送客服会话消息（无附件）
     * @param sessionId 会话ID
     * @param senderId 发送者ID
     * @param content 消息内容
     * @param contentType 内容类型
     * @return 消息对象
     */
    public Message sendMessage(String sessionId, String senderId, String content, String contentType) {
        return sendMessage(sessionId, senderId, content, contentType, null, null);
    }

    /**
     * 发送客服会话消息（带附件）
     * @param sessionId 会话ID
     * @param senderId 发送者ID（用户ID或客服ID）
     * @param content 消息内容
     * @param contentType 内容类型
     * @param fileUrl 附件URL（可选）
     * @param fileName 文件名（可选）
     * @return 消息对象
     */
    public Message sendMessage(String sessionId, String senderId, String content, String contentType, String fileUrl, String fileName) {
        logger.info("[客服] 发送消息 sessionId={}, senderId={}, contentType={}", sessionId, senderId, contentType);

        // 直接使用 sessionId 作为 conversationId
        ServiceSession session = sessionRepository.findById(sessionId).orElse(null);
        if (session == null) {
            logger.warn("[客服] 会话不存在 sessionId={}", sessionId);
            return null;
        }

        String finalContent = content;
        if (fileUrl != null && !fileUrl.isEmpty()) {
            finalContent = content != null ? content : "";
            if (!finalContent.isEmpty()) {
                finalContent += "\n";
            }
            finalContent += "[文件:" + (fileName != null ? fileName : "attachment") + "](" + fileUrl + ")";
            contentType = "file";
        }

        Message message = Message.builder()
            .id("msg_" + System.currentTimeMillis())
            .conversationId(sessionId)
            .conversationType("service")
            .senderId(senderId)
            .content(finalContent)
            .contentType(contentType != null ? contentType : "text")
            .status("sent")
            .createdAt(System.currentTimeMillis())
            .build();
        messageRepository.save(message);
        serviceLogger.info("[客服] 消息已存储 messageId={}, conversationId={}, senderId={}", message.getId(), message.getConversationId(), message.getSenderId());

        // 更新会话最后消息时间
        session.setLastMessageAt(System.currentTimeMillis());
        session.setUpdatedAt(System.currentTimeMillis());
        sessionRepository.save(session);

        // 推送消息给另一方
        String targetUserId;
        boolean fromService;
        String senderUserId = session.getUserId();
        String serviceId = session.getServiceId();

        if (senderId.equals(senderUserId)) {
            // 用户发的消息 → 推送给客服
            targetUserId = serviceId;
            fromService = false;
        } else if (serviceId != null && senderId.equals(serviceId)) {
            // 客服发的消息 → 推送给用户
            targetUserId = senderUserId;
            fromService = true;
        } else {
            serviceLogger.warn("[客服] 消息发送者无法识别 senderId={}, session userId={}, serviceId={}",
                senderId, senderUserId, serviceId);
            return message;
        }

        if (targetUserId != null) {
            Map<String, Object> messageMap = convertMessageToMap(message, session);
            pushMessageToUser(targetUserId, sessionId, messageMap, fromService);
        } else {
            serviceLogger.warn("[客服] 消息发送成功但目标用户ID为空 sessionId={}", sessionId);
        }

        return message;
    }

    /**
     * 转换消息为 Map（包含完整信息，供外部调用）
     */
    public Map<String, Object> convertMessageToMapWithSession(String sessionId, Message msg) {
        ServiceSession session = sessionRepository.findById(sessionId).orElse(null);
        return convertMessageToMap(msg, session);
    }

    private Map<String, Object> convertMessageToMap(Message msg) {
        Map<String, Object> map = new HashMap<>();
        map.put("id", msg.getId());
        map.put("senderId", msg.getSenderId());
        map.put("content", msg.getContent());
        map.put("contentType", msg.getContentType());
        map.put("createdAt", msg.getCreatedAt());
        return map;
    }

    /**
     * 转换消息为 Map（包含会话信息）
     */
    private Map<String, Object> convertMessageToMap(Message msg, ServiceSession session) {
        Map<String, Object> map = new HashMap<>();
        map.put("id", msg.getId());
        map.put("senderId", msg.getSenderId());
        map.put("content", msg.getContent());
        map.put("contentType", msg.getContentType());
        map.put("status", msg.getStatus());
        map.put("createdAt", msg.getCreatedAt());

        // 判断是用户还是客服
        boolean isService = session != null && msg.getSenderId().equals(session.getServiceId());
        map.put("isService", isService);
        map.put("senderName", isService ? session.getServiceName() : session.getUserName());

        // 用户头像
        if (!isService && session != null) {
            map.put("senderAvatar", session.getUserAvatar());
        }

        return map;
    }

    /**
     * 转移会话
     */
    /**
     * 转接会话（从一个客服转移到另一个客服）
     * @param sessionId 会话ID
     * @param fromServiceId 原客服ID
     * @param toServiceId 目标客服ID
     * @return 转接结果 {success, serviceName}
     */
    public Map<String, Object> transferSession(String sessionId, String fromServiceId, String toServiceId) {
        logger.info("[客服] 转接会话 sessionId={}, from={}, to={}", sessionId, fromServiceId, toServiceId);

        Optional<ServiceSession> sessionOpt = sessionRepository.findById(sessionId);
        if (!sessionOpt.isPresent()) {
            logger.warn("[客服] 转接失败，会话不存在 sessionId={}", sessionId);
            return error("会话不存在");
        }

        Optional<AdminUser> toServiceOpt = adminUserRepository.findById(toServiceId);
        if (!toServiceOpt.isPresent()) {
            logger.warn("[客服] 转接失败，目标客服不存在 toServiceId={}", toServiceId);
            return error("目标客服不存在");
        }

        AdminUser toService = toServiceOpt.get();
        if (!"online".equals(toService.getStatus())) {
            logger.warn("[客服] 转接失败，目标客服不在线 toServiceId={}", toServiceId);
            return error("目标客服不在线");
        }

        int maxChats = toService.getMaxChats() != null ? toService.getMaxChats() : 10;
        int currentChats = toService.getCurrentChats() != null ? toService.getCurrentChats() : 0;
        if (currentChats >= maxChats) {
            logger.warn("[客服] 转接失败，目标客服已达接待上限 toServiceId={}", toServiceId);
            return error("目标客服已达接待上限");
        }

        ServiceSession session = sessionOpt.get();

        // 更新原客服接待数
        adminUserRepository.findById(fromServiceId).ifPresent(fromService -> {
            int fromCurrent = fromService.getCurrentChats() != null ? fromService.getCurrentChats() : 0;
            fromService.setCurrentChats(Math.max(0, fromCurrent - 1));
            adminUserRepository.save(fromService);
            logger.debug("[客服] 原客服接待数-1 serviceId={}, current={}", fromServiceId, Math.max(0, fromCurrent - 1));
        });

        // 更新目标客服接待数
        toService.setCurrentChats(currentChats + 1);
        adminUserRepository.save(toService);

        // 更新会话
        session.setServiceId(toServiceId);
        session.setServiceName(toService.getNickname());
        session.setUpdatedAt(System.currentTimeMillis());
        sessionRepository.save(session);

        // 更新 Redis
        redisTemplate.opsForHash().put(REDIS_SERVICE_SESSION, session.getUserId(), toServiceId);

        // 推送转移通知给用户
        // 推送转移通知给用户
        Map<String, Object> transferredData = new HashMap<>();
        transferredData.put("sessionId", sessionId);
        transferredData.put("serviceId", toServiceId);
        transferredData.put("serviceName", toService.getNickname());
        pushToUser(session.getUserId(), "service_session_transferred", transferredData);

        return success("serviceName", toService.getNickname());
    }

    /**
     * 结束客服会话
     * @param sessionId 会话ID
     * @param serviceId 客服ID
     * @return 是否成功
     */
    public boolean endSession(String sessionId, String serviceId) {
        logger.info("[客服] 结束会话 sessionId={}, serviceId={}", sessionId, serviceId);

        Optional<ServiceSession> sessionOpt = sessionRepository.findById(sessionId);
        if (!sessionOpt.isPresent()) {
            logger.warn("[客服] 会话不存在 sessionId={}", sessionId);
            return false;
        }

        ServiceSession session = sessionOpt.get();
        
        // 获取实际客服ID（如果参数为 null，从会话中获取）
        String actualServiceId = serviceId != null ? serviceId : session.getServiceId();
        
        session.setStatus("finished");
        session.setEndedAt(System.currentTimeMillis());
        session.setUpdatedAt(System.currentTimeMillis());
        sessionRepository.save(session);

        // 更新客服接待数
        if (actualServiceId != null) {
            adminUserRepository.findById(actualServiceId).ifPresent(service -> {
                int current = service.getCurrentChats() != null ? service.getCurrentChats() : 0;
                service.setCurrentChats(Math.max(0, current - 1));
                adminUserRepository.save(service);
                logger.debug("[客服] 更新客服接待数 serviceId={}, currentChats={}", actualServiceId, Math.max(0, current - 1));
            });
        }

        // 清除 Redis 会话关系
        redisTemplate.opsForHash().delete(REDIS_SERVICE_SESSION, session.getUserId());

        // 推送会话结束消息给用户
        Map<String, Object> endedData = new HashMap<>();
        endedData.put("sessionId", sessionId);
        pushToUser(session.getUserId(), "service_session_ended", endedData);
        
        // 推送会话结束消息给客服
        if (actualServiceId != null) {
            pushToUser(actualServiceId, "service_session_ended", endedData);
        }

        // 清除缓存
        clearServiceStatusCache();

        logger.info("[客服] 会话已结束 sessionId={}, userId={}, serviceId={}", sessionId, session.getUserId(), actualServiceId);
        return true;
    }

    /**
     * 评价会话
     * @param userId 用户ID
     * @param rating 评分（1-5）
     * @param comment 评价内容
     * @return 是否成功
     */
    public boolean rateSession(String userId, Integer rating, String comment) {
        List<ServiceSession> sessions = sessionRepository.findByUserIdOrderByCreatedAtDesc(userId, PageRequest.of(0, 10));
        ServiceSession session = sessions.stream()
            .filter(s -> "finished".equals(s.getStatus()))
            .findFirst()
            .orElse(null);

        if (session == null) {
            return false;
        }

        session.setRating(rating);
        session.setRatingComment(comment);
        sessionRepository.save(session);

        return true;
    }

    /**
     * 标记用户已读
     */
    public void markUserRead(String userId) {
        sessionRepository.findByUserIdAndStatus(userId, "chatting").ifPresent(session -> {
            session.setUserUnreadCount(0);
            sessionRepository.save(session);
        });
    }

    /**
     * 重置用户会话状态（清理旧数据）
     */
    public boolean resetUserSession(String userId) {
        // 清理 Redis 队列 (Sorted Set only)
        redisTemplate.opsForZSet().remove(REDIS_SERVICE_QUEUE_ORDER, userId);
        
        // 清理旧的 List 格式数据 (兼容性)
        redisTemplate.opsForList().remove(REDIS_SERVICE_QUEUE, 0, userId + "|");
        
        redisTemplate.opsForHash().delete(REDIS_SERVICE_SESSION, userId);

        // 清理 MongoDB 中的所有会话状态
        sessionRepository.findByUserId(userId).ifPresent(session -> {
            session.setStatus("finished");
            session.setEndedAt(System.currentTimeMillis());
            session.setUpdatedAt(System.currentTimeMillis());
            sessionRepository.save(session);
        });

        // 也检查 waiting 状态
        sessionRepository.findByUserIdAndStatus(userId, "waiting").ifPresent(session -> {
            session.setStatus("finished");
            session.setEndedAt(System.currentTimeMillis());
            session.setUpdatedAt(System.currentTimeMillis());
            sessionRepository.save(session);
        });

        clearServiceStatusCache();
        return true;
    }

    /**
     * 标记客服已读
     */
    public void markServiceRead(String sessionId) {
        sessionRepository.findById(sessionId).ifPresent(session -> {
            session.setServiceUnreadCount(0);
            sessionRepository.save(session);
        });
    }

    /**
     * 更新会话备注
     */
    public boolean updateSessionNote(String sessionId, String note) {
        Optional<ServiceSession> sessionOpt = sessionRepository.findById(sessionId);
        if (!sessionOpt.isPresent()) {
            return false;
        }
        ServiceSession session = sessionOpt.get();
        session.setInternalNote(note);
        sessionRepository.save(session);
        return true;
    }

    /**
     * 更新会话标签
     */
    public boolean updateSessionTags(String sessionId, List<String> tags) {
        Optional<ServiceSession> sessionOpt = sessionRepository.findById(sessionId);
        if (!sessionOpt.isPresent()) {
            return false;
        }
        ServiceSession session = sessionOpt.get();
        session.setTags(tags);
        sessionRepository.save(session);
        return true;
    }

    /**
     * 创建客服会话（复用 Message 表）
     */
    private void createServiceConversation(String userId, String serviceId) {
        String conversationId = "service_" + userId;
    }

    /**
     * 通知在线客服有新用户加入
     */
    public void notifyServiceNewUser(String userId, String userName) {
        String channel = "chat:service:new_user";
        redisTemplate.convertAndSend(channel, userId + "|" + userName);
    }

    /**
     * 获取服务统计
     */
    public Map<String, Object> getServiceStats() {
        Map<String, Object> stats = new HashMap<>();
        
        // 总会话数
        long totalSessions = sessionRepository.count();
        stats.put("totalSessions", totalSessions);

        // 进行中的会话
        long activeSessions = sessionRepository.countByStatus("chatting");
        stats.put("activeSessions", activeSessions);

        // 已完成的会话
        long finishedSessions = sessionRepository.countByStatus("finished");
        stats.put("finishedSessions", finishedSessions);

        // 等待中的会话
        long waitingSessions = sessionRepository.countByStatus("waiting");
        stats.put("waitingSessions", waitingSessions);

        // 在线客服数
        List<AdminUser> onlineServices = adminUserRepository.findAll().stream()
            .filter(s -> s.getRoles() != null && s.getRoles().contains("service"))
            .filter(s -> "online".equals(s.getStatus()))
            .collect(Collectors.toList());
        stats.put("onlineServices", onlineServices.size());

        // 等待队列长度
        Long queueSize = redisTemplate.opsForList().size(REDIS_SERVICE_QUEUE);
        stats.put("queueSize", queueSize != null ? queueSize : 0);

        return stats;
    }

    /**
     * 获取客服绩效统计
     */
    public Map<String, Object> getServicePerformance(String serviceId) {
        Map<String, Object> performance = new HashMap<>();
        
        // 获取该客服所有会话
        List<ServiceSession> allSessions = sessionRepository.findByServiceId(serviceId);
        
        long totalSessions = allSessions.size();
        long finishedSessions = allSessions.stream().filter(s -> "finished".equals(s.getStatus())).count();
        
        // 计算平均响应时间、满意度等
        performance.put("totalSessions", totalSessions);
        performance.put("finishedSessions", finishedSessions);
        
        // 计算平均会话时长
        List<Long> durations = allSessions.stream()
            .filter(s -> s.getChatStartAt() != null && s.getEndedAt() != null)
            .map(s -> s.getEndedAt() - s.getChatStartAt())
            .collect(Collectors.toList());
        
        if (!durations.isEmpty()) {
            long avgDuration = durations.stream().reduce(0L, Long::sum) / durations.size();
            performance.put("avgDuration", avgDuration / 1000 / 60); // 分钟
        } else {
            performance.put("avgDuration", 0);
        }

        return performance;
    }

    /**
     * 推送消息给指定用户（通用方法）
     * @param userId 用户ID
     * @param type 消息类型（如service_queue_update, service_session_started等）
     * @param data 消息数据
     */
    public void pushToUser(String userId, String type, Map<String, Object> data) {
        Channel channel = sessionManager.getChannelByUserId(userId);
        if (channel != null && channel.isActive()) {
            ServerPayload payload = new ServerPayload();
            payload.setType(type);
            payload.setPayload(data);
            payload.setTimestamp(System.currentTimeMillis());
            channel.writeAndFlush(payload);

            // 记录到对应日志文件
            if ("service_new_message".equals(type)) {
                servicePushLogger.info("[客服->用户] userId={}, type={}", userId, type);
            } else {
                servicePushLogger.info("[客服推送] userId={}, type={}", userId, type);
            }
        } else {
            serviceLogger.info("[客服] 用户不在线，无法推送 userId={}", userId);
        }
    }

    /**
     * 推送客服新消息给用户（专用方法）
     * @param targetUserId 目标用户ID
     * @param sessionId 会话ID
     * @param messageMap 消息数据
     * @param fromService true=客服发的，false=用户发的
     */
    private void pushMessageToUser(String targetUserId, String sessionId, Map<String, Object> messageMap, boolean fromService) {
        Channel channel = sessionManager.getChannelByUserId(targetUserId);
        if (channel != null && channel.isActive()) {
            ServerPayload payload = new ServerPayload();
            payload.setType("service_new_message");
            Map<String, Object> pushData = new HashMap<>();
            pushData.put("sessionId", sessionId);
            pushData.put("message", messageMap);
            payload.setPayload(pushData);
            payload.setTimestamp(System.currentTimeMillis());
            channel.writeAndFlush(payload);

            // 记录到对应日志文件
            if (fromService) {
                servicePushLogger.info("[客服->用户] sessionId={}, userId={}, content={}",
                    sessionId, targetUserId, messageMap.get("content"));
            } else {
                userPushLogger.info("[用户->客服] sessionId={}, userId={}, content={}",
                    sessionId, targetUserId, messageMap.get("content"));
            }
        } else {
            serviceLogger.info("[客服] 用户不在线，无法推送消息 userId={}", targetUserId);
        }
    }

    /**
     * 推送用户正在输入提示给客服
     * @param serviceId 客服ID
     */
    public void pushTypingToUser(String serviceId) {
        List<ServiceSession> sessions = sessionRepository.findByServiceIdAndStatus(serviceId, "chatting");
        if (!sessions.isEmpty()) {
            String userId = sessions.get(0).getUserId();
            Map<String, Object> typingData = new HashMap<>();
            typingData.put("serviceId", serviceId);
            pushToUser(userId, "service_typing", typingData);
        }
    }
}