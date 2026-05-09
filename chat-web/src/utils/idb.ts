import { openDB, type IDBPDatabase, type DBSchema } from 'idb';
import type { StoredMessage, StoredConversation } from '@/types/chat';

/**
 * ============================================
 * IndexedDB Utility Functions
 * Provides helper functions for IndexedDB operations
 * ============================================
 */

// Database configuration
const DB_NAME = 'chat-web-db';
const DB_VERSION = 1;

/**
 * Chat database schema
 */
interface ChatDBSchema extends DBSchema {
  messages: {
    key: string;
    value: StoredMessage;
    indexes: {
      'by-conversation': string;
      'by-created': number;
      'by-stored': number;
    };
  };
  conversations: {
    key: string;
    value: StoredConversation;
    indexes: {
      'by-updated': number;
    };
  };
  pending_messages: {
    key: string;
    value: StoredMessage & { retryAt?: number };
    indexes: {
      'by-created': number;
    };
  };
}

/**
 * Database instance cache
 */
let dbInstance: IDBPDatabase<ChatDBSchema> | null = null;

/**
 * Get or create database instance
 * @returns Promise<IDBPDatabase>
 */
export async function getDatabase(): Promise<IDBPDatabase<ChatDBSchema>> {
  if (dbInstance) {
    return dbInstance;
  }

  dbInstance = await openDB<ChatDBSchema>(DB_NAME, DB_VERSION, {
    upgrade(database) {
      // Messages store
      if (!database.objectStoreNames.contains('messages')) {
        const messageStore = database.createObjectStore('messages', { keyPath: 'id' });
        messageStore.createIndex('by-conversation', 'conversationId');
        messageStore.createIndex('by-created', 'createdAt');
        messageStore.createIndex('by-stored', 'storedAt');
      }

      // Conversations store
      if (!database.objectStoreNames.contains('conversations')) {
        const convStore = database.createObjectStore('conversations', { keyPath: 'id' });
        convStore.createIndex('by-updated', 'updatedAt');
      }

      // Pending messages store
      if (!database.objectStoreNames.contains('pending_messages')) {
        const pendingStore = database.createObjectStore('pending_messages', { keyPath: 'id' });
        pendingStore.createIndex('by-created', 'createdAt');
      }
    }
  });

  return dbInstance;
}

/**
 * Close database connection
 */
export async function closeDatabase(): Promise<void> {
  if (dbInstance) {
    dbInstance.close();
    dbInstance = null;
  }
}

/**
 * Clear all data from database
 */
export async function clearDatabase(): Promise<void> {
  const db = await getDatabase();

  const tx = db.transaction(['messages', 'conversations', 'pending_messages'], 'readwrite');

  await tx.objectStore('messages').clear();
  await tx.objectStore('conversations').clear();
  await tx.objectStore('pending_messages').clear();

  await tx.done;
}

/**
 * Delete entire database
 */
export async function deleteDatabase(): Promise<void> {
  await closeDatabase();
  await indexedDB.deleteDatabase(DB_NAME);
}

// ============================================
// Message Operations
// ============================================

/**
 * Save message to database
 * @param message - Message to save
 */
export async function saveMessageToDB(message: StoredMessage): Promise<void> {
  const db = await getDatabase();
  await db.put('messages', message);
}

/**
 * Save multiple messages
 * @param messages - Messages to save
 */
export async function saveMessagesToDB(messages: StoredMessage[]): Promise<void> {
  const db = await getDatabase();
  const tx = db.transaction('messages', 'readwrite');
  const store = tx.objectStore('messages');

  await Promise.all(messages.map(msg => store.put(msg)));
  await tx.done;
}

/**
 * Get messages by conversation ID
 * @param conversationId - Conversation ID
 * @param limit - Max number of messages
 * @param cursor - Cursor (timestamp) for pagination
 * @returns Array of messages
 */
export async function getMessagesByConversation(
  conversationId: string,
  limit = 50,
  cursor?: number
): Promise<StoredMessage[]> {
  const db = await getDatabase();
  const tx = db.transaction('messages', 'readonly');
  const store = tx.objectStore('messages');
  const index = store.index('by-conversation');

  let range: IDBKeyRange;
  if (cursor) {
    range = IDBKeyRange.bound([conversationId, cursor], [conversationId, 0], true, true);
  } else {
    range = IDBKeyRange.only(conversationId);
  }

  const messages: StoredMessage[] = [];
  let count = 0;

  for await (const cursor of index.iterate(range, 'prev')) {
    if (count < limit) {
      messages.push(cursor.primaryKey as unknown as StoredMessage);
    }
    count++;
    if (count >= limit + 1) break;
  }

  return messages.slice(0, limit);
}

/**
 * Delete message by ID
 * @param messageId - Message ID
 */
export async function deleteMessageFromDB(messageId: string): Promise<void> {
  const db = await getDatabase();
  await db.delete('messages', messageId);
}

/**
 * Get all messages count
 */
export async function getMessagesCount(): Promise<number> {
  const db = await getDatabase();
  return db.count('messages');
}

// ============================================
// Conversation Operations
// ============================================

/**
 * Save conversation
 * @param conversation - Conversation to save
 */
export async function saveConversationToDB(conversation: StoredConversation): Promise<void> {
  const db = await getDatabase();
  await db.put('conversations', conversation);
}

/**
 * Get all conversations
 */
export async function getAllConversations(): Promise<StoredConversation[]> {
  const db = await getDatabase();
  const conversations = await db.getAll('conversations');
return conversations.sort((a, b) =>
    (b.updatedAt || 0) - (a.updatedAt || 0)
  );
}

/**
 * Get conversation by ID
 * @param conversationId - Conversation ID
 */
export async function getConversationById(conversationId: string): Promise<StoredConversation | undefined> {
  const db = await getDatabase();
  return db.get('conversations', conversationId);
}

/**
 * Delete conversation
 * @param conversationId - Conversation ID
 */
export async function deleteConversationFromDB(conversationId: string): Promise<void> {
  const db = await getDatabase();
  await db.delete('conversations', conversationId);
}

// ============================================
// Pending Message Operations (Offline Queue)
// ============================================

/**
 * Add pending message
 * @param message - Message to queue
 */
export async function addPendingMessage(message: StoredMessage): Promise<void> {
  const db = await getDatabase();
  await db.put('pending_messages', message);
}

/**
 * Get all pending messages
 */
export async function getPendingMessages(): Promise<Array<StoredMessage & { retryAt?: number }>> {
  const db = await getDatabase();
  const messages = await db.getAll('pending_messages');
  return messages.sort((a, b) => a.createdAt - b.createdAt);
}

/**
 * Remove pending message
 * @param messageId - Message ID
 */
export async function removePendingMessage(messageId: string): Promise<void> {
  const db = await getDatabase();
  await db.delete('pending_messages', messageId);
}

/**
 * Get pending messages count
 */
export async function getPendingMessagesCount(): Promise<number> {
  const db = await getDatabase();
  return db.count('pending_messages');
}

// ============================================
// Storage Info
// ============================================

/**
 * Get storage usage estimate
 * Note: This is an approximation
 */
export async function getStorageInfo(): Promise<{ messages: number; conversations: number; pending: number }> {
  const db = await getDatabase();

  return {
    messages: await db.count('messages'),
    conversations: await db.count('conversations'),
    pending: await db.count('pending_messages')
  };
}

/**
 * Estimate storage size in bytes
 * This is a rough estimate based on message count
 */
export async function estimateStorageSize(): Promise<number> {
  const info = await getStorageInfo();
  // Rough estimate: 1KB per message, 500 bytes per conversation
  return info.messages * 1024 + info.conversations * 500;
}