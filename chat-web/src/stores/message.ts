import { defineStore } from 'pinia';
import { ref, shallowRef, triggerRef } from 'vue';
import { nanoid } from 'nanoid';
import type { Message, MessageStatus, CursorPageResult } from '@/types/chat';
import { useMessageSync } from '@/composables/useMessageSync';
import { chatApi } from '@/utils/api';

/**
 * ============================================
 * Message Store (Pinia)
 * Manages:
 * - Message state per conversation
 * - Cursor-based pagination
 * - Optimistic updates
 * - Memory limit (500 messages per conversation)
 * - IndexedDB persistence
 * ============================================
 */

const MAX_MESSAGES_PER_CONVERSATION = 500;

export const useMessageStore = defineStore('message', () => {
  // Message map: conversationId -> messages array
  // Use shallowRef for performance (we don't need deep reactivity)
  const messagesMap = shallowRef<Map<string, Message[]>>(new Map());

  // Cursor map for pagination
  const cursorMap = ref<Map<string, string | undefined>>(new Map());

  // Loading states
  const loadingMap = ref<Map<string, boolean>>(new Map());
  const loadingMoreMap = ref<Map<string, boolean>>(new Map());

  // Has more data map
  const hasMoreMap = ref<Map<string, boolean>>(new Map());

  // Pending message IDs (sent but not confirmed)
  const pendingMessageIds = ref<Set<string>>(new Set());

  // Message sync composable
  const messageSync = useMessageSync();

  // ============================================
  // Computed
  // ============================================

  /**
   * Get messages for a conversation
   * @param conversationId - Conversation ID
   * @returns Messages array
   */
  function getMessages(conversationId: string): Message[] {
    return messagesMap.value.get(conversationId) || [];
  }

  /**
   * Check if loading for a conversation
   * @param conversationId - Conversation ID
   */
  function isLoading(conversationId: string): boolean {
    return loadingMap.value.get(conversationId) || false;
  }

  /**
   * Check if loading more for a conversation
   * @param conversationId - Conversation ID
   */
  function isLoadingMore(conversationId: string): boolean {
    return loadingMoreMap.value.get(conversationId) || false;
  }

  /**
   * Check if has more messages
   * @param conversationId - Conversation ID
   */
  function hasMore(conversationId: string): boolean {
    return hasMoreMap.value.get(conversationId) ?? true;
  }

  // ============================================
  // Actions
  // ============================================

  /**
   * Add message with optimistic update support
   * @param conversationId - Conversation ID
   * @param message - Message to add
   * @param isOptimistic - Whether this is an optimistic update
   */
  function addMessage(conversationId: string, message: Message, isOptimistic = false): void {
    const current = messagesMap.value.get(conversationId) || [];

    // Check for duplicate
    if (current.some(m => m.id === message.id)) {
      return;
    }

    // Add to end (newest at bottom for chat UI)
    const updated = [...current, message];

    // Enforce memory limit
    const trimmed = updated.slice(0, MAX_MESSAGES_PER_CONVERSATION);

    // Update map
    const newMap = new Map(messagesMap.value);
    newMap.set(conversationId, trimmed);
    messagesMap.value = newMap;

    // Persist to IndexedDB (debounced in production)
    if (!isOptimistic) {
      messageSync.saveMessage(message);
    }

    // Trigger update for Vue reactivity
    triggerRef(messagesMap);
  }

  /**
   * Add multiple messages
   * @param conversationId - Conversation ID
   * @param messages - Messages to add
   */
  function addMessages(conversationId: string, messages: Message[]): void {
    const current = messagesMap.value.get(conversationId) || [];
    const currentIds = new Set(current.map(m => m.id));

    // Filter out duplicates
    const newMessages = messages.filter(m => !currentIds.has(m.id));

    if (newMessages.length === 0) return;

    // Merge and sort by createdAt ascending (oldest first, newest at bottom)
    const merged = [...current, ...newMessages].sort((a, b) => a.createdAt - b.createdAt);

    // Enforce memory limit
    const trimmed = merged.slice(0, MAX_MESSAGES_PER_CONVERSATION);

    // Update map
    const newMap = new Map(messagesMap.value);
    newMap.set(conversationId, trimmed);
    messagesMap.value = newMap;

    // Persist to IndexedDB
    messageSync.saveMessages(newMessages);

    triggerRef(messagesMap);
  }

  /**
   * Update message status
   * @param messageId - Message ID
   * @param status - New status
   * @param conversationId - Conversation ID
   */
  function updateMessageStatus(
    messageId: string,
    status: MessageStatus,
    conversationId?: string
  ): void {
    for (const [convId, messages] of messagesMap.value) {
      if (conversationId && convId !== conversationId) continue;

      const index = messages.findIndex(m => m.id === messageId);
      if (index !== -1) {
        const newMap = new Map(messagesMap.value);
        const updatedMessages = [...messages];
        updatedMessages[index] = { ...updatedMessages[index], status };
        newMap.set(convId, updatedMessages);
        messagesMap.value = newMap;
        triggerRef(messagesMap);

        // Remove from pending if confirmed
        if (status === 'sent' || status === 'delivered' || status === 'read') {
          pendingMessageIds.value.delete(messageId);
        }

        break;
      }
    }
  }

  /**
   * Remove message
   * @param messageId - Message ID
   * @param conversationId - Conversation ID
   */
  function removeMessage(messageId: string, conversationId: string): void {
    const messages = messagesMap.value.get(conversationId);
    if (!messages) return;

    const newMap = new Map(messagesMap.value);
    newMap.set(
      conversationId,
      messages.filter(m => m.id !== messageId)
    );
    messagesMap.value = newMap;

    messageSync.deleteMessage(messageId);
    triggerRef(messagesMap);
  }

  /**
   * Create optimistic message
   * @param conversationId - Conversation ID
   * @param content - Message content
   * @param contentType - Content type
   * @returns Created message with temp ID
   */
  function createOptimisticMessage(
    conversationId: string,
    content: string,
    contentType: 'text' | 'image' | 'file' = 'text'
  ): Message {
    const currentUserId = sessionStorage.getItem('chat_userId') || 'current_user';
    const message: Message = {
      id: `temp_${nanoid()}`,
      conversationId,
      senderId: currentUserId,
      content,
      contentType,
      status: 'sending',
      createdAt: Date.now()
    };

    addMessage(conversationId, message, true);
    pendingMessageIds.value.add(message.id);

    return message;
  }

  /**
   * Replace optimistic message with real one
   * @param tempId - Temporary ID
   * @param realMessage - Real message from server
   * @param conversationId - Conversation ID
   */
  function replaceOptimisticMessage(
    tempId: string,
    realMessage: Message,
    conversationId: string
  ): void {
    const messages = messagesMap.value.get(conversationId);
    if (!messages) return;

    const index = messages.findIndex(m => m.id === tempId);
    if (index !== -1) {
      const newMap = new Map(messagesMap.value);
      const updatedMessages = [...messages];
      updatedMessages[index] = { ...realMessage, status: 'sent' };
      newMap.set(conversationId, updatedMessages);
      messagesMap.value = newMap;
      pendingMessageIds.value.delete(tempId);
      triggerRef(messagesMap);
    }
  }

  /**
   * Fetch history for a conversation
   * @param conversationId - Conversation ID
   * @param cursor - Cursor for pagination
   * @param limit - Number of messages to fetch
   */
  async function fetchHistory(
    conversationId: string,
    cursor?: string,
    limit = 20
  ): Promise<CursorPageResult<Message>> {
    // Check loading state
    if (isLoading(conversationId)) {
      return { items: [], hasMore: false };
    }

    const isInitial = !cursor;
    loadingMap.value.set(conversationId, isInitial);
    loadingMoreMap.value.set(conversationId, !isInitial);

    try {
      // Fetch from server via HTTP API
      const cursorNum = cursor ? parseInt(cursor) : undefined;
      const data = await chatApi.getMessages(conversationId, cursorNum, limit);
      
      const items: Message[] = (data.messages || []).map((m: any) => ({
        id: m.id,
        conversationId: m.conversationId,
        senderId: m.senderId,
        content: m.content,
        contentType: (m.contentType || 'text') as any,
        status: (m.status || 'sent') as any,
        createdAt: m.createdAt,
        updatedAt: m.updatedAt,
        metadata: m.metadata,
        replyTo: m.replyTo,
        reactions: m.reactions
      }));

      // Check if there are more messages
      const hasMore = data.hasMore !== false && items.length >= limit;

      // Update state
      if (items.length > 0) {
        if (isInitial) {
          addMessages(conversationId, items);
        } else {
          // Append to existing with deduplication
          const current = messagesMap.value.get(conversationId) || [];
          const currentIds = new Set(current.map(m => m.id));
          const newItems = items.filter(m => !currentIds.has(m.id));
          
          if (newItems.length > 0) {
            const merged = [...current, ...newItems]
              .sort((a, b) => a.createdAt - b.createdAt)
              .slice(0, MAX_MESSAGES_PER_CONVERSATION);

            messagesMap.value.set(conversationId, merged);
          }
        }
      }

      // Update has more state
      hasMoreMap.value.set(conversationId, hasMore);

      // Return cursor for next fetch
      const nextCursor = items.length > 0 
        ? String(items[items.length - 1].createdAt) 
        : undefined;

      return { items, hasMore, cursor: nextCursor };
    } catch (error) {
      console.error('[MessageStore] Failed to fetch history:', error);
      return { items: [], hasMore: false, cursor: undefined };
    } finally {
      loadingMap.value.set(conversationId, false);
      loadingMoreMap.value.set(conversationId, false);
    }
  }

  /**
   * Clear messages for a conversation
   * @param conversationId - Conversation ID
   */
  function clearMessages(conversationId: string): void {
    const newMap = new Map(messagesMap.value);
    newMap.delete(conversationId);
    messagesMap.value = newMap;
    cursorMap.value.delete(conversationId);
    hasMoreMap.value.delete(conversationId);
    triggerRef(messagesMap);
  }

  /**
   * Mark messages as read
   * @param conversationId - Conversation ID
   * @param messageIds - Message IDs to mark as read
   */
  function markAsRead(conversationId: string, messageIds: string[]): void {
    const messages = messagesMap.value.get(conversationId);
    if (!messages) return;

    const newMap = new Map(messagesMap.value);
    const updatedMessages = messages.map(m =>
      messageIds.includes(m.id) && m.status !== 'read'
        ? { ...m, status: 'read' as MessageStatus }
        : m
    );
    newMap.set(conversationId, updatedMessages);
    messagesMap.value = newMap;
    triggerRef(messagesMap);
  }

  /**
   * Retry failed message
   * @param messageId - Message ID
   * @param conversationId - Conversation ID
   */
  async function retryMessage(messageId: string, conversationId: string): Promise<void> {
    const messages = messagesMap.value.get(conversationId);
    if (!messages) return;

    const message = messages.find(m => m.id === messageId);
    if (!message || message.status !== 'failed') return;

    // Update status to sending
    updateMessageStatus(messageId, 'sending', conversationId);

    // In real implementation, this would call WebSocket send
    // For now, we'll simulate a retry
    setTimeout(() => {
      updateMessageStatus(messageId, 'sent', conversationId);
    }, 1000);
  }

  // ============================================
  // Getters
  // ============================================

  return {
    // State
    messagesMap,

    // Computed
    getMessages,
    isLoading,
    isLoadingMore,
    hasMore,

    // Actions
    addMessage,
    addMessages,
    updateMessageStatus,
    removeMessage,
    createOptimisticMessage,
    replaceOptimisticMessage,
    fetchHistory,
    clearMessages,
    markAsRead,
    retryMessage,

    // Pending
    pendingMessageIds
  };
});