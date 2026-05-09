# Chat Admin 后台管理系统

基于 Vue3 + TypeScript + Vite + Element Plus 的后台管理系统。

## 项目信息

| 项目 | 值 |
|------|------|
| 端口 | 3001 |
| 后端代理 | 8082 |
| 技术栈 | Vue3 + TypeScript + Vite + Element Plus |
| 包管理器 | npm |

## 快速开始

```bash
# 安装依赖
npm install

# 启动开发服务器
npm run dev

# 构建生产版本
npm run build

# 类型检查
npm run typecheck
```

访问 http://localhost:3001

## 项目结构

```
src/
├── main.ts              # 应用入口
├── App.vue              # 根组件 (布局)
├── router/
│   └── index.ts         # 路由配置 (7个页面)
├── views/
│   ├── Login.vue        # 登录页
│   ├── Dashboard.vue    # 仪表盘 (统计)
│   ├── Admins.vue       # 管理员管理
│   ├── Users.vue        # 用户管理
│   ├── Groups.vue       # 群组管理
│   ├── Conversations.vue # 会话管理
│   ├── Friends.vue      # 好友管理
│   └── Messages.vue     # 消息管理
└── utils/
    ├── request.ts       # axios 拦截器 (JWT)
    └── api.ts           # API 接口封装
```

## 页面功能

| 页面 | 功能 |
|------|------|
| 登录 | 用户名/密码登录，JWT token 存储 (admin-jwt.secret) |
| 仪表盘 | 展示用户数、群组数、会话数、消息数 |
| 管理员 | 添加、删除管理员 |
| 用户 | 查看用户列表，启用/禁用用户，查看详细信息 |
| 群组 | 查看群组列表，删除群组，查看成员 |
| 会话 | 查看会话列表，删除会话，按类型筛选 |
| 好友 | 查看好友关系，删除好友，按用户筛选 |
| 消息 | 搜索/查看消息，删除消息，按会话/发送者筛选 |

## 路由

| 路径 | 名称 | 说明 |
|------|------|------|
| /login | Login | 登录页 (无需鉴权) |
| / | Dashboard | 仪表盘 |
| /admins | Admins | 管理员管理 |
| /users | Users | 用户管理 |
| /groups | Groups | 群组管理 |
| /conversations | Conversations | 会话管理 |
| /friends | Friends | 好友管理 |
| /messages | Messages | 消息管理 |

所有页面需要登录后才能访问。

## API 封装

`src/utils/api.ts` 封装了所有后台 API 调用：

```typescript
import { adminApi } from '@/utils/api'

// 登录
adminApi.login(username, password)

// 统计
adminApi.getStats()

// 管理员
adminApi.getAdmins()
adminApi.createAdmin(data)
adminApi.deleteAdmin(id)

// 用户
adminApi.getUsers({ limit, skip })
adminApi.updateUserEnable(id, enabled)

// 群组
adminApi.getGroups({ limit, skip })
adminApi.deleteGroup(id)

// 会话
adminApi.getConversations({ type, limit, skip })
adminApi.deleteConversation(id)

// 好友
adminApi.getFriends({ userId, limit, skip })
adminApi.deleteFriend(id)

// 消息
adminApi.getMessages({ conversationId, senderId, limit, skip })
adminApi.deleteMessage(id)
```

## 请求拦截

`src/utils/request.ts` 实现了 JWT token 自动注入：

- 请求自动添加 `Authorization: Bearer <token>` header
- 响应 401 时自动跳转登录页
- token 存储在 sessionStorage

## 样式

全局字体配置支持中文显示 (App.vue)：

```css
* {
  font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", "Microsoft YaHei", "微软雅黑", "PingFang SC", sans-serif;
}
```

## 配置

### Vite 配置 (vite.config.ts)

- 开发服务器端口: 3001
- API 代理: /api → http://localhost:8082
- 路径别名: @ → src

### 依赖

| 包 | 用途 |
|----|------|
| vue ^3.4.21 | 核心框架 |
| vue-router ^4.3.0 | 路由 |
| axios ^1.6.8 | HTTP 请求 |
| element-plus ^2.7.0 | UI 组件库 |
| @element-plus/icons-vue ^2.3.1 | 图标 |

## 注意事项

1. 所有 API 请求通过 Vite 代理到后端 8082 端口
2. 登录后 token 存储在 sessionStorage
3. 页面刷新时重新验证登录状态
4. 中文界面，兼容各主流操作系统