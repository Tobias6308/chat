# Chat Server

基于 Java 8 + Netty + MongoDB + Redis 的实时聊天后端服务。

## 技术栈

- **Java 8** - 编程语言 (兼容性: 不使用 var, List.of(), Map.of())
- **Netty 4.1** - WebSocket 服务器 (端口 8081)
- **Spring Boot 2.7** - Web 框架 (REST API 端口 8082)
- **MongoDB** - 数据存储 (server.local.com:27017/chat)
- **Redis** - 缓存 (server.local.com:6879, db: 6, 密码: redis.123456)
- **JWT** - 身份认证
- **Lombok** - 代码简化

## 项目结构

```
chat-server/
├── src/main/java/com/chat/
│   ├── common/          # 公共枚举、常量
│   │   └── ErrorCode.java
│   ├── config/         # 配置类
│   │   ├── ApplicationContextHelper.java
│   │   ├── AuthInterceptor.java
│   │   ├── FileUploadConfig.java       # 文件上传配置
│   │   ├── MongoConfig.java
│   │   ├── RedisConfig.java
│   │   ├── WebSocketConfig.java        # WebSocket 配置
│   │   └── WebMvcConfig.java
│   ├── controller/    # HTTP 控制器
│   │   ├── AuthController.java
│   │   ├── ChatController.java         # 聊天会话 API
│   │   ├── ConfigController.java       # 配置接口
│   │   ├── FileUploadController.java   # 文件上传 API
│   │   ├── FriendController.java
│   │   └── GroupController.java       # 群组 API
│   ├── document/      # MongoDB 文档模型
│   │   ├── Conversation.java
│   │   ├── Friend.java
│   │   ├── Group.java
│   │   ├── Message.java
│   │   └── User.java
│   ├── dto/           # 数据传输对象
│   │   ├── AuthPayload.java
│   │   ├── ClientPayload.java
│   │   └── ServerPayload.java
│   ├── netty/         # Netty WebSocket 处理
│   │   ├── AuthHandler.java
│   │   ├── HeartbeatHandler.java
│   │   ├── JsonDecoder.java
│   │   ├── JsonEncoder.java
│   │   ├── MessageHandler.java
│   │   ├── NettyWebSocketServer.java
│   │   ├── SessionManager.java
│   │   └── WebSocketHandler.java
│   ├── repository/    # MongoDB Repository
│   │   ├── ConversationRepository.java
│   │   ├── FriendRepository.java
│   │   ├── GroupRepository.java
│   │   ├── MessageRepository.java
│   │   └── UserRepository.java
│   ├── security/      # 安全认证
│   │   └── JwtUtil.java
│   ├── service/       # 业务逻辑
│   │   ├── AdminService.java           # 后台管理服务
│   │   ├── ConversationService.java    # 会话服务
│   │   ├── FileUploadService.java      # 文件上传服务
│   │   ├── GroupService.java          # 群组服务
│   │   ├── MessageService.java         # 消息服务
│   │   ├── RedisMuteService.java       # 用户静音状态 (Redis)
│   │   ├── RedisPinService.java       # 用户置顶状态 (Redis)
│   │   └── RedisUnreadService.java    # 未读消息状态 (Redis)
│   └── ChatServerApplication.java
└── src/main/resources/
    └── application.yml
```

## 配置

### 端口配置

| 服务 | 端口 |
|------|------|
| Netty WebSocket | 8081 |
| Tomcat REST API | 8082 |

### MongoDB

```
host: server.local.com
port: 27017
database: chat
username: chat
password: chat123
```

### Redis

```
host: server.local.com
port: 6879
password: redis.123456
database: 6
```

## API 接口

### 认证接口

#### 用户注册
```
POST /api/auth/register
Content-Type: application/json

{
  "username": "用户名",
  "password": "密码",
  "nickname": "昵称(可选)"
}

Response:
{
  "token": "JWT令牌",
  "userId": "用户ID",
  "nickname": "昵称"
}
```

#### 用户登录
```
POST /api/auth/login
Content-Type: application/json

{
  "username": "用户名",
  "password": "密码"
}

Response:
{
  "token": "JWT令牌",
  "userId": "用户ID",
  "nickname": "昵称",
  "avatar": "头像URL"
}
```

### 会话接口

#### 获取会话列表
```
GET /api/chat/conversations
Authorization: Bearer <token>

Response:
{
  "conversations": [
    {
      "id": "会话ID",
      "type": "private/group",
      "name": "会话名称",
      "avatar": "头像",
      "unreadCount": 0,
      "lastMessageContent": "最后消息",
      "lastMessageAt": 1234567890,
      "pinned": false,
      "muted": false
    }
  ]
}
```

#### 获取会话详情
```
GET /api/chat/conversation/{id}
Authorization: Bearer <token>

Response:
{
  "id": "会话ID",
  "type": "private/group",
  "name": "会话名称",
  "avatar": "头像",
  "participants": ["用户ID列表"],
  "unreadCount": 0,
  "pinned": false,
  "muted": false,
  "createdAt": 1234567890
}
```

#### 获取历史消息
```
GET /api/chat/conversation/{id}/messages?cursor=123&limit=50
Authorization: Bearer <token>

Response:
{
  "messages": [...],
  "cursor": "下一页游标",
  "hasMore": false
}
```

#### 创建私聊会话
```
POST /api/chat/private
Authorization: Bearer <token>
Content-Type: application/json

{
  "friendId": "好友ID"
}

Response:
{
  "id": "会话ID",
  "type": "private",
  "name": "会话名称"
}
```

#### 标记已读
```
POST /api/chat/conversation/{id}/read
Authorization: Bearer <token>

Response:
{
  "success": true
}
```

#### 切换置顶 (用户特定，Redis存储)
```
POST /api/chat/conversation/{id}/pin
Authorization: Bearer <token>

Response:
{
  "success": true,
  "pinned": true/false
}
```

#### 切换静音 (用户特定，Redis存储)
```
POST /api/chat/conversation/{id}/mute
Authorization: Bearer <token>

Response:
{
  "success": true,
  "muted": true/false
}
```

#### 获取配置信息
```
GET /api/chat/config
Authorization: Bearer <token>

Response:
{
  "fileServerUrl": "http://localhost:8082"
}
```

### 文件上传接口

#### 上传图片
```
POST /api/chat/upload/image
Authorization: Bearer <token>
Content-Type: multipart/form-data

Response:
{
  "url": "/uploads/image/2026/05/09/xxx.jpg",
  "filename": "xxx.jpg",
  "size": 12345
}
```

#### 上传音频
```
POST /api/chat/upload/audio
Authorization: Bearer <token>
Content-Type: multipart/form-data

Response:
{
  "url": "/uploads/audio/2026/05/09/xxx.mp3",
  "filename": "xxx.mp3",
  "size": 12345
}
```

#### 上传文件
```
POST /api/chat/upload/file
Authorization: Bearer <token>
Content-Type: multipart/form-data

Response:
{
  "url": "/uploads/document/2026/05/09/xxx.pdf",
  "filename": "xxx.pdf",
  "size": 12345
}
```

#### 下载文件
```
GET /api/chat/download{path}
Example: /api/chat/download/uploads/image/2026/05/09/xxx.jpg
```

### 群组接口

#### 创建群组
```
POST /api/group
Authorization: Bearer <token>
Content-Type: application/json

{
  "name": "群名称",
  "description": "群描述(可选)",
  "memberIds": ["成员ID列表"]
}

Response:
{
  "id": "群组ID",
  "name": "群名称",
  "avatar": "头像",
  "description": "群描述",
  "ownerId": "群主ID",
  "memberCount": 1,
  "createdAt": 1234567890
}
```

#### 获取群组列表
```
GET /api/group/list
Authorization: Bearer <token>

Response:
{
  "groups": [...]
}
```

#### 获取群组详情
```
GET /api/group/{id}
Authorization: Bearer <token>

Response:
{
  "id": "群组ID",
  "name": "群名称",
  "avatar": "头像",
  "description": "群描述",
  "ownerId": "群主ID",
  "memberCount": 10,
  "type": "group",
  "allMuted": false,
  "createdAt": 1234567890,
  "members": [...]
}
```

#### 获取群成员
```
GET /api/group/{id}/members
Authorization: Bearer <token>

Response:
{
  "members": [
    {
      "userId": "用户ID",
      "nickname": "昵称",
      "avatar": "头像",
      "role": "owner/admin/member",
      "joinedAt": 1234567890,
      "muted": false
    }
  ]
}
```

#### 添加群成员
```
POST /api/group/{id}/members
Authorization: Bearer <token>
Content-Type: application/json

{
  "memberIds": ["成员ID列表"]
}

Response:
{
  "success": true
}
```

#### 移除群成员
```
DELETE /api/group/{id}/members/{memberId}
Authorization: Bearer <token>

Response:
{
  "success": true
}
```

#### 更新群组
```
PUT /api/group/{id}
Authorization: Bearer <token>
Content-Type: application/json

{
  "name": "新名称",
  "description": "新描述"
}

Response:
{
  "id": "群组ID",
  "name": "群名称",
  "description": "群描述"
}
```

#### 解散群组
```
DELETE /api/group/{id}
Authorization: Bearer <token>

Response:
{
  "success": true
}
```

#### 切换群组置顶 (用户特定，Redis存储)
```
POST /api/group/{id}/pin
Authorization: Bearer <token>

Response:
{
  "success": true,
  "pinned": true/false
}
```

#### 切换群组静音 (用户特定，Redis存储)
```
POST /api/group/{id}/mute
Authorization: Bearer <token>

Response:
{
  "success": true,
  "muted": true/false
}
```

### 好友接口

#### 搜索用户
```
GET /api/friend/search?keyword=关键词
Authorization: Bearer <token>

Response:
{
  "users": [
    {
      "userId": "用户ID",
      "username": "用户名",
      "nickname": "昵称",
      "avatar": "头像",
      "isFriend": false
    }
  ]
}
```

#### 发送好友请求
```
POST /api/friend/request
Authorization: Bearer <token>
Content-Type: application/json

{
  "userId": "目标用户ID"
}

Response:
{
  "success": true,
  "requestId": "请求ID"
}
```

#### 接受好友请求
```
POST /api/friend/request/{requestId}/accept
Authorization: Bearer <token>

Response:
{
  "success": true
}
```

#### 拒绝好友请求
```
POST /api/friend/request/{requestId}/reject
Authorization: Bearer <token>

Response:
{
  "success": true
}
```

#### 删除好友
```
DELETE /api/friend/{friendId}
Authorization: Bearer <token>

Response:
{
  "success": true
}
```

#### 获取好友列表
```
GET /api/friend/list
Authorization: Bearer <token>

Response:
{
  "friends": [
    {
      "userId": "用户ID",
      "username": "用户名",
      "nickname": "昵称",
      "avatar": "头像",
      "online": false,
      "remark": "备注",
      "addedAt": 1234567890
    }
  ]
}
```

#### 获取好友请求
```
GET /api/friend/requests
Authorization: Bearer <token>

Response:
{
  "received": [...],
  "sent": [...]
}
```

## WebSocket 协议

### 客户端发送

#### 认证
```json
{
  "type": "auth",
  "id": "消息ID",
  "timestamp": 1234567890,
  "payload": {
    "token": "JWT令牌",
    "deviceId": "设备ID",
    "clientInfo": {
      "platform": "web",
      "version": "1.0.0",
      "language": "zh-CN"
    }
  }
}
```

#### 发送消息
```json
{
  "type": "send",
  "id": "消息ID",
  "timestamp": 1234567890,
  "payload": {
    "conversationId": "会话ID",
    "content": "消息内容",
    "contentType": "text/image/file"
  }
}
```

#### 获取历史消息
```json
{
  "type": "fetch_history",
  "id": "消息ID",
  "timestamp": 1234567890,
  "payload": {
    "conversationId": "会话ID",
    "cursor": "游标",
    "limit": 50
  }
}
```

#### 消息确认
```json
{
  "type": "ack",
  "id": "消息ID",
  "timestamp": 1234567890,
  "payload": {
    "messageId": "消息ID",
    "status": "delivered/read"
  }
}
```

#### 心跳
```json
{
  "type": "ping",
  "id": "消息ID",
  "timestamp": 1234567890,
  "payload": {}
}
```

### 服务器响应

#### 认证成功
```json
{
  "type": "auth_ok",
  "id": "消息ID",
  "timestamp": 1234567890,
  "payload": {
    "userId": "用户ID",
    "serverTime": 1234567890
  }
}
```

#### 错误响应
```json
{
  "type": "error",
  "id": "消息ID",
  "timestamp": 1234567890,
  "payload": {
    "code": "ERROR_CODE",
    "message": "错误消息"
  }
}
```

#### 消息推送
```json
{
  "type": "message",
  "id": "消息ID",
  "timestamp": 1234567890,
  "payload": {
    "message": {...}
  }
}
```

#### 历史消息
```json
{
  "type": "history",
  "id": "消息ID",
  "timestamp": 1234567890,
  "payload": {
    "messages": [...],
    "cursor": "游标",
    "hasMore": false
  }
}
```

## 错误码

| 错误码 | 说明 |
|--------|------|
| USERNAME_EXISTS | 用户名已存在 |
| USER_NOT_FOUND | 用户不存在 |
| PASSWORD_ERROR | 密码错误 |
| INVALID_TOKEN | 无效的令牌 |
| AUTH_EXPIRED | 认证已过期 |
| INVALID_AUTH | 认证信息不完整 |
| AUTH_FAILED | 认证失败 |
| NOT_AUTHENTICATED | 未认证 |
| FORBIDDEN | 无权限访问 |
| GROUP_NOT_FOUND | 群组不存在 |
| CONVERSATION_NOT_FOUND | 会话不存在 |
| FRIEND_NOT_FOUND | 好友不存在 |
| FRIEND_REQUEST_EXISTS | 好友请求已存在 |
| SEND_FAILED | 发送失败 |
| HISTORY_FAILED | 获取历史消息失败 |
| INVALID_JSON | 无效的JSON格式 |
| INVALID_PARAM | 参数错误 |
| INTERNAL_ERROR | 服务器内部错误 |

## 后台管理 API

### 认证
```
POST /api/admin/login
Content-Type: application/json

{
  "username": "admin",
  "password": "admin123"
}

Response:
{
  "token": "JWT令牌",
  "expiresIn": 86400
}
```

### 统计
```
GET /api/admin/stats
Authorization: Bearer <token>

Response:
{
  "userCount": 100,
  "groupCount": 50,
  "conversationCount": 200,
  "messageCount": 10000
}
```

### 用户管理
```
GET /api/admin/users?limit=20&skip=0
Authorization: Bearer <token>

PUT /api/admin/users/{id}
Authorization: Bearer <token>
Content-Type: application/json

{ "enabled": false }
```

### 群组管理
```
GET /api/admin/groups?limit=20&skip=0
Authorization: Bearer <token>

DELETE /api/admin/groups/{id}
Authorization: Bearer <token>
```

### 会话管理
```
GET /api/admin/conversations?type=private&limit=20&skip=0
Authorization: Bearer <token>

DELETE /api/admin/conversations/{id}
Authorization: Bearer <token>
```

### 消息管理
```
GET /api/admin/messages?conversationId=xxx&limit=20&skip=0
Authorization: Bearer <token>

DELETE /api/admin/messages/{id}
Authorization: Bearer <token>
```

## 客服模块 API

### 用户端接口

#### 获取客服状态
```
GET /api/chat/service/status
Authorization: Bearer <token>

Response:
{
  "hasAvailableService": true,
  "onlineCount": 2,
  "waitingCount": 0,
  "availableServices": [
    { "id": "xxx", "nickname": "客服小王", "availableSlots": 5 }
  ]
}
```

#### 获取客服列表
```
GET /api/chat/service/list
Authorization: Bearer <token>

Response:
{
  "services": [
    { "id": "xxx", "nickname": "客服小王", "status": "online", "maxChats": 10, "currentChats": 3 }
  ]
}
```

#### 加入等待队列
```
POST /api/chat/service/join
Authorization: Bearer <token>
Content-Type: application/json

{
  "userName": "用户昵称",
  "userAvatar": "头像URL"
}

Response:
{
  "success": true,
  "status": "waiting",
  "position": 1,
  "estimatedWait": "3分钟"
}
```

#### 离开队列
```
POST /api/chat/service/leave
Authorization: Bearer <token>

Response:
{ "success": true }
```

#### 获取队列状态
```
GET /api/chat/service/queue
Authorization: Bearer <token>

Response:
{
  "status": "chatting",
  "sessionId": "service_xxx",
  "serviceId": "yyy",
  "serviceName": "客服小王"
}
```

#### 获取会话消息
```
GET /api/chat/service/conversation?limit=50&skip=0
Authorization: Bearer <token>

Response:
{
  "status": "chatting",
  "messages": [
    { "id": "msg_xxx", "senderId": "userId", "content": "你好", "contentType": "text", "createdAt": 1234567890 }
  ]
}
```

#### 发送消息
```
POST /api/chat/service/message
Authorization: Bearer <token>
Content-Type: application/json

{
  "content": "消息内容",
  "contentType": "text",
  "fileUrl": "可选的文件URL",
  "fileName": "可选的文件名"
}

Response:
{
  "success": true,
  "message": { "id": "msg_xxx", ... }
}
```

#### 结束会话
```
POST /api/chat/service/end
Authorization: Bearer <token>

Response:
{ "success": true }
```

#### 评价会话
```
POST /api/chat/service/rate
Authorization: Bearer <token>
Content-Type: application/json

{
  "rating": 5,
  "comment": "服务态度很好"
}

Response:
{ "success": true }
```

#### 标记已读
```
POST /api/chat/service/read
Authorization: Bearer <token>

Response:
{ "success": true }
```

### 客服端接口

#### 获取客服列表
```
GET /api/admin/service/list
Authorization: Bearer <admin_token>

Response:
{
  "services": [...]
}
```

#### 创建客服账号
```
POST /api/admin/service/create
Authorization: Bearer <admin_token>
Content-Type: application/json

{
  "username": "service01",
  "password": "123456",
  "nickname": "客服小王"
}

Response:
{ "success": true }
```

#### 更新客服状态
```
PUT /api/admin/service/status
Authorization: Bearer <admin_token>
Content-Type: application/json

{
  "status": "online"  // online/busy/offline
}

Response:
{ "success": true, "status": "online" }
```

#### 接待下一个用户
```
POST /api/admin/service/next
Authorization: Bearer <admin_token>

Response:
{
  "success": true,
  "sessionId": "service_xxx",
  "userId": "yyy",
  "userName": "用户昵称"
}
```

#### 获取会话列表
```
GET /api/admin/service/sessions
Authorization: Bearer <admin_token>

Response:
{
  "sessions": [
    {
      "id": "service_xxx",
      "userId": "yyy",
      "userName": "用户昵称",
      "status": "chatting",
      "chatStartAt": 1234567890
    }
  ]
}
```

#### 获取会话消息
```
GET /api/admin/service/session/{sessionId}/messages?limit=50&skip=0
Authorization: Bearer <admin_token>

Response:
{
  "messages": [...]
}
```

#### 发送消息
```
POST /api/admin/service/message
Authorization: Bearer <admin_token>
Content-Type: application/json

{
  "sessionId": "service_xxx",
  "content": "回复内容",
  "contentType": "text",
  "fileUrl": "可选",
  "fileName": "可选"
}

Response:
{
  "success": true,
  "message": {...}
}
```

#### 结束会话
```
POST /api/admin/service/end
Authorization: Bearer <admin_token>
Content-Type: application/json

{
  "sessionId": "service_xxx"
}

Response:
{ "success": true }
```

#### 转移会话
```
POST /api/admin/service/transfer
Authorization: Bearer <admin_token>
Content-Type: application/json

{
  "sessionId": "service_xxx",
  "toServiceId": "目标客服ID"
}

Response:
{ "success": true, "serviceName": "客服小李" }
```

#### 获取快捷回复
```
GET /api/admin/service/quick-replies
Authorization: Bearer <admin_token>

Response:
{
  "quickReplies": [
    { "id": "qr1", "title": "您好", "content": "您好，请问有什么可以帮您？" }
  ]
}
```

#### 添加快捷回复
```
POST /api/admin/service/quick-replies
Authorization: Bearer <admin_token>
Content-Type: application/json

{
  "title": "问候语",
  "content": "您好，很高兴为您服务"
}

Response:
{ "success": true }
```

#### 删除快捷回复
```
DELETE /api/admin/service/quick-replies/{replyId}
Authorization: Bearer <admin_token>

Response:
{ "success": true }
```

#### 更新会话备注
```
PUT /api/admin/service/session/{sessionId}/note
Authorization: Bearer <admin_token>
Content-Type: application/json

{
  "note": "客户询问了退货政策"
}

Response:
{ "success": true }
```

#### 更新会话标签
```
PUT /api/admin/service/session/{sessionId}/tags
Authorization: Bearer <admin_token>
Content-Type: application/json

{
  "tags": ["VIP", "售后"]
}

Response:
{ "success": true }
```

#### 获取服务统计
```
GET /api/admin/service/stats
Authorization: Bearer <admin_token>

Response:
{
  "totalSessions": 100,
  "activeSessions": 5,
  "finishedSessions": 90,
  "waitingSessions": 2,
  "onlineServices": 3,
  "queueSize": 0
}
```

#### 获取客服绩效
```
GET /api/admin/service/performance
Authorization: Bearer <admin_token>

Response:
{
  "totalSessions": 50,
  "finishedSessions": 48,
  "avgDuration": 15  // 分钟
}
```

### WebSocket 客服消息协议

#### 客户端发送 (用户端)
```json
{
  "type": "service",
  "id": "msg_xxx",
  "timestamp": 1234567890,
  "payload": {
    "action": "join_queue",
    "userName": "用户昵称",
    "userAvatar": "头像URL"
  }
}
```

支持的 action:
- `join_queue` - 加入队列
- `leave_queue` - 离开队列
- `get_status` - 获取状态
- `get_messages` - 获取消息
- `send_message` - 发送消息
- `end_session` - 结束会话
- `mark_read` - 标记已读
- `typing` - 正在输入

#### 服务器推送
```json
{
  "type": "service_queue_join",
  "id": "msg_xxx",
  "payload": {
    "success": true,
    "status": "waiting",
    "position": 1
  }
}
```

```json
{
  "type": "service_message",
  "payload": {
    "id": "msg_xxx",
    "senderId": "userId",
    "content": "消息内容",
    "createdAt": 1234567890
  }
}
```

```json
{
  "type": "service_typing",
  "payload": {
    "userId": "xxx",
    "userName": "用户昵称"
  }
}
```

## 构建与运行

```bash
# 编译
mvn clean compile

# 打包
mvn package -DskipTests

# 运行
java -jar target/chat-server-1.0.0.jar

# 开发模式
mvn spring-boot:run
```