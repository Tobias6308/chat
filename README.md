# WebChat 项目介绍

WebChat 是一个功能完整的即时通讯（IM）系统，提供实时聊天、好友管理、群组交流等功能。

## 技术栈

### 后端 (chat-server)
- **Spring Boot 2.7.18** - Web 框架
- **Netty 4.1.100** - WebSocket 长连接
- **MongoDB** - 数据存储
- **Redis** - 缓存与会话管理
- **JWT** - 用户身份认证

### 前端 (chat-web)
- **Vue 3** - 响应式 UI 框架
- **TypeScript** - 类型安全
- **Vite** - 构建工具
- **Pinia** - 状态管理
- **Tailwind CSS** - 样式框架

### 管理后台 (chat-admin)
- **Vue 3** + **TypeScript**
- **Element Plus** - UI 组件库
- **Axios** - HTTP 客户端

## 项目结构

```
webchat/
├── chat-server/     # Java 后端服务 (Spring Boot + Netty)
├── chat-web/        # 用户 Web 客户端
└── chat-admin/      # 管理后台系统
```

## 功能特性

### 用户端 (chat-web)
- 用户登录/注册
- 好友添加与管理
- 群组创建与聊天
- 好友请求处理
- 个人资料管理
- 实时消息收发 (WebSocket)

### 管理端 (chat-admin)
- 用户管理
- 群组管理
- 消息管理
- 管理员账户管理

## 快速开始

### 后端启动
```bash
cd chat-server
mvn spring-boot:run
```

### 前端启动
```bash
cd chat-web
npm install
npm run dev
```

### 管理后台启动
```bash
cd chat-admin
npm install
npm run dev
```

## 环境要求

- JDK 1.8+
- Maven 3.6+
- Node.js 18+
- MongoDB
- Redis

---

## 技术逻辑文档

### 1. WebSocket 长连接实现

#### 1.1 Netty 服务器架构

后端使用 Netty 框架实现 WebSocket 长连接，核心组件：

```
NettyWebSocketServer (主服务器)
├── Boss EventLoopGroup (1 线程) - 接受客户端连接
├── Worker EventLoopGroup (16 线程) - 处理 I/O 事件
└── WebSocketChannelInitializer (Channel 初始化器)
    ├── HttpServerCodec (HTTP 编解码)
    ├── HttpObjectAggregator (请求聚合)
    ├── WebSocketServerProtocolHandler (WebSocket 协议)
    ├── IdleStateHandler (心跳检测)
    ├── JsonDecoder (JSON 解码)
    ├── JsonEncoder (JSON 编码)
    ├── AuthHandler (认证处理)
    ├── HeartbeatHandler (心跳处理)
    └── MessageHandler (消息处理)
```

#### 1.2 连接建立流程

1. 客户端通过 `/ws` 路径发起 HTTP 升级请求
2. `AuthHandler` 验证 JWT Token
3. 验证成功后建立 WebSocket 连接
4. `SessionManager` 注册用户会话 (内存 + Redis)

#### 1.3 消息协议格式

**客户端发送 (ClientPayload)**:
```typescript
interface ClientPayload {
  action: string;      // 操作类型: chat/heartbeat/auth
  data?: any;          // 业务数据
  timestamp?: number;  // 时间戳
}
```

**服务器响应 (ServerPayload)**:
```typescript
interface ServerPayload {
  type: string;        // 消息类型: message/notice/error
  data?: any;          // 响应数据
  timestamp?: number;
}
```

### 2. 消息存储与同步

#### 2.1 MongoDB 数据模型

**Message 文档结构**:
```
{
  id: string,              // 消息ID
  conversationId: string,  // 会话ID
  senderId: string,        // 发送者ID
  content: string,         // 消息内容
  contentType: string,     // 内容类型: text/image/file
  status: string,         // 状态: sending/sent/delivered/read
  createdAt: number,      // 创建时间戳
  updatedAt: number,      // 更新时间戳
  metadata: object,       // 附加信息
  replyTo: string,        // 回复的消息ID
  reactions: object[]     // 表情反应
}
```

#### 2.2 游标分页实现

消息历史采用**游标分页**而非传统偏移分页：

```java
// 服务端 (MessageService.java)
public Map<String, Object> getHistoryWithCursor(String conversationId, Long cursor, int limit) {
  // cursor 为消息创建时间戳
  // 查询比 cursor 早的消息
  messages = messageRepository.findByConversationIdWithCursor(
    conversationId, cursor, limit + 1
  );

  // 判断是否有更多
  boolean hasMore = messages.size() > limit;
  // 返回下一条消息时间作为游标
  String nextCursor = messages.get(messages.size() - 1).getCreatedAt();
}
```

**优点**:
- 避免传统 OFFSET 分页的性能问题
- 支持快速跳转到任意位置
- 消息新增时不影响已获取的分页

### 3. 会话管理

#### 3.1 双层会话存储

```
┌─────────────────┐     ┌─────────────────┐
│   内存 (Map)    │     │   Redis        │
├─────────────────┤     ├─────────────────┤
│ channels        │     │ chat:ws:session:userId
│ (Channel ID -> │────▶│ -> channelId
│  Channel)       │     │ TTL: 30分钟     │
│                 │     │                 │
│ userChannelMap  │     │                 │
│ (userId ->     │     │                 │
│  channelId)    │     │                 │
└─────────────────┘     └─────────────────┘
```

#### 3.2 会话生命周期

1. **建立连接**: `SessionManager.register()` - 用户认证后绑定 Channel
2. **保持连接**: 心跳包维持 (心跳间隔可配置)
3. **断开连接**: `SessionManager.remove()` - 清理内存和 Redis
4. **消息发送**: `SessionManager.sendToUser()` - 通过 Channel 发送

### 4. 认证与安全

#### 4.1 JWT 认证流程

```
登录请求 ──▶ JWT 生成 ──▶ 返回 Token
                   │
                   ▼
          ┌────────────────┐
          │ JwtUtil        │
          │ - secret       │
          │ - expiration   │
          │ - HS256 签名   │
          └────────────────┘

请求携带 Token ──▶ AuthInterceptor 验证 ──▶ 解析 userId
                                         │
                                         ▼
                              放入请求属性供后续使用
```

#### 4.2 安全措施

- **Token 验证**: 每次 WebSocket 连接时验证 JWT
- **HTTP 拦截**: API 请求通过 `AuthInterceptor` 验证 Token
- **MD5 签名**: 敏感数据使用 MD5 校验
- **慢请求日志**: 通过 AOP 记录耗时 > 1s 的请求

### 5. 前端状态管理 (Pinia)

#### 5.1 Message Store 架构

```typescript
// 核心设计
messagesMap: Map<conversationId, Message[]>

// 特性:
- shallowRef: 避免深层响应式开销
- 乐观更新: 发送消息立即显示，后台确认
- 内存限制: 每个会话最多 500 条消息
- IndexedDB 持久化: 消息本地缓存
```

#### 5.2 乐观更新流程

```
1. 用户发送消息
   │
   ▼
2. createOptimisticMessage() 创建临时消息
   - ID 格式: temp_xxx
   - 状态: sending
   │
   ▼
3. WebSocket 发送真实消息
   │
   ▼
4. 服务器响应确认
   │
   ▼
5. replaceOptimisticMessage() 替换为真实消息
   - ID 替换为服务器返回的真实ID
   - 状态更新为 sent
```

### 6. 群组功能实现

#### 6.1 群组数据结构

```java
// Group 实体
{
  id: string,              // 群ID (group_xxx)
  name: string,           // 群名称
  description: string,     // 群描述
  ownerId: string,         // 群主ID
  members: List<GroupMember>,  // 成员列表
  memberCount: int,        // 成员数量
  type: string,           // 类型: group
  allMuted: boolean,      // 全员禁言
  createdAt: number
}

// GroupMember 成员
{
  userId: string,
  nickname: string,
  role: string,      // owner/admin/member
  joinedAt: number,
  muted: boolean    // 个人禁言
}
```

#### 6.2 群组操作

| 操作 | 权限要求 | 说明 |
|------|---------|------|
| 创建群组 | 登录用户 | 创建者自动成为群主 |
| 添加成员 | 群主/管理员 | 需填写成员昵称 |
| 移除成员 | 群主/管理员 | 群主不可被移除 |
| 设置管理员 | 群主 | 仅群主可操作 |
| 禁言/解禁 | 群主/管理员 | 个人禁言开关 |
| 解散群组 | 群主 | 删除群组数据 |
| 退出群组 | 成员 | 群主不可退出 |

### 7. 消息推送机制

#### 7.1 广播逻辑

```java
// MessageService.java
public void broadcastToConversation(String conversationId, String excludeUserId, ServerPayload payload) {
  // 1. 获取会话成员
  conversationRepository.findById(conversationId)
    .ifPresent(conversation -> {
      // 2. 遍历成员，排除发送者
      for (String userId : conversation.getParticipants()) {
        if (!userId.equals(excludeUserId)) {
          // 3. 通过 SessionManager 发送
          sessionManager.sendToUser(userId, payload);
        }
      }
    });
}
```

#### 7.2 在线/离线处理

- **在线用户**: 直接通过 Channel 发送
- **离线用户**: 消息存储至 MongoDB，用户上线后通过历史消息获取
- **未读计数**: Redis 记录用户未读消息数

### 8. 管理后台 API

管理后台通过 REST API 与后端交互：

| 模块 | 路径 | 功能 |
|-----|------|------|
| 用户管理 | GET/POST/PUT/DELETE /api/admin/users | 增删改查用户 |
| 群组管理 | GET/DELETE /api/admin/groups | 查询/解散群组 |
| 消息管理 | GET/DELETE /api/admin/messages | 查询/删除消息 |
| 好友管理 | GET/DELETE /api/admin/friends | 管理好友关系 |
| 统计分析 | GET /api/admin/stats | 在线用户数等统计 |

### 9. 性能优化策略

1. **Netty 线程池**: 16 个 Worker 线程处理并发
2. **内存会话**: 热点数据存内存，Redis 做持久化
3. **游标分页**: 避免大 OFFSET 查询
4. **消息内存限制**: 每会话最多 500 条
5. **WebSocket 复用**: 长连接避免频繁握手
6. **心跳检测**: 及时发现断连