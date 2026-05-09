/**
 * ============================================
 * Chat Application Type Definitions
 * WebSocket JSON Protocol TypeScript Types
 * ============================================
 */

// ============================================
// WebSocket Payload Types (Client -> Server)
// ============================================

/**
 * Client WebSocket payload types
 */
export type ClientPayloadType =
  | 'auth'
  | 'ping'
  | 'send'
  | 'ack'
  | 'fetch_history';

/**
 * Base client payload structure
 * All client messages follow this format
 */
export interface BaseClientPayload {
  type: ClientPayloadType;
  id: string;
  timestamp: number;
}

/**
 * Authentication payload
 * Sent during WebSocket handshake/connection
 */
export interface AuthPayload extends BaseClientPayload {
  type: 'auth';
  payload: {
    token: string;
    deviceId?: string;
    clientInfo?: {
      platform: string;
      version: string;
      language: string;
    };
  };
}

/**
 * Ping payload for heartbeat
 */
export interface PingPayload extends BaseClientPayload {
  type: 'ping';
  payload: Record<string, never>;
}

/**
 * Send message payload
 */
export interface SendPayload extends BaseClientPayload {
  type: 'send';
  payload: {
    conversationId: string;
    content: string;
    contentType?: 'text' | 'image' | 'file';
    metadata?: Record<string, unknown>;
  };
}

/**
 * ACK payload for message delivery confirmation
 */
export interface AckPayload extends BaseClientPayload {
  type: 'ack';
  payload: {
    messageId: string;
    status: 'delivered' | 'read';
  };
}

/**
 * Fetch history payload with cursor-based pagination
 */
export interface FetchHistoryPayload extends BaseClientPayload {
  type: 'fetch_history';
  payload: {
    conversationId: string;
    cursor?: string;
    limit?: number;
  };
}

/**
 * Union type for all client payloads
 */
export type ClientPayload =
  | AuthPayload
  | PingPayload
  | SendPayload
  | AckPayload
  | FetchHistoryPayload;

// ============================================
// WebSocket Response Types (Server -> Client)
// ============================================

/**
 * Server WebSocket response types
 */
export type ServerPayloadType =
  | 'auth_ok'
  | 'pong'
  | 'message'
  | 'ack_ok'
  | 'history'
  | 'error';

/**
 * Base server response structure
 */
export interface BaseServerPayload {
  type: ServerPayloadType;
  timestamp: number;
  id?: string;
}

/**
 * Authentication success response
 */
export interface AuthOkPayload extends BaseServerPayload {
  type: 'auth_ok';
  payload: {
    userId: string;
    userInfo: {
      nickname: string;
      avatar?: string;
    };
    serverTime: number;
  };
}

/**
 * Pong response for heartbeat
 */
export interface PongPayload extends BaseServerPayload {
  type: 'pong';
  payload: Record<string, never>;
}

/**
 * Incoming message from server
 */
export interface MessagePayload extends BaseServerPayload {
  type: 'message';
  payload: {
    message: Message;
  };
}

/**
 * ACK confirmation for sent message
 */
export interface AckOkPayload extends BaseServerPayload {
  type: 'ack_ok';
  payload: {
    messageId: string;
    status: 'sent' | 'delivered' | 'read';
    timestamp: number;
  };
}

/**
 * History response with cursor pagination
 */
export interface HistoryPayload extends BaseServerPayload {
  type: 'history';
  payload: {
    messages: Message[];
    cursor?: string;
    hasMore: boolean;
  };
}

/**
 * Error response from server
 */
export interface ErrorPayload extends BaseServerPayload {
  type: 'error';
  payload: {
    code: string;
    message: string;
    details?: Record<string, unknown>;
  };
}

/**
 * Union type for all server payloads
 */
export type ServerPayload =
  | AuthOkPayload
  | PongPayload
  | MessagePayload
  | AckOkPayload
  | HistoryPayload
  | ErrorPayload;

// ============================================
// Message Types
// ============================================

/**
 * Message status enum
 */
export type MessageStatus = 'sending' | 'sent' | 'delivered' | 'read' | 'failed';

/**
 * Message content type
 */
export type MessageContentType = 'text' | 'image' | 'file' | 'audio' | 'video';

/**
 * Message entity
 */
export interface Message {
  id: string;
  conversationId: string;
  senderId: string;
  content: string;
  contentType: MessageContentType;
  status: MessageStatus;
  createdAt: number;
  updatedAt?: number;
  metadata?: Record<string, unknown>;
  replyTo?: string;
  reactions?: MessageReaction[];
}

/**
 * Message reaction
 */
export interface MessageReaction {
  userId: string;
  emoji: string;
  timestamp: number;
}

// ============================================
// Conversation Types
// ============================================

/**
 * Conversation type
 */
export type ConversationType = 'private' | 'group' | 'channel';

/**
 * Conversation entity
 */
export interface Conversation {
  id: string;
  type: ConversationType;
  name: string;
  avatar?: string;
  participants: Participant[];
  lastMessage?: {
    id: string;
    content: string;
    contentType?: string;
    senderId: string;
    createdAt: number;
  };
  unreadCount: number;
  hasNewMessages: boolean;
  pinned: boolean;
  muted: boolean;
  createdAt: number;
  updatedAt?: number;
}

/**
 * Participant in a conversation
 */
export interface Participant {
  userId: string;
  nickname: string;
  avatar?: string;
  role?: 'owner' | 'admin' | 'member';
  joinedAt?: number;
  lastSeen?: number;
}

// ============================================
// WebSocket Connection Status
// ============================================

/**
 * WebSocket connection status
 */
export type WSStatus = 'connecting' | 'connected' | 'disconnecting' | 'disconnected' | 'reconnecting';

/**
 * WebSocket connection state info
 */
export interface WSStateInfo {
  status: WSStatus;
  reconnectAttempts: number;
  lastConnectedAt?: number;
  lastError?: string;
}

// ============================================
// Queue Types
// ============================================

/**
 * Queued message for offline/reconnecting scenarios
 */
export interface QueuedMessage {
  id: string;
  payload: ClientPayload;
  attempts: number;
  maxAttempts: number;
  createdAt: number;
}

// ============================================
// Pagination Types
// ============================================

/**
 * Cursor-based pagination result
 */
export interface CursorPageResult<T> {
  items: T[];
  cursor?: string;
  hasMore: boolean;
}

// ============================================
// User Types
// ============================================

/**
 * Current user info
 */
export interface CurrentUser {
  userId: string;
  nickname: string;
  avatar?: string;
  token: string;
}

// ============================================
// Event Types for Custom Events
// ============================================

/**
 * WebSocket event names
 */
export type WSEventName =
  | 'open'
  | 'close'
  | 'error'
  | 'message'
  | 'status_change'
  | 'reconnecting'
  | 'auth_error';

/**
 * WebSocket event payload
 */
export interface WSEventPayload {
  open: { timestamp: number };
  close: { code: number; reason: string; timestamp: number };
  error: { error: Error; timestamp: number };
  message: { payload: ServerPayload; timestamp: number };
  status_change: { status: WSStatus; timestamp: number };
  reconnecting: { attempt: number; timestamp: number };
  auth_error: { code: string; message: string; timestamp: number };
}

// ============================================
// Broadcast Channel Types (Multi-tab sync)
// ============================================

/**
 * Broadcast channel message types
 */
export type BroadcastChannelMessageType =
  | 'active_conversation_change'
  | 'unread_count_update'
  | 'message_sync'
  | 'logout';

/**
 * Broadcast channel message
 */
export interface BroadcastMessage {
  type: BroadcastChannelMessageType;
  payload: Record<string, unknown>;
  timestamp: number;
  tabId: string;
}

// ============================================
// IndexedDB Types
// ============================================

/**
 * IndexedDB store names
 */
export type IDBStoreName = 'messages' | 'conversations' | 'pending_messages';

/**
 * Message stored in IndexedDB
 */
export interface StoredMessage extends Message {
  storedAt: number;
}

/**
 * Conversation stored in IndexedDB
 */
export interface StoredConversation extends Conversation {
  storedAt: number;
}

// ============================================
// Friend Types
// ============================================

/**
 * Friend status
 */
export type FriendStatus = 'pending' | 'accepted' | 'rejected' | 'blocked';

/**
 * Friend entity
 */
export interface Friend {
  id: string;
  userId: string;
  nickname: string;
  avatar?: string;
  status: FriendStatus;
  addedAt: number;
  lastChatAt?: number;
  unreadCount: number;
}

/**
 * Friend request
 */
export interface FriendRequest {
  id: string;
  fromUserId: string;
  fromNickname: string;
  fromAvatar?: string;
  toUserId: string;
  toNickname?: string;
  toAvatar?: string;
  status: 'pending' | 'accepted' | 'rejected';
  message?: string;
  createdAt: number;
  handledAt?: number;
}

/**
 * Search user result
 */
export interface SearchUserResult {
  userId: string;
  nickname: string;
  avatar?: string;
  isFriend: boolean;
  hasPendingRequest: boolean;
}

// ============================================
// Group Types
// ============================================

/**
 * Group member role
 */
export type GroupMemberRole = 'owner' | 'admin' | 'member';

/**
 * Group member
 */
export interface GroupMember {
  userId: string;
  nickname: string;
  avatar?: string;
  role: GroupMemberRole;
  joinedAt: number;
  lastSeen?: number;
  muted?: boolean;
}

/**
 * Group info
 */
export interface Group {
  id: string;
  name: string;
  avatar?: string;
  description?: string;
  ownerId: string;
  myRole?: 'owner' | 'admin' | 'member';
  members: GroupMember[];
  memberCount: number;
  createdAt: number;
  updatedAt?: number;
  pinned: boolean;
  muted: boolean;
  unreadCount: number;
  isMuted: boolean;
  conversationId?: string;
}

/**
 * Group invitation
 */
export interface GroupInvite {
  id: string;
  groupId: string;
  groupName: string;
  fromUserId: string;
  fromNickname: string;
  toUserId: string;
  status: 'pending' | 'accepted' | 'rejected';
  message?: string;
  createdAt: number;
  handledAt?: number;
}

/**
 * Group join request
 */
export interface GroupJoinRequest {
  id: string;
  groupId: string;
  groupName: string;
  userId: string;
  nickname: string;
  avatar?: string;
  message?: string;
  status: 'pending' | 'accepted' | 'rejected';
  createdAt: number;
  handledAt?: number;
}