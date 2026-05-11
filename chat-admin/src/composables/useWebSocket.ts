import { ref } from 'vue';

interface WSMessage {
  type: string;
  payload: any;
}

interface WSStatus {
  value: 'disconnected' | 'connecting' | 'connected';
}

interface HeartbeatState {
  intervalId: number | null;
  lastPongTime: number;
  missedPongs: number;
}

const WS_URL = (import.meta as any).env?.VITE_WS_URL || 'ws://localhost:8081/ws';

let ws: WebSocket | null = null;
const status = ref<WSStatus['value']>('disconnected');
const eventListeners = new Map<string, Set<(data: any) => void>>();
const messageQueue: WSMessage[] = [];
let reconnectTimeout: number | null = null;
let reconnectAttempt = 0;
const MAX_RECONNECT_ATTEMPTS = 10;
const RECONNECT_DELAY = 3000;
const HEARTBEAT_INTERVAL = 30000; // 30秒心跳

// 心跳状态
const heartbeat: HeartbeatState = {
  intervalId: null,
  lastPongTime: 0,
  missedPongs: 0
};

// 保存 token 用于重连
let savedToken: string | null = null;

// connect 函数声明（供 doReconnect 使用）
let connect: ((token?: string) => void) | null = null;

function startHeartbeat() {
  if (heartbeat.intervalId) return;

  heartbeat.lastPongTime = Date.now();
  heartbeat.missedPongs = 0;

  heartbeat.intervalId = window.setInterval(() => {
    // 发送 ping
    if (ws && ws.readyState === WebSocket.OPEN) {
      ws.send(JSON.stringify({
        type: 'ping',
        payload: { timestamp: Date.now() }
      }));
    }

    // 检查是否收到 pong
    const timeSinceLastPong = Date.now() - heartbeat.lastPongTime;
    if (heartbeat.lastPongTime > 0 && timeSinceLastPong > HEARTBEAT_INTERVAL * 2) {
      heartbeat.missedPongs++;
      console.warn(`[Admin WS] Missed pong (${heartbeat.missedPongs}/2)`);

      if (heartbeat.missedPongs >= 2) {
        console.warn('[Admin WS] Heartbeat timeout, reconnecting...');
        stopHeartbeat();
        doReconnect();
      }
    }
  }, HEARTBEAT_INTERVAL);
}

function stopHeartbeat() {
  if (heartbeat.intervalId) {
    clearInterval(heartbeat.intervalId);
    heartbeat.intervalId = null;
  }
}

function doReconnect() {
  if (reconnectAttempt < MAX_RECONNECT_ATTEMPTS) {
    if (reconnectTimeout) clearTimeout(reconnectTimeout);
    reconnectTimeout = window.setTimeout(() => {
      reconnectAttempt++;
      console.log(`[Admin WS] Reconnecting (${reconnectAttempt}/${MAX_RECONNECT_ATTEMPTS})...`);
      connect?.(savedToken || undefined);
    }, RECONNECT_DELAY);
  }
}

export function useWebSocket() {
  // 将 connect 函数赋值给外部变量，供 doReconnect 使用
  connect = function(token?: string) {
    if (ws && (ws.readyState === WebSocket.OPEN || ws.readyState === WebSocket.CONNECTING)) {
      return;
    }

    savedToken = token || savedToken;
    status.value = 'connecting';

    ws = new WebSocket(WS_URL);

    ws.onopen = () => {
      console.log('[Admin WS] Connected');
      status.value = 'connected';
      reconnectAttempt = 0;

      // 启动心跳
      startHeartbeat();

      if (savedToken) {
        send({
          type: 'auth',
          payload: { token: savedToken }
        });
      }

      flushQueue();
    };

    ws.onmessage = (event) => {
      try {
        const payload = JSON.parse(event.data);
        console.log('[Admin WS] Received:', payload.type, payload);

        // 处理 pong 响应
        if (payload.type === 'pong') {
          heartbeat.missedPongs = 0;
          heartbeat.lastPongTime = Date.now();
          return;
        }

        const listeners = eventListeners.get(payload.type);
        if (listeners) {
          listeners.forEach(cb => cb({ payload }));
        } else {
          console.log('[Admin WS] No listeners for type:', payload.type);
        }
      } catch (e) {
        console.error('[Admin WS] Parse error:', e);
      }
    };

    ws.onclose = (event) => {
      console.log('[Admin WS] Closed:', event.code);
      status.value = 'disconnected';
      ws = null;

      // 停止心跳
      stopHeartbeat();

      // 自动重连
      doReconnect();
    };

    ws.onerror = (error) => {
      console.error('[Admin WS] Error:', error);
    };
  }

  function disconnect() {
    if (reconnectTimeout) {
      clearTimeout(reconnectTimeout);
      reconnectTimeout = null;
    }
    stopHeartbeat();
    if (ws) {
      ws.close(1000, 'Manual disconnect');
      ws = null;
    }
    status.value = 'disconnected';
  }

  function send(msg: WSMessage) {
    if (ws && ws.readyState === WebSocket.OPEN) {
      ws.send(JSON.stringify(msg));
    } else {
      messageQueue.push(msg);
    }
  }

  function flushQueue() {
    while (messageQueue.length > 0 && ws?.readyState === WebSocket.OPEN) {
      const msg = messageQueue.shift();
      if (msg) ws.send(JSON.stringify(msg));
    }
  }

  function on(eventType: string, callback: (data: any) => void) {
    if (!eventListeners.has(eventType)) {
      eventListeners.set(eventType, new Set());
    }
    eventListeners.get(eventType)!.add(callback);

    return () => {
      eventListeners.get(eventType)?.delete(callback);
    };
  }

  function off(eventType: string, callback: (data: any) => void) {
    eventListeners.get(eventType)?.delete(callback);
  }

  return {
    status,
    connect,
    disconnect,
    send,
    on,
    off
  };
}