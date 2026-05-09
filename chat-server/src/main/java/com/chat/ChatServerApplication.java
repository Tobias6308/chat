package com.chat;

import com.chat.service.AdminService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

import javax.annotation.PostConstruct;

/**
 * 聊天服务器启动类
 * 
 * Spring Boot 应用入口，整合 Netty WebSocket 服务器
 * 
 * @EnableScheduling 启用定时任务 (用于心跳检测等)
 */
@SpringBootApplication
@EnableScheduling
public class ChatServerApplication {
    
    private static final Logger logger = LoggerFactory.getLogger(ChatServerApplication.class);
    
    @Value("${admin.reset-enabled:false}")
    private boolean resetEnabled;
    
    @Autowired
    private AdminService adminService;
    
    /**
     * 应用主入口
     * 
     * @param args 命令行参数
     */
    public static void main(String[] args) {
        SpringApplication.run(ChatServerApplication.class, args);
    }
    
    @PostConstruct
    public void init() {
        // 如果启用了数据重置，则清空并重新初始化
        if (resetEnabled) {
            logger.info("检测到 admin.reset-enabled=true，清空并初始化数据...");
            
            logger.info("检查并初始化管理员账号...");
            adminService.initAdminUser();
            
            // 初始化测试数据（内部会自动清空现有数据）
            try {
                adminService.initTestData();
                logger.info("数据初始化完成: 用户{}, 群组{}, 会话{}, 消息{}", 
                    adminService.getStats().get("userCount"),
                    adminService.getStats().get("groupCount"),
                    adminService.getStats().get("conversationCount"),
                    adminService.getStats().get("messageCount"));
            } catch (Exception e) {
                logger.error("数据初始化失败: {}", e.getMessage());
            }
        }
    }
}