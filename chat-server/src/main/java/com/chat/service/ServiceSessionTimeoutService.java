package com.chat.service;

import com.chat.document.ServiceSession;
import com.chat.repository.ServiceSessionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 客服会话超时服务
 * 定期检查并处理超时会话
 * 
 * 超时配置:
 * - USER_TIMEOUT: 用户30分钟无活动自动结束会话
 * - QUEUE_TIMEOUT: 排队等待超过15分钟自动离开队列
 * 
 * 检查频率: 每60秒执行一次 (@Scheduled(fixedRate = 60000))
 */
@Service
public class ServiceSessionTimeoutService {

    private static final Logger logger = LoggerFactory.getLogger(ServiceSessionTimeoutService.class);

    /**
     * 用户无活动超时时间（毫秒）
     * 30分钟 = 30 * 60 * 1000
     */
    private static final long USER_TIMEOUT = 30 * 60 * 1000;

    /**
     * 排队等待超时时间（毫秒）
     * 15分钟 = 15 * 60 * 1000
     */
    private static final long QUEUE_TIMEOUT = 15 * 60 * 1000;

    @Autowired
    private ServiceSessionService serviceSessionService;

    @Autowired
    private ServiceSessionRepository sessionRepository;

    /**
     * 超时检查主方法
     * 每60秒执行一次，检查所有超时会话
     * 1. 检查排队超时用户 -> 自动离开队列
     * 2. 检查聊天超时会话 -> 自动结束会话
     */
    @Scheduled(fixedRate = 60000) // 每分钟检查一次
    public void checkTimeouts() {
        logger.info("=== ServiceSessionTimeoutService checking timeouts ===");
        checkWaitingTimeout();
        checkChattingTimeout();
    }

    /**
     * 检查排队超时用户
     * 遍历所有 waiting 状态的会话，超过15分钟自动离开队列
     */
    private void checkWaitingTimeout() {
        List<ServiceSession> waitingSessions = sessionRepository.findByStatusOrderByCreatedAtAsc("waiting");
        long now = System.currentTimeMillis();

        for (ServiceSession session : waitingSessions) {
            if (session.getWaitingStartAt() != null && 
                now - session.getWaitingStartAt() > QUEUE_TIMEOUT) {
                logger.info("User {} waited too long, removing from queue", session.getUserId());
                serviceSessionService.leaveQueue(session.getUserId());
            }
        }
    }

    /**
     * 检查聊天超时会话
     * 遍历所有 chatting 状态的会话，超过30分钟无消息自动结束
     */
    private void checkChattingTimeout() {
        List<ServiceSession> allSessions = sessionRepository.findAll();
        long now = System.currentTimeMillis();

        for (ServiceSession session : allSessions) {
            if ("chatting".equals(session.getStatus()) && session.getLastMessageAt() != null) {
                if (now - session.getLastMessageAt() > USER_TIMEOUT) {
                    logger.info("Session {} inactive for too long, ending session", session.getId());
                    serviceSessionService.endSession(session.getId(), session.getServiceId());
                }
            }
        }
    }
}