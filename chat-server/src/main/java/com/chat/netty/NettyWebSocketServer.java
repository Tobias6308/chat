package com.chat.netty;

import com.chat.config.ApplicationContextHelper;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.codec.http.websocketx.WebSocketServerProtocolHandler;
import io.netty.handler.timeout.IdleStateHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;

@Component
public class NettyWebSocketServer {
    
    private static final Logger logger = LoggerFactory.getLogger(NettyWebSocketServer.class);
    
    private static final String WEBSOCKET_PATH = "/ws";
    
    /**
     * Boss 线程组 (接受连接)
     */
    private EventLoopGroup bossGroup;
    
    /**
     * Worker 线程组 (处理 I/O)
     */
    private EventLoopGroup workerGroup;
    
    /**
     * 服务器 Channel
     */
    private Channel serverChannel;
    
    /**
     * WebSocket 端口 (从配置文件读取)
     */
    @Value("${netty.port:8080}")
    private int port;
    
    /**
     * Boss 线程数
     */
    @Value("${netty.boss-threads:1}")
    private int bossThreads;
    
    /**
     * Worker 线程数
     */
    @Value("${netty.worker-threads:16}")
    private int workerThreads;
    
    /**
     * 最大帧长度 (字节)
     */
    @Value("${netty.max-frame-length:65536}")
    private int maxFrameLength;
    
    /**
     * 服务器启动方法
     * 
     * 在 Spring 容器初始化完成后执行
     * 
     * @throws InterruptedException 如果启动失败
     */
    @PostConstruct
    public void start() throws InterruptedException {
        // 1. 创建 Boss 线程组 (1 个线程足够)
        bossGroup = new NioEventLoopGroup(bossThreads);
        
        // 2. 创建 Worker 线程组
        workerGroup = new NioEventLoopGroup(workerThreads);
        
        // 3. 创建服务器引导程序
        ServerBootstrap bootstrap = new ServerBootstrap();
        
        // 4. 配置服务器
        bootstrap
            // 绑定线程组
            .group(bossGroup, workerGroup)
            // 使用 NIO 通道
            .channel(NioServerSocketChannel.class)
            // 设置 Channel 选项
            .option(ChannelOption.SO_BACKLOG, 128)      // 连接队列大小
            .option(ChannelOption.SO_REUSEADDR, true)   // 地址复用
            .childOption(ChannelOption.SO_KEEPALIVE, true)  // TCP keepalive
            .childOption(ChannelOption.TCP_NODELAY, true)  // 禁用 Nagle 算法
            .childOption(ChannelOption.SO_RCVBUF, 1048576)  // 接收缓冲区 1MB
            .childOption(ChannelOption.SO_SNDBUF, 1048576); // 发送缓冲区 1MB
        
        // 5. 设置 Channel 初始化器
        // 使用懒加载获取 Spring 管理的 Bean
        bootstrap.childHandler(ApplicationContextHelper.getBean(WebSocketChannelInitializer.class));
        
        // 6. 绑定端口并启动
        logger.info("Starting Netty WebSocket Server on port {}", port);
        ChannelFuture future = bootstrap.bind(port).sync();
        
        // 7. 获取服务器 Channel 并保存
        serverChannel = future.channel();
        
        logger.info("Netty WebSocket Server started successfully on port {}", port);
    }
    
    /**
     * 关闭服务器方法
     * 
     * 在应用关闭时执行，优雅关闭线程组
     */
    @PreDestroy
    public void shutdown() {
        logger.info("Shutting down Netty WebSocket Server...");
        
        // 关闭 Server Channel
        if (serverChannel != null) {
            serverChannel.close();
        }
        
        // 优雅关闭线程组 (等待任务完成)
        if (bossGroup != null) {
            bossGroup.shutdownGracefully();
        }
        if (workerGroup != null) {
            workerGroup.shutdownGracefully();
        }
        
        logger.info("Netty WebSocket Server shut down completely");
    }
}