import { ref, onMounted, onUnmounted } from 'vue';
import { openDB, type IDBPDatabase } from 'idb';
import type {
  StoredMessage,
  StoredConversation,
  BroadcastMessage,
  CursorPageResult,
  Message,
  Conversation
} from '@/types/chat';

// ============================================
// IndexedDB Configuration
// ============================================

const DB_NAME = 'chat-web-db';
const DB_VERSION = 1;

interface ChatDB {
  messages: StoredMessage;
  conversations: StoredConversation;
  pending_messages: StoredMessage;
}

// ============================================
// useMessageSync Composable
// Handles:
// - IndexedDB persistence for offline support
// - BroadcastChannel for multi-tab synchronization
// - Cursor-based pagination
// - Memory management (500 message limit per conversation)
// ============================================

export function useMessageSync() {
  // IndexedDB instance
  const db = ref<IDBPDatabase<ChatDB> | null>(null);

  // BroadcastChannel for multi-tab sync
  const broadcastChannel = ref<BroadcastChannel | null>(null);

  // Current active conversation (synced across tabs)
  const activeConversationId = ref<string | null>(null);

  // Unread count (synced across tabs)
  const totalUnreadCount = ref(0);

  // Loading states
  const isLoading = ref(false);

  // Tab ID for broadcast messages
  const tabId = ref<string | null>(null);

  // ============================================
// IndexedDB Operations
// ============================================

  /**
   * Initialize IndexedDB
   */
  async function initDB(): Promise<void> {
    try {
      db.value = await openDB<ChatDB>(DB_NAME, DB_VERSION, {
        upgrade(database) {
          // Messages store
          if (!database.objectStoreNames.contains('messages')) {
            const messageStore = database.createObjectStore('messages', { keyPath: 'id' });
            messageStore.createIndex('conversationId', 'conversationId');
            messageStore.createIndex('createdAt', 'createdAt');
            messageStore.createIndex('storedAt', 'storedAt');
          }

          // Conversations store
          if (!database.objectStoreNames.contains('conversations')) {
            const convStore = database.createObjectStore('conversations', { keyPath: 'id' });
            convStore.createIndex('lastMessageAt', 'lastMessageAt');
            convStore.createIndex('updatedAt', 'updatedAt');
          }

          // Pending messages store (for offline sending)
          if (!database.objectStoreNames.contains('pending_messages')) {
            const pendingStore = database.createObjectStore('pending_messages', { keyPath: 'id' });
            pendingStore.createIndex('createdAt', 'createdAt');
          }
        }
      });

      console.log('[MessageSync] IndexedDB initialized');
    } catch (error) {
      console.error('[MessageSync] Failed to initialize IndexedDB:', error);
    }
  }

  /**
   * Save message to IndexedDB
   * @param message - Message to save
   */
  async function saveMessage(message: Message): Promise<void> {
    if (!db.value) return;

    try {
      const stored: StoredMessage = {
        ...message,
        storedAt: Date.now()
      };

      await db.value.put('messages', stored);

      // Enforce memory limit (500 messages per conversation)
      await trimConversationMessages(message.conversationId);
    } catch (error) {
      console.error('[MessageSync] Failed to save message:', error);
    }
  }

  /**
   * Save multiple messages to IndexedDB
   * @param messages - Messages to save
   */
  async function saveMessages(messages: Message[]): Promise<void> {
    if (!db.value || messages.length === 0) return;

    try {
      const tx = db.value.transaction('messages', 'readwrite');
      const store = tx.objectStore('messages');

      for (const message of messages) {
        const stored: StoredMessage = {
          ...message,
          storedAt: Date.now()
        };
        await store.put(stored);
      }

      await tx.done;

      // Trim each conversation
      const convIds = [...new Set(messages.map(m => m.conversationId))];
      for (const convId of convIds) {
        await trimConversationMessages(convId);
      }
    } catch (error) {
      console.error('[MessageSync] Failed to save messages:', error);
    }
  }

  /**
   * Get messages from IndexedDB with cursor pagination
   * @param conversationId - Conversation ID
   * @param cursor - Cursor for pagination
   * @param limit - Number of messages to fetch
   * @returns CursorPageResult<Message>
   */
  async function getMessages(
    conversationId: string,
    cursor?: string,
    limit = 50
  ): Promise<CursorPageResult<Message>> {
    if (!db.value) {
      return { items: [], hasMore: false };
    }

    try {
      const index = db.value.transaction('messages', 'readonly').store.index('conversationId');

      // Get all messages for conversation (with limit + 1 to check hasMore)
      const allMessages = await index.getAll([conversationId], limit + 1);

      // Sort by createdAt descending (newest first)
      allMessages.sort((a, b) => b.createdAt - a.createdAt);

      // Apply cursor filter if provided
      let filtered = allMessages;
      if (cursor) {
        const cursorTime = parseInt(cursor, 10);
        filtered = allMessages.filter(m => m.createdAt < cursorTime);
      }

      const hasMore = filtered.length > limit;
      const items = filtered.slice(0, limit);

      return {
        items,
        cursor: hasMore && items.length > 0 ? String(items[items.length - 1].createdAt) : undefined,
        hasMore
      };
    } catch (error) {
      console.error('[MessageSync] Failed to get messages:', error);
      return { items: [], hasMore: false };
    }
  }

  /**
   * Trim conversation messages to enforce 500 limit
   * @param conversationId - Conversation ID
   */
  async function trimConversationMessages(conversationId: string): Promise<void> {
    if (!db.value) return;

    try {
      const tx = db.value.transaction('messages', 'readwrite');
      const store = tx.objectStore('messages');
      const index = store.index('conversationId');

      const allMessages = await index.getAll([conversationId]);

      if (allMessages.length > 500) {
        // Sort by createdAt ascending and keep only 500 newest
        allMessages.sort((a, b) => a.createdAt - b.createdAt);
        const toDelete = allMessages.slice(0, allMessages.length - 500);

        for (const msg of toDelete) {
          await store.delete(msg.id);
        }
      }

      await tx.done;
    } catch (error) {
      console.error('[MessageSync] Failed to trim messages:', error);
    }
  }

  /**
   * Delete message from IndexedDB
   * @param messageId - Message ID
   */
  async function deleteMessage(messageId: string): Promise<void> {
    if (!db.value) return;

    try {
      await db.value.delete('messages', messageId);
    } catch (error) {
      console.error('[MessageSync] Failed to delete message:', error);
    }
  }

  // ============================================
// Conversation Operations
// ============================================

  /**
   * Save conversation to IndexedDB
   * @param conversation - Conversation to save
   */
  async function saveConversation(conversation: Conversation): Promise<void> {
    if (!db.value) return;

    try {
      const stored: StoredConversation = {
        ...conversation,
        storedAt: Date.now()
      };

      await db.value.put('conversations', stored);
    } catch (error) {
      console.error('[MessageSync] Failed to save conversation:', error);
    }
  }

  /**
   * Get all conversations from IndexedDB
   * @returns Array of conversations
   */
  async function getConversations(): Promise<Conversation[]> {
    if (!db.value) return [];

    try {
      const all = await db.value.getAll('conversations');
      return all.sort((a, b) => (b.updatedAt || 0) - (a.updatedAt || 0));
    } catch (error) {
      console.error('[MessageSync] Failed to get conversations:', error);
      return [];
    }
  }

  /**
   * Get conversation by ID
   * @param conversationId - Conversation ID
   * @returns Conversation or undefined
   */
  async function getConversation(conversationId: string): Promise<Conversation | undefined> {
    if (!db.value) return undefined;

    try {
      return await db.value.get('conversations', conversationId);
    } catch (error) {
      console.error('[MessageSync] Failed to get conversation:', error);
      return undefined;
    }
  }

  // ============================================
// BroadcastChannel Operations (Multi-tab sync)
// ============================================

  /**
   * Initialize BroadcastChannel
   */
  function initBroadcastChannel(): void {
    try {
      broadcastChannel.value = new BroadcastChannel('chat-web-sync');

      broadcastChannel.value.onmessage = (event) => {
        handleBroadcastMessage(event.data as BroadcastMessage);
      };

      // Generate tab ID
      tabId.value = `tab_${Date.now()}_${Math.random().toString(36).substr(2, 9)}`;

      console.log('[MessageSync] BroadcastChannel initialized');
    } catch (error) {
      console.error('[MessageSync] Failed to initialize BroadcastChannel:', error);
    }
  }

  /**
   * Handle incoming broadcast message
   * @param message - Broadcast message
   */
  function handleBroadcastMessage(message: BroadcastMessage): void {
    // Ignore own messages
    if (message.tabId === tabId.value) return;

    switch (message.type) {
      case 'active_conversation_change':
        // Update active conversation from another tab
        activeConversationId.value = message.payload.conversationId as string;
        break;

      case 'unread_count_update':
        // Update unread count from another tab
        totalUnreadCount.value = message.payload.count as number;
        break;

      case 'message_sync':
        // Handle message sync from another tab
        // This is typically used to update read status
        break;

      case 'logout':
        // Handle logout from another tab
        window.dispatchEvent(new CustomEvent('force-logout'));
        break;
    }
  }

  /**
   * Broadcast message to other tabs
   * @param type - Message type
   * @param payload - Message payload
   */
  function broadcast<K extends BroadcastMessage['type']>(
    type: K,
    payload: Record<string, unknown>
  ): void {
    if (!broadcastChannel.value) return;

    const message: BroadcastMessage = {
      type,
      payload,
      timestamp: Date.now(),
      tabId: tabId.value || 'unknown'
    };

    broadcastChannel.value.postMessage(message);
  }

  /**
   * Broadcast active conversation change
   * @param conversationId - Conversation ID
   */
  function broadcastActiveConversation(conversationId: string | null): void {
    activeConversationId.value = conversationId;
    broadcast('active_conversation_change', { conversationId: conversationId || '' });
  }

  /**
   * Broadcast unread count update
   * @param count - Total unread count
   */
  function broadcastUnreadCount(count: number): void {
    totalUnreadCount.value = count;
    broadcast('unread_count_update', { count });
  }

  // ============================================
// Cleanup
// ============================================

  /**
   * Cleanup resources
   */
  function cleanup(): void {
    if (broadcastChannel.value) {
      broadcastChannel.value.close();
      broadcastChannel.value = null;
    }

    if (db.value) {
      db.value.close();
      db.value = null;
    }
  }

  // ============================================
// Lifecycle
// ============================================

  onMounted(async () => {
    await initDB();
    initBroadcastChannel();
  });

  onUnmounted(() => {
    cleanup();
  });

  // ============================================
// Return Public API
// ============================================

  return {
    // IndexedDB
    saveMessage,
    saveMessages,
    getMessages,
    deleteMessage,
    saveConversation,
    getConversations,
    getConversation,

    // BroadcastChannel
    broadcastActiveConversation,
    broadcastUnreadCount,

    // State
    activeConversationId: readonly(activeConversationId),
    totalUnreadCount: readonly(totalUnreadCount),
    isLoading: readonly(isLoading),

    // Helpers
    db: readonly(db)
  };
}

/**
 * Helper for readonly refs
 */
function readonly<T>(value: T): T {
  return value;
}