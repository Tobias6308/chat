# 实时聊天应用 (Vue 3 Web)

一个生产级的 Vue 3 Web 端实时聊天应用，支持 WebSocket 实时通信、虚拟列表渲染、多标签页同步和离线消息存储。

## 🚀 快速启动

### 环境要求

- Node.js 18+
- npm 8+

### 安装依赖

```bash
cd chat-web
npm install
```

### 启动命令

#### 方式一：单独启动

```bash
# 启动前端开发服务器
npm run dev

# 启动 Mock WebSocket 服务器 (可选)
npm run dev:ws
```

#### 方式二：一键启动 (推荐)

```bash
# 同时启动前端 + Mock WS 服务器
npm run dev:all
```

#### 生产构建

```bash
npm run build
```

#### 代码检查

```bash
# TypeScript 类型检查
npx vue-tsc --noEmit

# ESLint 检查
npm run lint
```

构建产物输出到 `dist/` 目录

### 访问地址

- 前端: http://localhost:3000
- Mock WS: ws://localhost:8080/ws

---

## ⚙️ 环境配置

### 开发环境变量

在项目根目录创建 `.env.development`：

```env
VITE_WS_URL=wss://localhost:8080/ws
VITE_API_BASE=http://localhost:8080/api
```

### 生产环境变量

在项目根目录创建 `.env.production`：

```env
VITE_WS_URL=wss://your-websocket-server.com/ws
VITE_API_BASE=https://your-api-server.com/api
```

---

## 📋 JSON 协议联调 Checklist

### 后端需实现的 WebSocket 协议

| 客户端发送 | 服务端响应 | 说明 |
|-----------|-----------|------|
| `{"type":"auth","id":"...","payload":{"token":"..."},"timestamp":123}` | `{"type":"auth_ok","payload":{"userId":"...","userInfo":{...}},"timestamp":123}` | 认证握手 |
| `{"type":"ping","id":"...","timestamp":123}` | `{"type":"pong","timestamp":123}` | 心跳保活 (15s) |
| `{"type":"send","id":"...","payload":{"conversationId":"...","content":"..."},"timestamp":123}` | `{"type":"ack_ok","id":"...","payload":{"messageId":"...","status":"sent"}},"timestamp":123}` | 发送消息 |
| `{"type":"ack","id":"...","payload":{"messageId":"...","status":"delivered"}},"timestamp":123}` | - | 已读回执 |
| `{"type":"fetch_history","id":"...","payload":{"conversationId":"...","cursor":"...","limit":50},"timestamp":123}` | `{"type":"history","payload":{"messages":[...],"cursor":"...","hasMore":true},"timestamp":123}` | 历史分页 |

### 服务端推送场景

- **新消息**: `{"type":"message","payload":{"message":{...}},"timestamp":123}`
- **错误响应**: `{"type":"error","payload":{"code":"AUTH_EXPIRED","message":"..."},"timestamp":123}`

### 关闭码处理

| 关闭码 | 客户端行为 |
|-------|-----------|
| 1006 | 自动重连 (指数退避 1s~30s) |
| 4001 | 停止重连，跳转登录页 |
| 4003 | 停止重连，显示账号禁用提示 |

---

## 🔧 浏览器性能压测与内存泄漏排查

### 使用 Chrome DevTools

#### Performance 面板

1. 打开 Chrome DevTools (F12)
2. 切换到 Performance 面板
3. 点击录制按钮，执行用户操作（发送消息、滚动、切换会话）
4. 检查 Main thread 是否掉帧，查找 Long Tasks (>50ms)

#### Memory 面板

1. 切换到 Memory 面板
2. 拍摄堆快照 (Heap Snapshot)
3. 执行操作后再次拍摄快照
4. 对比内存增长，查找 Detached DOM 节点

### 关键监控指标

| 指标 | 预期值 |
|-----|-------|
| FPS | 保持 60fps |
| Memory | 内存增长平稳 |
| JS Heap | 单会话 <50MB |

### 常见泄漏点及解决方案

| 问题 | 解决方案 |
|-----|---------|
| WebSocket 事件监听器未移除 | onUnmounted 中调用 ws.disconnect() |
| setInterval 心跳未清理 | stopHeartbeat() 在断连时调用 |
| 消息数组无限增长 | 已实现 500 条/会话限制 |
| IndexedDB 连接未关闭 | db.close() 在组件卸载时调用 |

---

## 📱 PWA 离线聊天扩展

### Service Worker 缓存策略

```javascript
// public/sw.js
const CACHE_NAME = 'chat-v1';

self.addEventListener('fetch', (event) => {
  const url = new URL(event.request.url);

  // 仅缓存静态资源，API 和 WebSocket 不缓存
  if (url.pathname.startsWith('/assets/')) {
    event.respondWith(
      caches.match(event.request).then(cached =>
        cached || fetch(event.request)
      )
    );
  }
});
```

### PWA 清单配置

在 `public/manifest.json` 配置：

```json
{
  "name": "实时聊天应用",
  "short_name": "聊天",
  "start_url": "/",
  "display": "standalone",
  "background_color": "#ffffff",
  "theme_color": "#3b82f6",
  "icons": [
    {
      "src": "/icon-192.png",
      "sizes": "192x192",
      "type": "image/png"
    },
    {
      "src": "/icon-512.png",
      "sizes": "512x512",
      "type": "image/png"
    }
  ]
}
```

### 离线消息队列

当检测到离线时：
1. 消息存入 IndexedDB 的 `pending_messages` 表
2. 网络恢复后自动重发
3. 重发失败超过 5 次标记为发送失败

---

## 📁 项目结构

```
chat-web/
├── public/                  # 静态资源
├── src/
│   ├── api/                 # HTTP 接口
│   ├── assets/              # 样式文件
│   │   └── main.css
│   ├── components/
│   │   ├── chat/            # 聊天组件
│   │   │   ├── ChatLayout.vue      # 会话列表 + 聊天区域布局
│   │   │   ├── ChatInput.vue       # 消息输入框 + 文件上传
│   │   │   ├── MessageBubble.vue   # 消息气泡 (支持图片/语音/文件预览)
│   │   │   └── VirtualMessageList.vue # 虚拟列表 (高性能渲染)
│   │   └── ui/              # UI 组件
│   ├── composables/
│   │   ├── useWebSocket.ts  # WebSocket 核心逻辑
│   │   └── useMessageSync.ts # 消息同步 (IndexedDB + BroadcastChannel)
│   ├── stores/
│   │   ├── conversation.ts  # 会话状态管理 (置顶/静音)
│   │   ├── message.ts       # 消息状态管理
│   │   ├── friend.ts        # 好友状态管理
│   │   └── group.ts         # 群组状态管理
│   ├── types/
│   │   └── chat.ts          # TypeScript 类型定义
│   ├── utils/
│   │   ├── sanitize.ts      # XSS 防护
│   │   └── idb.ts           # IndexedDB 工具
│   ├── views/
│   │   ├── ChatRoom.vue     # 聊天页面
│   │   └── Login.vue        # 登录页面
│   ├── router/
│   │   └── index.ts         # 路由配置
│   ├── App.vue
│   ├── main.ts
│   └── env.d.ts
├── index.html
├── package.json
├── vite.config.ts
├── tsconfig.json
├── tailwind.config.js
└── postcss.config.js
```

---

## 🔐 安全规范

1. **XSS 防护**: 所有用户内容必须通过 `DOMPurify.sanitize()` 处理
2. **禁止 v-html**: 直接渲染用户输入必须使用安全函数
3. **Token 管理**: 仅在内存和 sessionStorage 中存储，WebSocket 握手时传递
4. **消息存储**: 使用 IndexedDB，禁止使用 localStorage/sessionStorage 存储消息

---

## 📝 技术栈

- Vue 3.4+ (Composition API, `<script setup>`)
- TypeScript 5+ (strict 模式)
- Vite 5+
- Pinia 2+ (状态管理)
- Vue Router 4+
- TailwindCSS 3.4+
- @tanstack/vue-virtual (虚拟列表)
- idb (IndexedDB)
- dompurify (XSS)
- nanoid (ID 生成)
- dayjs (时间处理)

---

## ✨ 新增功能

### 文件消息
- 支持图片、语音、文件上传
- 自动按类型/日期存储 (uploads/image|audio|document/yyyy/MM/dd/)
- 图片点击预览弹窗 (支持下载)
- 语音内置播放器

### 用户状态 (Redis存储)
- **置顶**: 每个用户独立的置顶会话列表
- **静音**: 每个用户独立的静音会话列表
- API: `POST /api/chat/conversation/{id}/pin`, `POST /api/chat/conversation/{id}/mute`

### 会话列表
- 显示总会话数 `totalCount`
- 显示消息类型预览: [图片]/[语音]/[视频]/[文件]

### 图片预览
- 点击图片弹出预览弹窗
- 支持下载原图
- 点击背景/ESC 关闭

---

## 📄 许可证

MIT