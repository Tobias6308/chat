import { defineStore } from 'pinia';
import { ref, computed, shallowRef, triggerRef } from 'vue';
import type { Conversation, Participant } from '@/types/chat';
import { useMessageSync } from '@/composables/useMessageSync';
import { chatApi } from '@/utils/api';

/**
 * ============================================
 * 会话 Store (Pinia)
 * 管理:
 * - 会话列表
 * - 当前会话
 * - 未读数
 * - 置顶/静音状态
 * - IndexedDB 持久化
 * - BroadcastChannel 同步
 * ============================================
 */

export const useConversationStore = defineStore('conversation', () => {
  // 会话数组 - 使用 shallowRef 提升性能
  const conversations = shallowRef<Conversation[]>([]);

  // 当前会话 ID
  const activeConversationId = ref<string | null>(null);

  // 加载状态
  const isLoading = ref(false);

  // 总会话数
  const totalCount = ref(0);

  // 搜索关键词
  const searchQuery = ref('');

  // 消息同步 composable
  const messageSync = useMessageSync();

  // ============================================
  // 计算属性
  // ============================================

  /**
   * 获取当前会话
   */
  const activeConversation = computed(() => {
    if (!activeConversationId.value) return null;
    return conversations.value.find(c => c.id === activeConversationId.value) || null;
  });

  /**
   * 获取未读总数
   */
  const totalUnreadCount = computed(() => {
    return conversations.value.reduce((sum, c) => sum + c.unreadCount, 0);
  });

  /**
   * 获取置顶会话
   */
  const pinnedConversations = computed(() => {
    return conversations.value.filter(c => c.pinned);
  });

  /**
   * 按搜索关键词过滤会话
   */
  const filteredConversations = computed(() => {
    if (!searchQuery.value.trim()) {
      return conversations.value;
    }

    const query = searchQuery.value.toLowerCase();
    return conversations.value.filter(c =>
      c.name.toLowerCase().includes(query)
    );
  });

  /**
   * 根据 ID 获取会话
   */
  function getConversationById(id: string): Conversation | undefined {
    return conversations.value.find(c => c.id === id);
  }

  /**
   * 获取会话的未读数
   */
  function getUnreadCount(conversationId: string): number {
    const conv = conversations.value.find(c => c.id === conversationId);
    return conv?.hasNewMessages ? 1 : 0;
  }

  // ============================================
  // 方法
  // ============================================

  /**
   * 设置当前会话
   * @param conversationId - 会话 ID
   */
  function setActiveConversation(conversationId: string | null): void {
    activeConversationId.value = conversationId;

    // 广播到其他标签页
    messageSync.broadcastActiveConversation(conversationId);

    // 切换到该会话时清除未读
    if (conversationId) {
      clearUnread(conversationId);
    }
  }

  /**
   * 添加或更新会话
   * @param conversation - 要添加/更新的会话
   */
  function upsertConversation(conversation: Conversation): void {
    const index = conversations.value.findIndex(c => c.id === conversation.id);

    if (index !== -1) {
      // 更新现有
      const updated = [...conversations.value];
      updated[index] = { ...updated[index], ...conversation };
      conversations.value = updated;
    } else {
      // 添加新的
      conversations.value = [...conversations.value, conversation];
    }

    // 持久化到 IndexedDB
    messageSync.saveConversation(conversation);

    // 按最后消息时间排序
    sortConversations();

    triggerRef(conversations);
  }

  /**
   * 批量添加或更新会话
   * @param items - 要添加/更新的会话数组
   */
  function upsertConversations(items: Conversation[]): void {
    const existingIds = new Set(conversations.value.map(c => c.id));
    const newConversations: Conversation[] = [];

    for (const item of items) {
      if (existingIds.has(item.id)) {
        const index = conversations.value.findIndex(c => c.id === item.id);
        if (index !== -1) {
          const updated = [...conversations.value];
          updated[index] = { ...updated[index], ...item };
          newConversations.push(updated[index]);
        }
      } else {
        newConversations.push(item);
      }
    }

    if (newConversations.length > 0) {
      conversations.value = [...conversations.value, ...newConversations];
      sortConversations();

      // 持久化所有到 IndexedDB
      newConversations.forEach(c => messageSync.saveConversation(c));

      triggerRef(conversations);
    }
  }

  /**
   * 删除会话
   * @param conversationId - 会话 ID
   */
  function removeConversation(conversationId: string): void {
    conversations.value = conversations.value.filter(c => c.id !== conversationId);

    if (activeConversationId.value === conversationId) {
      activeConversationId.value = null;
    }

    triggerRef(conversations);
  }

  /**
   * 更新会话属性
   * @param conversationId - 会话 ID
   * @param updates - 要更新的属性
   */
  function updateConversation(
    conversationId: string,
    updates: Partial<Conversation>
  ): void {
    const index = conversations.value.findIndex(c => c.id === conversationId);
    if (index === -1) return;

    const updated = [...conversations.value];
    updated[index] = { ...updated[index], ...updates };
    conversations.value = updated;

    // 持久化
    messageSync.saveConversation(updated[index]);

    sortConversations();
    triggerRef(conversations);
  }

  /**
   * 切换置顶状态
   * @param conversationId - 会话 ID
   */
  async function togglePin(conversationId: string): Promise<boolean> {
    const conv = conversations.value.find(c => c.id === conversationId);
    if (!conv) return false;

    try {
      const result = await chatApi.togglePin(conversationId);
      if (result.success) {
        updateConversation(conversationId, { pinned: result.pinned });
        return result.pinned;
      }
    } catch (error) {
      console.error('切换置顶失败:', error);
    }
    return false;
  }

  /**
   * 切换静音状态
   * @param conversationId - 会话 ID
   */
  async function toggleMute(conversationId: string): Promise<boolean> {
    const conv = conversations.value.find(c => c.id === conversationId);
    if (!conv) return false;

    try {
      const result = await chatApi.toggleMute(conversationId);
      if (result.success) {
        updateConversation(conversationId, { muted: result.muted });
        return result.muted;
      }
    } catch (error) {
      console.error('切换静音失败:', error);
    }
    return false;
  }

  /**
   * 增加未读数
   * @param conversationId - 会话 ID
   * @param count - 增量 (默认 1)
   */
  function incrementUnread(conversationId: string, count = 1): void {
    const conv = conversations.value.find(c => c.id === conversationId);
    if (!conv) return;

    updateConversation(conversationId, {
      hasNewMessages: true
    });

    // 广播未读数
    const newTotal = conversations.value.filter(c => c.hasNewMessages).length;
    messageSync.broadcastUnreadCount(newTotal);
  }

  /**
   * 清除会话未读数
   * @param conversationId - 会话 ID
   */
  function clearUnread(conversationId: string): void {
    const conv = conversations.value.find(c => c.id === conversationId);
    if (!conv || !conv.hasNewMessages) return;

    updateConversation(conversationId, { hasNewMessages: false });

    // 广播
    const newTotal = conversations.value.filter(c => c.hasNewMessages).length;
    messageSync.broadcastUnreadCount(newTotal);
  }

  /**
   * 清除所有未读数
   */
  function clearAllUnread(): void {
    conversations.value.forEach(c => {
      if (c.hasNewMessages) {
        updateConversation(c.id, { hasNewMessages: false });
      }
    });

    messageSync.broadcastUnreadCount(0);
  }

  /**
   * 按更新时间排序会话，置顶优先
   */
  function sortConversations(): void {
    conversations.value = [...conversations.value].sort((a, b) => {
      // 置顶优先
      if (a.pinned && !b.pinned) return -1;
      if (!a.pinned && b.pinned) return 1;

      // 然后按更新时间
      const aTime = a.updatedAt || 0;
      const bTime = b.updatedAt || 0;
      return bTime - aTime;
    });
  }

  /**
   * 从 IndexedDB 加载会话
   */
  async function loadFromCache(): Promise<void> {
    isLoading.value = true;

    try {
      const cached = await messageSync.getConversations();
      if (cached.length > 0) {
        conversations.value = cached;
        sortConversations();
        triggerRef(conversations);
      }
    } catch (error) {
      console.error('[ConversationStore] 从缓存加载失败:', error);
    } finally {
      isLoading.value = false;
    }
  }

  /**
   * 从 API 加载会话
   */
  async function loadFromApi(): Promise<void> {
    isLoading.value = true;

    try {
      const response = await chatApi.getConversations() as any;
      
      // API 返回 { conversations: [...] }
      const conversationList = response.conversations || [];
      
      if (conversationList.length > 0) {
        const newConversations: Conversation[] = conversationList.map((c: any) => ({
          id: c.id,
          type: c.type,
          name: c.name || '会话',
          avatar: c.avatar,
          participants: (c.participants || []).map((p: any) => {
            // 兼容字符串 userId 和对象格式
            const userId = typeof p === 'string' ? p : (p.userId || p);
            return {
              userId,
              nickname: typeof p === 'object' && p.nickname ? p.nickname : '用户',
              avatar: typeof p === 'object' ? p.avatar : undefined
            };
          }),
          lastMessage: c.lastMessage ? {
            id: c.lastMessage.id || '',
            content: c.lastMessage.content || '',
            contentType: c.lastMessage.contentType,
            senderId: c.lastMessage.senderId || '',
            createdAt: c.lastMessage.createdAt || 0
          } : undefined,
          unreadCount: c.unreadCount || 0,
          hasNewMessages: c.hasNewMessages || false,
          pinned: c.pinned || false,
          muted: c.muted || false,
          createdAt: c.createdAt || Date.now(),
          updatedAt: c.updatedAt
        }));

        // 用新数据替换所有会话 (避免重复)
        conversations.value = newConversations;

        // 保存总会话数
        totalCount.value = response.totalCount || 0;

        // 保存所有新会话到缓存
        newConversations.forEach(c => messageSync.saveConversation(c));

        sortConversations();
        triggerRef(conversations);
      }
    } catch (error) {
      console.error('[ConversationStore] 从 API 加载失败:', error);
    } finally {
      isLoading.value = false;
    }
  }

  /**
   * 设置搜索关键词
   * @param query - 搜索关键词
   */
  function setSearchQuery(query: string): void {
    searchQuery.value = query;
  }

  /**
   * 根据用户 ID 获取参与者
   * @param conversationId - 会话 ID
   * @param userId - 用户 ID
   */
  function getParticipant(conversationId: string, userId: string): Participant | undefined {
    const conv = conversations.value.find(c => c.id === conversationId);
    return conv?.participants.find(p => p.userId === userId);
  }

  // ============================================
  // 初始化
  // ============================================

  return {
    // 状态
    conversations,
    activeConversationId,
    isLoading,
    totalCount,
    searchQuery,

    // 计算属性
    activeConversation,
    totalUnreadCount,
    pinnedConversations,
    filteredConversations,

    // 获取器
    getConversationById,
    getUnreadCount,

    // 方法
    setActiveConversation,
    upsertConversation,
    upsertConversations,
    removeConversation,
    updateConversation,
    togglePin,
    toggleMute,
    incrementUnread,
    clearUnread,
    clearAllUnread,
    loadFromCache,
    loadFromApi,
    setSearchQuery,
    getParticipant
  };
});