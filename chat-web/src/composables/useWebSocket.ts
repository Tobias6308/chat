import { ref, onUnmounted, onMounted, computed } from 'vue';
import { nanoid } from 'nanoid';
import type {
  ClientPayload,
  ServerPayload,
  WSStatus,
  WSStateInfo,
  QueuedMessage,
  WSEventName,
  WSEventPayload
} from '@/types/chat';

/**
 * ============================================
 * useWebSocket Composable
 * WebSocket Connection Management with:
 * - JSON serialization/deserialization
 * - Exponential backoff reconnection
 * - Heartbeat/ping-pong
 * - Visibility change downgrade
 * - Message queue for offline scenarios
 * - Event subscription system
 * - Cleanup on unmount
 * ============================================
 */

// Configuration constants
const WS_URL = import.meta.env.VITE_WS_URL || 'ws://localhost:8081/ws';
const RECONNECT_BASE_DELAY = 1000; // 1 second
const RECONNECT_MAX_DELAY = 30000; // 30 seconds
const RECONNECT_MAX_ATTEMPTS = 10;
const PING_INTERVAL = 30000; // 30 seconds
const PING_INTERVAL_BACKGROUND = 60000; // 60 seconds (background mode)
const QUEUE_MAX_ATTEMPTS = 5;
const MESSAGE_QUEUE_MAX_SIZE = 100;

// Reconnection state interface
interface ReconnectState {
  attempt: number;
  timeoutId: number | null;
  nextDelay: number;
}

// Event listener type
type EventListener<K extends WSEventName> = (payload: WSEventPayload[K]) => void;

// ============================================
// Singleton State (shared across all components)
// ============================================

let ws: WebSocket | null = null;
const status = ref<WSStatus>('disconnected');
const stateInfo = ref<WSStateInfo>({
  status: 'disconnected',
  reconnectAttempts: 0,
  lastConnectedAt: undefined,
  lastError: undefined
});
const heartbeat = ref({
  intervalId: null as number | null,
  lastPongTime: 0,
  isBackground: false,
  missedPongs: 0
});
const reconnectState = ref<ReconnectState>({
  attempt: 0,
  timeoutId: null,
  nextDelay: RECONNECT_BASE_DELAY
});
const messageQueue = ref<QueuedMessage[]>([]);
const eventListeners = new Map<WSEventName, Set<EventListener<any>>>();
const authToken = ref<string | null>(null);
const isConnected = computed(() => status.value === 'connected');

/**
 * useWebSocket composable factory
 * Manages WebSocket connection with robust error handling and reconnection logic
 * Uses singleton pattern - all components share the same WebSocket instance
 */
export function useWebSocket() {

  // ============================================
  // Event System
  // ============================================

  /**
   * Subscribe to WebSocket events
   * @param event - Event name
   * @param listener - Event listener callback
   * @returns Unsubscribe function
   */
  function on<K extends WSEventName>(event: K, listener: EventListener<K>): () => void {
    if (!eventListeners.has(event)) {
      eventListeners.set(event, new Set());
    }
    eventListeners.get(event)!.add(listener);

    // Return unsubscribe function
    return () => {
      const listeners = eventListeners.get(event);
      if (listeners) {
        listeners.delete(listener);
      }
    };
  }

  /**
   * Emit event to all listeners
   * @param event - Event name
   * @param payload - Event payload
   */
  function emit<K extends WSEventName>(event: K, payload: WSEventPayload[K]): void {
    const listeners = eventListeners.get(event);
    if (listeners) {
      listeners.forEach(listener => {
        try {
          listener(payload);
        } catch (error) {
          console.error(`[WebSocket] Event listener error for ${event}:`, error);
        }
      });
    }
  }

  // ============================================
  // JSON Serialization/Deserialization
  // ============================================

  /**
   * Safely serialize payload to JSON string
   * @param payload - Client payload to serialize
   * @returns JSON string or null on failure
   */
  function serializePayload(payload: ClientPayload): string | null {
    try {
      return JSON.stringify(payload);
    } catch (error) {
      console.error('[WebSocket] JSON serialization failed:', error);
      return null;
    }
  }

  /**
   * Safely deserialize JSON string to server payload
   * @param data - JSON string from server
   * @returns Server payload or null on failure
   */
  function deserializePayload(data: string): ServerPayload | null {
    try {
      const parsed = JSON.parse(data) as ServerPayload;

      // Validate basic payload structure
      if (!parsed.type || typeof parsed.timestamp !== 'number') {
        console.warn('[WebSocket] Invalid payload structure:', parsed);
        return null;
      }

      return parsed;
    } catch (error) {
      console.error('[WebSocket] JSON deserialization failed:', error);
      emit('error', { error: error as Error, timestamp: Date.now() });
      return null;
    }
  }

  // ============================================
  // WebSocket Connection Management
  // ============================================

  /**
   * Update connection status and state info
   * @param newStatus - New connection status
   * @param error - Optional error message
   */
  function setStatus(newStatus: WSStatus, error?: string): void {
    status.value = newStatus;
    stateInfo.value = {
      ...stateInfo.value,
      status: newStatus,
      lastError: error,
      lastConnectedAt: newStatus === 'connected' ? Date.now() : stateInfo.value.lastConnectedAt
    };
    emit('status_change', { status: newStatus, timestamp: Date.now() });
  }

  /**
   * Create and configure WebSocket connection
   */
  function createConnection(): WebSocket {
    const socket = new WebSocket(WS_URL);

    // Binary type for handling data
    socket.binaryType = 'arraybuffer';

    return socket;
  }

  /**
   * Connect to WebSocket server with authentication
   * @param token - Authorization token
   */
  function connect(token: string): void {
    console.log('[WebSocket] connect() called with token:', token ? 'exists' : 'empty');
    // Store token for reconnection
    authToken.value = token;

    // Skip if already connected or connecting
    if (ws && (ws.readyState === WebSocket.OPEN || ws.readyState === WebSocket.CONNECTING)) {
      console.log('[WebSocket] Already connected or connecting, skipping');
      return;
    }

    // Close existing connection if any
    if (ws) {
      ws.close(1000, 'Reconnecting');
      ws = null;
    }

    console.log('[WebSocket] Creating new connection, status:', status.value);
    setStatus('connecting');

    console.log('[WebSocket] Creating WebSocket to:', WS_URL);
    const socket = createConnection();
    ws = socket;

    // Connection opened handler
    socket.onopen = () => {
      console.log('[WebSocket] Connection opened');
      setStatus('connected');

      // Reset reconnection state
      reconnectState.value = {
        attempt: 0,
        timeoutId: null,
        nextDelay: RECONNECT_BASE_DELAY
      };
      stateInfo.value.reconnectAttempts = 0;

      // Emit open event
      emit('open', { timestamp: Date.now() });

      // Send authentication
      sendAuth(token);

      // Start heartbeat
      startHeartbeat();

      // Flush queued messages
      flushMessageQueue();
    };

    // Message received handler
    socket.onmessage = (event) => {
      // Handle text frames (JSON)
      if (typeof event.data === 'string') {
        const payload = deserializePayload(event.data);
        if (payload) {
          handleServerPayload(payload);
          emit('message', { payload, timestamp: Date.now() });
        }
      }
    };

    // Error handler
    socket.onerror = (event) => {
      console.error('[WebSocket] Error:', event);
      emit('error', { error: new Error('WebSocket error'), timestamp: Date.now() });
    };

    // Connection closed handler
    socket.onclose = (event) => {
      console.log('[WebSocket] Connection closed:', event.code, event.reason);
      
      // Skip reconnection if intentionally closed by client
      if (event.reason === 'Client disconnect') {
        setStatus('disconnected');
        stopHeartbeat();
        emit('close', { code: event.code, reason: event.reason, timestamp: Date.now() });
        return;
      }
      
      setStatus('disconnected');

      // Stop heartbeat
      stopHeartbeat();

      // Emit close event
      emit('close', { code: event.code, reason: event.reason, timestamp: Date.now() });

      // Handle specific close codes
      handleCloseCode(event.code, event.reason);
    };
  }

  /**
   * Disconnect from WebSocket server
   */
  function disconnect(): void {
    if (reconnectState.value.timeoutId) {
      clearTimeout(reconnectState.value.timeoutId);
      reconnectState.value.timeoutId = null;
    }

    stopHeartbeat();

    if (ws) {
      setStatus('disconnecting');
      ws.close(1000, 'Client disconnect');
      ws = null;
    }

    setStatus('disconnected');
  }

  // ============================================
  // Server Payload Handling
  // ============================================

  /**
   * Handle incoming server payload
   * @param payload - Server payload
   */
  function handleServerPayload(payload: ServerPayload): void {
    switch (payload.type) {
      case 'auth_ok':
        console.log('[WebSocket] Authentication successful');
        break;

      case 'pong':
        // Reset missed pong counter on successful pong
        heartbeat.value.missedPongs = 0;
        heartbeat.value.lastPongTime = Date.now();
        break;

      case 'message':
        // Messages are handled by the store via event subscription
        break;

      case 'ack_ok':
        // ACK handling is done by the store
        break;

      case 'history':
        // History is handled by the store
        break;

      case 'error':
        console.error('[WebSocket] Server error:', payload.payload);
        if (payload.payload.code === 'AUTH_EXPIRED' || payload.payload.code === 'INVALID_TOKEN') {
          emit('auth_error', {
            code: payload.payload.code,
            message: payload.payload.message,
            timestamp: Date.now()
          });
        }
        break;
    }
  }

  // ============================================
  // Authentication
   // ============================================

  /**
   * Send authentication payload
   * @param token - Authorization token
   */
  function sendAuth(token: string): void {
    const authPayload: ClientPayload = {
      type: 'auth',
      id: nanoid(),
      timestamp: Date.now(),
      payload: {
        token,
        deviceId: getDeviceId(),
        clientInfo: {
          platform: 'web',
          version: '1.0.0',
          language: navigator.language
        }
      }
    };

    sendPayload(authPayload);
  }

  // ============================================
  // Heartbeat / Ping-Pong
   // ============================================

  /**
   * Start heartbeat with ping-pong mechanism
   */
  function startHeartbeat(): void {
    stopHeartbeat();

    const interval = heartbeat.value.isBackground
      ? PING_INTERVAL_BACKGROUND
      : PING_INTERVAL;

    heartbeat.value.intervalId = window.setInterval(() => {
      sendPing();

      // Check for missed pongs
      const timeSinceLastPong = Date.now() - heartbeat.value.lastPongTime;
      if (heartbeat.value.lastPongTime > 0 && timeSinceLastPong > interval * 2) {
        heartbeat.value.missedPongs++;
        if (heartbeat.value.missedPongs >= 2) {
          console.warn('[WebSocket] Missed pongs, reconnecting...');
          handleReconnect();
        }
      }
    }, interval);

    // Set initial pong time
    heartbeat.value.lastPongTime = Date.now();
  }

  /**
   * Stop heartbeat
   */
  function stopHeartbeat(): void {
    if (heartbeat.value.intervalId !== null) {
      clearInterval(heartbeat.value.intervalId);
      heartbeat.value.intervalId = null;
    }
  }

  /**
   * Send ping payload
   */
  function sendPing(): void {
    const pingPayload: ClientPayload = {
      type: 'ping',
      id: nanoid(),
      timestamp: Date.now(),
      payload: {}
    };

    sendPayload(pingPayload);
  }

  /**
   * Adjust heartbeat based on visibility
   * @param isVisible - Page visibility state
   */
  function handleVisibilityChange(isVisible: boolean): void {
    if (isVisible) {
      // Page became visible
      if (heartbeat.value.isBackground) {
        console.log('[WebSocket] Page visible, switching to normal heartbeat');
        heartbeat.value.isBackground = false;
        startHeartbeat();

        // Sync data after coming back
        emit('status_change', { status: 'reconnecting', timestamp: Date.now() });
      }
    } else {
      // Page became hidden
      if (!heartbeat.value.isBackground) {
        console.log('[WebSocket] Page hidden, switching to background heartbeat');
        heartbeat.value.isBackground = true;
        startHeartbeat();
      }
    }
  }

  // ============================================
  // Reconnection Logic
   // ============================================

  /**
   * Handle automatic reconnection with exponential backoff
   */
  function handleReconnect(): void {
    if (!authToken.value) {
      console.warn('[WebSocket] No auth token, skipping reconnection');
      return;
    }

    if (reconnectState.value.attempt >= RECONNECT_MAX_ATTEMPTS) {
      console.error('[WebSocket] Max reconnection attempts reached');
      setStatus('disconnected', 'Max reconnection attempts reached');
      return;
    }

    setStatus('reconnecting');
    reconnectState.value.attempt++;
    stateInfo.value.reconnectAttempts = reconnectState.value.attempt;

    const delay = reconnectState.value.nextDelay;
    console.log(`[WebSocket] Reconnecting in ${delay}ms (attempt ${reconnectState.value.attempt})`);

    emit('reconnecting', { attempt: reconnectState.value.attempt, timestamp: Date.now() });

    // Schedule reconnection
    reconnectState.value.timeoutId = window.setTimeout(() => {
      connect(authToken.value!);

      // Exponential backoff: double delay up to max
      reconnectState.value.nextDelay = Math.min(
        reconnectState.value.nextDelay * 2,
        RECONNECT_MAX_DELAY
      );
    }, delay);
  }

  /**
   * Handle WebSocket close codes
   * @param code - Close code
   * @param reason - Close reason
   */
  function handleCloseCode(code: number, _reason: string): void {
    // 1006: Abnormal closure - should reconnect
    // 4001: Invalid token - should not reconnect, redirect to login
    // 4003: Account disabled - should not reconnect

    switch (code) {
      case 1006:
        console.log('[WebSocket] Abnormal closure, attempting reconnect');
        handleReconnect();
        break;
      case 4001:
        console.error('[WebSocket] Invalid token, stopping reconnection');
        emit('auth_error', { code: 'INVALID_TOKEN', message: 'Invalid token', timestamp: Date.now() });
        break;
      case 4003:
        console.error('[WebSocket] Account disabled, stopping reconnection');
        emit('auth_error', { code: 'ACCOUNT_DISABLED', message: 'Account disabled', timestamp: Date.now() });
        break;
      default:
        // For other close codes, attempt reconnection
        if (authToken.value) {
          handleReconnect();
        }
        break;
    }
  }

  // ============================================
  // Message Queue (Offline Support)
   // ============================================

  /**
   * Add message to queue
   * @param payload - Client payload to queue
   */
  function queueMessage(payload: ClientPayload): void {
    // Remove oldest if queue is full
    if (messageQueue.value.length >= MESSAGE_QUEUE_MAX_SIZE) {
      messageQueue.value.shift();
    }

    messageQueue.value.push({
      id: payload.id,
      payload,
      attempts: 0,
      maxAttempts: QUEUE_MAX_ATTEMPTS,
      createdAt: Date.now()
    });
  }

  /**
   * Flush queued messages
   */
  function flushMessageQueue(): void {
    if (messageQueue.value.length === 0 || status.value !== 'connected') {
      return;
    }

    console.log(`[WebSocket] Flushing ${messageQueue.value.length} queued messages`);

    const toRemove: string[] = [];

    messageQueue.value.forEach((item) => {
      if (item.attempts >= item.maxAttempts) {
        toRemove.push(item.id);
        return;
      }

      const sent = sendPayload(item.payload);
      if (sent) {
        item.attempts++;
      }
    });

    // Remove failed items
    messageQueue.value = messageQueue.value.filter(
      item => !toRemove.includes(item.id)
    );
  }

  // ============================================
  // Payload Sending
   // ============================================

  /**
   * Send payload to server
   * @param payload - Client payload to send
   * @param queueIfFailed - Whether to queue if send fails
   * @returns true if sent successfully
   */
  function sendPayload(payload: ClientPayload, queueIfFailed = true): boolean {
    console.log('[WebSocket] sendPayload called, type:', payload.type, 'readyState:', ws?.readyState);
    if (!ws || ws.readyState !== WebSocket.OPEN) {
      console.warn('[WebSocket] Cannot send, not connected, readyState:', ws?.readyState);
      if (queueIfFailed) {
        queueMessage(payload);
      }
      return false;
    }

    const jsonString = serializePayload(payload);
    if (!jsonString) {
      if (queueIfFailed) {
        queueMessage(payload);
      }
      return false;
    }

    try {
      console.log('[WebSocket] Sending payload:', JSON.stringify(payload));
      ws.send(jsonString);
      return true;
    } catch (error) {
      console.error('[WebSocket] Send error:', error);
      if (queueIfFailed) {
        queueMessage(payload);
      }
      return false;
    }
  }

  // ============================================
  // Public API Methods
   // ============================================

  /**
   * Send message payload
   * @param conversationId - Conversation ID
   * @param content - Message content
   * @param contentType - Content type
   * @returns Message ID
   */
  function sendMessage(
    conversationId: string,
    content: string,
    contentType: 'text' | 'image' | 'file' = 'text'
  ): string {
    console.log('[WebSocket] sendMessage called, readyState:', ws?.readyState, 'status:', status.value);
    // Auto-connect if not connected but token exists
    if (ws?.readyState !== WebSocket.OPEN) {
      console.log('[WebSocket] Not connected (readyState not OPEN), queuing message');
      if (authToken.value) {
        // Use queue instead of forcing immediate send
        console.log('[WebSocket] Not connected, queuing message');
      }
      // Queue the message for when connection is established
      const id = nanoid();
      const payload: ClientPayload = {
        type: 'send',
        id,
        timestamp: Date.now(),
        payload: { conversationId, content, contentType }
      };
      queueMessage(payload);
      return id;
    }

    console.log('[WebSocket] Connected, sending message directly');
    const id = nanoid();

    const payload: ClientPayload = {
      type: 'send',
      id,
      timestamp: Date.now(),
      payload: {
        conversationId,
        content,
        contentType
      }
    };

    sendPayload(payload);
    return id;
  }

  /**
   * Send ACK for message delivery
   * @param messageId - Message ID
   * @param status - ACK status
   */
  function sendAck(messageId: string, status: 'delivered' | 'read'): void {
    // Skip if not connected
    if (ws?.readyState !== WebSocket.OPEN) {
      return;
    }
    
    const payload: ClientPayload = {
      type: 'ack',
      id: nanoid(),
      timestamp: Date.now(),
      payload: {
        messageId,
        status
      }
    };

    sendPayload(payload);
  }

  /**
   * Fetch conversation history
   * @param conversationId - Conversation ID
   * @param cursor - Cursor for pagination
   * @param limit - Number of messages to fetch
   */
  function fetchHistory(conversationId: string, cursor?: string, limit = 50): void {
    const payload: ClientPayload = {
      type: 'fetch_history',
      id: nanoid(),
      timestamp: Date.now(),
      payload: {
        conversationId,
        cursor,
        limit
      }
    };

    sendPayload(payload);
  }

  // ============================================
  // Device ID Generation
   // ============================================

  /**
   * Get or generate device ID
   * @returns Device ID
   */
  function getDeviceId(): string {
    // Use sessionStorage for device ID (not for messages)
    let deviceId = sessionStorage.getItem('chat_device_id');
    if (!deviceId) {
      deviceId = nanoid();
      sessionStorage.setItem('chat_device_id', deviceId);
    }
    return deviceId;
  }

  // ============================================
  // Lifecycle Hooks
   // ============================================

  /**
   * Setup visibility change listener
   */
  function setupVisibilityHandler(): void {
    const handleVisibility = (): void => {
      handleVisibilityChange(!document.hidden);
    };
    document.addEventListener('visibilitychange', handleVisibility);
  }

  /**
   * Initialize WebSocket connection
   */
  onMounted(() => {
    setupVisibilityHandler();
  });

  /**
   * Cleanup on unmount
   */
  onUnmounted(() => {
    disconnect();
    messageQueue.value = [];
    eventListeners.clear();
  });

  // ============================================
  // Return Public API
   // ============================================

  return {
    // State
    status: readonly(status),
    stateInfo: readonly(stateInfo),
    isConnected,

    // Connection
    connect,
    disconnect,

    // Messaging
    sendMessage,
    sendAck,
    sendPayload,

    // History
    fetchHistory,

    // Events
    on,

    // Queue
    messageQueue: readonly(messageQueue),

    // Heartbeat control
    heartbeat: readonly(heartbeat)
  };
}

/**
 * Helper to create readonly refs
 */
function readonly<T>(value: T): T {
  return value;
}