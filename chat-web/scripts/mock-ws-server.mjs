/**
 * ============================================
 * Mock WebSocket Server
 * 用于本地开发联调，支持:
 * - JSON 协议认证
 * - 心跳 (ping/pong)
 * - 消息发送与 ACK
 * - 历史消息游标分页
 * - 多标签页广播模拟
 * ============================================
 */

import { WebSocketServer, WebSocket } from 'ws';

const PORT = 8080;
const WS_PATH = '/ws';

// 模拟数据存储
const connectedClients = new Map();
const conversations = new Map([
  ['conv_1', {
    id: 'conv_1',
    name: '张三',
    participants: ['user_1', 'current_user'],
    messages: generateMockMessages('conv_1', 'user_1', 20)
  }],
  ['conv_2', {
    id: 'conv_2',
    name: '前端开发群',
    participants: ['user_2', 'user_3', 'current_user'],
    messages: generateMockMessages('conv_2', 'user_2', 30)
  }],
  ['conv_3', {
    id: 'conv_3',
    name: '产品经理',
    participants: ['user_4', 'current_user'],
    messages: generateMockMessages('conv_3', 'user_4', 10)
  }]
]);

// 生成模拟消息
function generateMockMessages(conversationId, senderId, count) {
  const messages = [];
  const contents = [
    '你好！最近怎么样？',
    '今天的工作完成了吗？',
    '好的，没问题！',
    '我这边有点忙，稍后回复你',
    '收到，谢谢！',
    '关于这个需求，我们需要讨论一下',
    '好的，我来实现这个功能',
    '测试通过了，代码已经合并',
    '有新版本发布了，大家记得更新',
    '周末有个技术分享会，要参加吗？'
  ];

  for (let i = 0; i < count; i++) {
    messages.push({
      id: `msg_${conversationId}_${i}`,
      conversationId,
      senderId: i % 2 === 0 ? senderId : 'current_user',
      content: contents[i % contents.length],
      contentType: 'text',
      status: 'read',
      createdAt: Date.now() - (count - i) * 60000,
      metadata: {}
    });
  }

  return messages;
}

// 生成 nanoid 风格 ID
function generateId() {
  const chars = 'abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789';
  let result = '';
  for (let i = 0; i < 21; i++) {
    result += chars.charAt(Math.floor(Math.random() * chars.length));
  }
  return result;
}

// 创建服务器
const wss = new WebSocketServer({ port: PORT, path: WS_PATH });

console.log(`[Mock WS] 服务器启动: ws://localhost:${PORT}${WS_PATH}`);
console.log('[Mock WS] 支持的协议: auth, ping, send, ack, fetch_history');

// 处理连接
wss.on('connection', (ws, req) => {
  const clientId = generateId();
  connectedClients.set(clientId, {
    ws,
    userId: null,
    authenticated: false,
    conversations: new Set()
  });

  console.log(`[Mock WS] 新连接: ${clientId}`);

  // 消息处理
  ws.on('message', (data) => {
    try {
      const message = JSON.parse(data.toString());
      handleMessage(clientId, message);
    } catch (error) {
      console.error('[Mock WS] 消息解析失败:', error);
      sendError(ws, 'INVALID_JSON', '消息格式错误');
    }
  });

  // 关闭处理
  ws.on('close', (code, reason) => {
    console.log(`[Mock WS] 断开: ${clientId}, code: ${code}, reason: ${reason.toString()}`);
    connectedClients.delete(clientId);
  });

  // 错误处理
  ws.on('error', (error) => {
    console.error(`[Mock WS] 错误: ${clientId}`, error);
  });
});

// 处理客户端消息
function handleMessage(clientId, message) {
  const client = connectedClients.get(clientId);
  if (!client) return;

  const { type, id, payload, timestamp } = message;

  console.log(`[Mock WS] 收到: ${type}`, { clientId, id });

  switch (type) {
    case 'auth':
      handleAuth(client, id, payload);
      break;
    case 'ping':
      handlePing(client, id, timestamp);
      break;
    case 'send':
      handleSend(client, id, payload);
      break;
    case 'ack':
      handleAck(client, id, payload);
      break;
    case 'fetch_history':
      handleFetchHistory(client, id, payload);
      break;
    default:
      console.warn(`[Mock WS] 未知消息类型: ${type}`);
  }
}

// 认证处理
function handleAuth(client, id, payload) {
  const { token, deviceId, clientInfo } = payload;

  // 模拟认证逻辑
  if (token && token.startsWith('demo_')) {
    const userId = 'current_user';
    client.userId = userId;
    client.authenticated = true;

    // 发送认证成功响应
    const response = {
      type: 'auth_ok',
      id,
      payload: {
        userId,
        userInfo: {
          nickname: '我',
          avatar: undefined
        },
        serverTime: Date.now()
      },
      timestamp: Date.now()
    };

    client.ws.send(JSON.stringify(response));
    console.log(`[Mock WS] 认证成功: ${clientId(client)}`);

    // 模拟接收新消息 (延迟 2 秒)
    setTimeout(() => {
      if (client.authenticated) {
        simulateIncomingMessage(client);
      }
    }, 2000);
  } else {
    // 认证失败
    const response = {
      type: 'error',
      id,
      payload: {
        code: 'INVALID_TOKEN',
        message: '无效的令牌'
      },
      timestamp: Date.now()
    };
    client.ws.send(JSON.stringify(response));
  }
}

// Ping 处理
function handlePing(client, id, timestamp) {
  const response = {
    type: 'pong',
    timestamp: Date.now()
  };
  client.ws.send(JSON.stringify(response));
}

// 消息发送处理
function handleSend(client, id, payload) {
  if (!client.authenticated) {
    sendError(client.ws, 'NOT_AUTHENTICATED', '未认证');
    return;
  }

  const { conversationId, content, contentType = 'text', metadata = {} } = payload;

  // 创建消息
  const message = {
    id: generateId(),
    conversationId,
    senderId: client.userId,
    content,
    contentType,
    status: 'sent',
    createdAt: Date.now(),
    metadata
  };

  // 保存到会话
  const conv = conversations.get(conversationId);
  if (conv) {
    conv.messages.unshift(message);
  }

  // 发送 ACK 确认
  const ackResponse = {
    type: 'ack_ok',
    id,
    payload: {
      messageId: message.id,
      status: 'sent',
      timestamp: Date.now()
    },
    timestamp: Date.now()
  };
  client.ws.send(JSON.stringify(ackResponse));

  // 模拟回复 (30% 概率)
  if (Math.random() < 0.3) {
    setTimeout(() => {
      simulateIncomingMessage(client, conversationId);
    }, 1000 + Math.random() * 2000);
  }
}

// ACK 处理
function handleAck(client, id, payload) {
  const { messageId, status } = payload;
  console.log(`[Mock WS] ACK: ${messageId} -> ${status}`);
}

// 历史消息获取
function handleFetchHistory(client, id, payload) {
  if (!client.authenticated) {
    sendError(client.ws, 'NOT_AUTHENTICATED', '未认证');
    return;
  }

  const { conversationId, cursor, limit = 50 } = payload;
  const conv = conversations.get(conversationId);

  if (!conv) {
    sendError(client.ws, 'CONVERSATION_NOT_FOUND', '会话不存在');
    return;
  }

  // 游标分页逻辑
  let messages = [...conv.messages];

  if (cursor) {
    const cursorTime = parseInt(cursor, 10);
    messages = messages.filter(m => m.createdAt < cursorTime);
  }

  // 返回指定数量
  const hasMore = messages.length > limit;
  const resultMessages = messages.slice(0, limit);
  const lastMessage = resultMessages[resultMessages.length - 1];

  const response = {
    type: 'history',
    id,
    payload: {
      messages: resultMessages,
      cursor: hasMore && lastMessage ? String(lastMessage.createdAt) : undefined,
      hasMore
    },
    timestamp: Date.now()
  };

  client.ws.send(JSON.stringify(response));
  console.log(`[Mock WS] 历史消息: ${conversationId}, ${resultMessages.length} 条, hasMore: ${hasMore}`);
}

// 模拟收到新消息
function simulateIncomingMessage(client, conversationId = null) {
  // 随机选择一个会话
  const convIds = Array.from(conversations.keys());
  const targetConvId = conversationId || convIds[Math.floor(Math.random() * convIds.length)];
  const conv = conversations.get(targetConvId);

  if (!conv) return;

  // 随机选择发送者
  const otherParticipants = conv.participants.filter(p => p !== client.userId);
  const senderId = otherParticipants[Math.floor(Math.random() * otherParticipants.length)];

  const contents = [
    '收到，我看一下',
    '好的，明白了',
    '这个问题我来处理',
    '已经修复了',
    '等一下，我正在忙',
    '有空聊一下',
    '这个功能很重要',
    '我来测试一下',
    'OK，没问题',
    '稍等，正在更新'
  ];

  const message = {
    id: generateId(),
    conversationId: targetConvId,
    senderId,
    content: contents[Math.floor(Math.random() * contents.length)],
    contentType: 'text',
    status: 'delivered',
    createdAt: Date.now(),
    metadata: {}
  };

  // 保存消息
  conv.messages.unshift(message);

  // 推送给客户端
  const response = {
    type: 'message',
    payload: {
      message
    },
    timestamp: Date.now()
  };

  client.ws.send(JSON.stringify(response));
  console.log(`[Mock WS] 推送消息: ${targetConvId} from ${senderId}`);
}

// 发送错误响应
function sendError(ws, code, message) {
  const response = {
    type: 'error',
    payload: {
      code,
      message
    },
    timestamp: Date.now()
  };
  ws.send(JSON.stringify(response));
}

// 获取客户端标识
function clientId(client) {
  for (const [id, c] of connectedClients) {
    if (c === client) return id;
  }
  return 'unknown';
}

// 定期广播系统消息 (可选)
setInterval(() => {
  // 模拟系统消息
  const systemMessage = {
    type: 'message',
    payload: {
      message: {
        id: generateId(),
        conversationId: 'conv_2',
        senderId: 'system',
        content: '系统公告: 服务器将在 30 分钟后进行维护',
        contentType: 'text',
        status: 'delivered',
        createdAt: Date.now()
      }
    },
    timestamp: Date.now()
  };

  // 广播给所有认证的客户端
  for (const [id, client] of connectedClients) {
    if (client.authenticated) {
      client.ws.send(JSON.stringify(systemMessage));
    }
  }
}, 60000); // 每分钟

console.log('[Mock WS] 模拟服务器运行中...');
console.log(`[Mock WS] 监听端口: ${PORT}`);