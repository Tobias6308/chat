<script setup lang="ts">
import { ref, computed, onMounted, watch } from 'vue';
import { useRouter, useRoute } from 'vue-router';
import { useFriendStore } from '@/stores/friend';
import { useGroupStore } from '@/stores/group';
import { useConversationStore } from '@/stores/conversation';
import { useMessageStore } from '@/stores/message';
import { userApi, chatApi } from '@/utils/api';
import VirtualMessageList from './VirtualMessageList.vue';
import ChatInput from './ChatInput.vue';

const router = useRouter();
const route = useRoute();
const friendStore = useFriendStore();
const groupStore = useGroupStore();

interface Props {
  wsStatus?: string;
}

const props = withDefaults(defineProps<Props>(), {
  wsStatus: 'disconnected'
});

const emit = defineEmits<{
  (e: 'selectConversation', conversationId: string): void;
  (e: 'sendMessage', content: string, conversationId: string, contentType: string): void;
  (e: 'retryMessage', messageId: string): void;
  (e: 'loadMore', conversationId: string): void;
  (e: 'clickUser', userId: string): void;
}>();

// Store
const conversationStore = useConversationStore();
const messageStore = useMessageStore();

// 用户信息
const userInfo = ref<{ userId: string; nickname: string; username: string; avatar: string } | null>(null);

// 用户信息弹窗
const showUserInfoModal = ref(false);
const selectedUserInfo = ref<{
  userId: string;
  nickname: string;
  avatar: string;
  username: string;
} | null>(null);

// 用户信息 - 每次进入页面都刷新
async function ensureUserInfo(): Promise<void> {
  try {
    const info = await userApi.getInfo();
    userInfo.value = {
      userId: info.userId,
      nickname: info.nickname,
      username: info.username,
      avatar: info.avatar
    };
  } catch (e) {
    console.error('Failed to load user info:', e);
  }
}

// 会话列表加载标志
let conversationListLoaded = false;

// 会话列表加载状态
const conversationLoading = computed(() => conversationStore.isLoading);

onMounted(async () => {
  // 加载用户信息 (带懒加载检查)
  await ensureUserInfo();

  // 仅首次访问时加载会话
  if (!conversationListLoaded) {
    conversationStore.loadFromApi();
    conversationListLoaded = true;
  }
});

// 路由变化时刷新用户信息
watch(() => route.fullPath, () => {
  ensureUserInfo();
});

// UI 状态
const showConversationList = ref(true);
const searchQuery = ref('');

// 连接状态文字
const connectionStatusText = computed(() => {
  switch (props.wsStatus) {
    case 'connected':
      return '已连接';
    case 'connecting':
      return '连接中...';
    case 'reconnecting':
      return '重连中...';
    case 'disconnected':
      return '未连接';
    default:
      return '未知';
  }
});

const connectionStatusColor = computed(() => {
  switch (props.wsStatus) {
    case 'connected':
      return 'bg-green-500';
    case 'connecting':
    case 'reconnecting':
      return 'bg-yellow-500';
    case 'disconnected':
      return 'bg-red-500';
    default:
      return 'bg-gray-500';
  }
});

// 过滤后的会话 (最新10个)
const filteredConversations = computed(() => {
  let list = [...conversationStore.conversations];
  
  // 如果有搜索关键词，先过滤
  if (searchQuery.value.trim()) {
    const query = searchQuery.value.toLowerCase();
    list = list.filter(c => c.name.toLowerCase().includes(query));
  }
  
  // 限制为最近10个 (置顶优先)
  const pinned = list.filter(c => c.pinned);
  const unpinned = list.filter(c => !c.pinned);
  
  // 按更新时间排序未置顶的
  unpinned.sort((a, b) => {
    const aTime = a.updatedAt || 0;
    const bTime = b.updatedAt || 0;
    return bTime - aTime;
  });
  
  return [...pinned, ...unpinned].slice(0, 10);
});

// 当前消息
const activeMessages = computed(() => {
  if (!conversationStore.activeConversationId) return [];
  return messageStore.getMessages(conversationStore.activeConversationId);
});

// 是否正在加载消息
const isLoadingMessages = computed(() => {
  if (!conversationStore.activeConversationId) return false;
  return messageStore.isLoading(conversationStore.activeConversationId);
});

// 是否正在加载更多
const isLoadingMore = computed(() => {
  if (!conversationStore.activeConversationId) return false;
  return messageStore.isLoadingMore(conversationStore.activeConversationId);
});

// 是否还有更多
const hasMoreMessages = computed(() => {
  if (!conversationStore.activeConversationId) return false;
  return messageStore.hasMore(conversationStore.activeConversationId);
});

// 当前会话的参与者
const activeParticipants = computed(() => {
  if (!conversationStore.activeConversationId) return [];
  const conv = conversationStore.getConversationById(conversationStore.activeConversationId);
  return conv?.participants || [];
});

// 获取私聊好友信息
function getFriendInfo(conversation: any) {
  // 群聊使用会话的名称和头像
  if (conversation.type === 'group') {
    return { name: conversation.name, avatar: conversation.avatar };
  }
  
  // 私聊获取对方的 info
  const currentUserId = userInfo.value?.userId || '';
  const participants = conversation.participants || [];
  
  // Find the other participant (not current user)
  const otherParticipant = participants.find((p: any) => p.userId !== currentUserId);
  
  if (otherParticipant) {
    return { 
      name: otherParticipant.nickname || otherParticipant.userId || '用户', 
      avatar: otherParticipant.avatar || '' 
    };
  }
  
  // Fallback to conversation name
  return { name: conversation.name, avatar: conversation.avatar };
}

// Handle conversation select
async function handleSelectConversation(conversationId: string): Promise<void> {
  conversationStore.setActiveConversation(conversationId);
  emit('selectConversation', conversationId);
  
  // 标记会话已读
  try {
    await chatApi.markAsRead(conversationId);
    conversationStore.updateConversation(conversationId, { hasNewMessages: false });
  } catch (e) {
    console.error('Failed to mark as read:', e);
  }
}

// Handle send message
function handleSendMessage(content: string, conversationId: string, contentType: string = 'text'): void {
  emit('sendMessage', content, conversationId, contentType);
}

// 切换置顶
async function handleTogglePin(conversationId: string, event: Event): Promise<void> {
  event.stopPropagation();
  await conversationStore.togglePin(conversationId);
}

// 切换静音
async function handleToggleMute(conversationId: string, event: Event): Promise<void> {
  event.stopPropagation();
  await conversationStore.toggleMute(conversationId);
}

// Handle retry
function handleRetryMessage(messageId: string): void {
  emit('retryMessage', messageId);
}

// Handle click user avatar
async function handleClickUser(userId: string): Promise<void> {
  if (!userId || userId === 'current_user') return;
  
  // Try to get user info from participants first
  const conv = conversationStore.activeConversationId 
    ? conversationStore.getConversationById(conversationStore.activeConversationId)
    : null;
  
  if (conv) {
    const participant = conv.participants?.find(p => p.userId === userId);
    if (participant) {
      selectedUserInfo.value = {
        userId: participant.userId,
        nickname: participant.nickname || '用户',
        avatar: participant.avatar || '',
        username: participant.userId
      };
      showUserInfoModal.value = true;
      return;
    }
  }
  
  // Try to fetch from API
  try {
    const userData = await userApi.getById(userId);
    selectedUserInfo.value = {
      userId: userData.userId,
      nickname: userData.nickname || '用户',
      avatar: userData.avatar || '',
      username: userData.userId
    };
    showUserInfoModal.value = true;
  } catch (e) {
    console.error('Failed to get user info:', e);
  }
}

// Handle load more
function handleLoadMore(): void {
  if (conversationStore.activeConversationId) {
    emit('loadMore', conversationStore.activeConversationId);
  }
}

// Toggle conversation list (for mobile)
function toggleConversationList(): void {
  showConversationList.value = !showConversationList.value;
}

// Format time
function formatTime(timestamp?: number): string {
  if (!timestamp) return '';
  const date = new Date(timestamp);
  const now = new Date();
  const diff = now.getTime() - date.getTime();
  const days = Math.floor(diff / (1000 * 60 * 60 * 24));

  if (days === 0) {
    return date.toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' });
  } else if (days === 1) {
    return '昨天';
  } else if (days < 7) {
    return `${days}天前`;
  } else {
    return date.toLocaleDateString('zh-CN', { month: 'short', day: 'numeric' });
  }
}

// Format last message preview
function formatLastMessage(conversation: any): string {
  if (!conversation.lastMessage) return '暂无消息';
  
  const contentType = conversation.lastMessage.contentType;
  const content = conversation.lastMessage.content || '';
  
  switch (contentType) {
    case 'image':
      return '[图片]';
    case 'audio':
      return '[语音]';
    case 'video':
      return '[视频]';
    case 'file':
      return '[文件]';
    default:
      return content || '暂无消息';
  }
}

// Go to group manage
function goToGroupManage(): void {
  const conv = conversationStore.activeConversation;
  if (conv && conv.type === 'group') {
    router.push(`/group-manage/${conv.id}`);
  }
}
</script>

<template>
  <div class="flex h-screen bg-gray-100">
    <!-- Conversation List (Left Sidebar) -->
    <aside
      class="w-80 bg-white border-r border-gray-200 flex flex-col"
      :class="{
        'hidden md:flex': !showConversationList
      }"
    >
      <!-- Header -->
      <div class="flex items-center justify-between px-4 py-3 border-b border-gray-200 bg-white">
        <!-- Left: Title -->
        <div class="flex items-center">
          <h1 class="text-lg font-semibold text-gray-800">消息</h1>
        </div>
        
        <!-- Right: Actions -->
        <div class="flex items-center gap-1">
          <!-- Connection status (dot only) -->
          <div class="flex items-center" :title="connectionStatusText">
            <span
              class="w-2 h-2 rounded-full"
              :class="connectionStatusColor"
            ></span>
          </div>

          <!-- Friends button -->
          <button
            class="relative p-1.5 hover:bg-gray-100 rounded-lg"
            title="好友管理"
            @click="router.push('/friends')"
          >
            <svg class="w-4 h-4 text-gray-600" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M17 20h5v-2a3 3 0 00-5.356-1.857M17 20H7m10 0v-2c0-.656-.126-1.283-.356-1.857M7 20H2v-2a3 3 0 015.356-1.857M7 20v-2c0-.656.126-1.283.356-1.857m0 0a5.002 5.002 0 019.288 0M15 7a3 3 0 11-6 0 3 3 0 016 0zm6 3a4 4 0 11-8 0 4 4 0 018 0zM17 20a4 4 0 01-8 0"></path>
            </svg>
            <!-- Badge for pending requests -->
            <span
              v-if="friendStore.pendingRequestsCount > 0"
              class="absolute -top-1 -right-1 min-w-[18px] h-[18px] px-1 bg-red-500 text-white text-xs rounded-full flex items-center justify-center"
            >
              {{ friendStore.pendingRequestsCount }}
            </span>
          </button>

          <!-- Groups button -->
          <button
            class="relative p-1.5 hover:bg-gray-100 rounded-lg"
            title="群聊管理"
            @click="router.push('/groups')"
          >
            <svg class="w-4 h-4 text-gray-600" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M19 21V5a2 2 0 00-2-2H7a2 2 0 00-2 2v16m14 0h2m-2 0h-5m-9 0H3m2 0h5M9 7h1m-1 4h1m4-4h1m-1 4h1m-5 10v-5a1 1 0 011-1h2a1 1 0 011 1v5m-4 0h4"></path>
            </svg>
            <!-- Badge for pending join requests -->
            <span
              v-if="groupStore.pendingJoinRequestsCount > 0"
              class="absolute -top-1 -right-1 min-w-[18px] h-[18px] px-1 bg-red-500 text-white text-xs rounded-full flex items-center justify-center"
            >
              {{ groupStore.pendingJoinRequestsCount }}
            </span>
          </button>

          <!-- Profile button -->
          <button
            class="flex items-center gap-1 p-1.5 hover:bg-gray-100 rounded-lg"
            title="个人设置"
            @click="router.push('/profile')"
          >
            <img
              v-if="userInfo?.avatar"
              :src="userInfo.avatar"
              class="w-5 h-5 rounded-full"
              :alt="userInfo.nickname"
            />
            <div
              v-else
              class="w-5 h-5 rounded-full bg-gradient-to-br from-primary-400 to-primary-600 flex items-center justify-center text-white text-xs font-medium"
            >
              {{ (userInfo?.nickname || userInfo?.username || '?').charAt(0).toUpperCase() }}
            </div>
            <span class="text-xs text-gray-700">{{ userInfo?.nickname || userInfo?.username }}</span>
          </button>
        </div>
      </div>

      <!-- Search -->
      <div class="px-3 py-2 flex items-center gap-2">
        <div class="relative flex-1">
          <input
            v-model="searchQuery"
            type="text"
            placeholder="搜索会话..."
            class="w-full px-3 py-2 pl-9 bg-gray-100 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-primary-500"
          >
          <svg
            class="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-gray-400"
            fill="none"
            stroke="currentColor"
            viewBox="0 0 24 24"
          >
            <path
              stroke-linecap="round"
              stroke-linejoin="round"
              stroke-width="2"
              d="M21 21l-6-6m2-5a7 7 0 11-14 0 7 7 0 0114 0z"
            ></path>
          </svg>
        </div>
        <div class="flex items-center gap-2">
          <!-- Session count -->
          <span v-if="conversationStore.totalCount > 0" class="text-xs text-gray-400">
            {{ conversationStore.conversations.length }}/{{ conversationStore.totalCount }}
          </span>
          <!-- Refresh button -->
          <button
            class="p-1.5 text-gray-500 hover:text-gray-700 hover:bg-gray-100 rounded-lg"
            title="刷新会话"
            :disabled="conversationLoading"
            @click="conversationStore.loadFromApi()"
          >
            <svg
              class="w-4 h-4"
              :class="{ 'animate-spin text-primary-500': conversationLoading, 'text-gray-500': !conversationLoading }"
              fill="none"
              stroke="currentColor"
              viewBox="0 0 24 24"
            >
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M4 4v5h.582m15.356 2A8.001 8.001 0 004.582 9m0 0H9m11 11v-5h-.581m0 0a8.003 8.003 0 01-15.357-2m15.357 2H15"></path>
            </svg>
          </button>
        </div>
      </div>

      <!-- Conversation List -->
      <div class="flex-1 overflow-y-auto">
        <!-- Loading state -->
        <div
          v-if="conversationLoading && filteredConversations.length === 0"
          class="flex flex-col items-center justify-center py-12 text-gray-400"
        >
          <svg class="animate-spin h-8 w-8 mb-3" fill="none" viewBox="0 0 24 24">
            <circle class="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" stroke-width="4"></circle>
            <path class="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4z"></path>
          </svg>
          <span class="text-sm">加载中...</span>
        </div>

        <!-- Empty state -->
        <div
          v-else-if="!conversationLoading && filteredConversations.length === 0"
          class="flex flex-col items-center justify-center py-12 text-gray-400"
        >
          <svg class="w-12 h-12 mb-3" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="1.5" d="M8 12h.01M12 12h.01M16 12h.01M21 12c0 4.418-4.03 8-9 8a9.863 9.863 0 01-4.255-.949L3 20l1.395-3.72C3.512 15.042 3 13.574 3 12c0-4.418 4.03-8 9-8s9 3.582 9 8z"></path>
          </svg>
          <span class="text-sm">暂无会话</span>
        </div>

        <!-- Conversation items -->
        <template v-else>
          <div
            v-for="conversation in filteredConversations"
            :key="conversation.id"
            class="flex items-center px-3 py-3 cursor-pointer hover:bg-gray-50 transition-colors"
            :class="{
              'bg-gray-100': conversationStore.activeConversationId === conversation.id,
              'bg-yellow-50': conversation.pinned
            }"
            @click="handleSelectConversation(conversation.id)"
          >
          <!-- Avatar -->
          <img
            v-if="getFriendInfo(conversation).avatar"
            :src="getFriendInfo(conversation).avatar"
            class="w-12 h-12 rounded-full flex-shrink-0 object-cover"
            :alt="getFriendInfo(conversation).name"
          />
          <div
            v-else
            class="flex-shrink-0 w-12 h-12 rounded-full flex items-center justify-center text-white font-medium"
            :class="conversation.type === 'group' ? 'bg-gradient-to-br from-green-400 to-green-600' : 'bg-gradient-to-br from-primary-400 to-primary-600'"
          >
            {{ getFriendInfo(conversation).name.charAt(0).toUpperCase() }}
          </div>

          <!-- Content -->
          <div class="flex-1 ml-3 min-w-0">
            <div class="flex items-center justify-between">
              <div class="flex items-center gap-1">
                <!-- 置顶图标 -->
                <button
                  v-if="conversation.pinned"
                  class="w-4 h-4 text-yellow-500 flex-shrink-0 hover:text-yellow-600 transition-colors"
                  @click="handleTogglePin(conversation.id, $event)"
                  title="取消置顶"
                >
                  <svg class="w-4 h-4" fill="currentColor" viewBox="0 0 24 24">
                    <path d="M16 12V4h1V2H7v2h1v8l-2 2v2h5v6l1 1 1-1v-6h5v-2l-2-2z"/>
                  </svg>
                </button>
                <button
                  v-else
                  class="w-4 h-4 text-gray-300 flex-shrink-0 hover:text-yellow-500 transition-colors"
                  @click="handleTogglePin(conversation.id, $event)"
                  title="置顶会话"
                >
                  <svg class="w-4 h-4" fill="currentColor" viewBox="0 0 24 24">
                    <path d="M16 12V4h1V2H7v2h1v8l-2 2v2h5v6l1 1 1-1v-6h5v-2l-2-2z"/>
                  </svg>
                </button>
                <button
                  v-if="conversation.muted"
                  class="w-4 h-4 text-red-500 flex-shrink-0 hover:text-red-600 transition-colors ml-1"
                  @click="handleToggleMute(conversation.id, $event)"
                  title="取消静音"
                >
                  <svg class="w-4 h-4" fill="currentColor" viewBox="0 0 24 24">
                    <path d="M12 22c1.1 0 2-.9 2-2h-4c0 1.1.9 2 2 2zm6-6v-5c0-3.07-1.63-5.64-4.5-6.32V4c0-.83-.67-1.5-1.5-1.5s-1.5.67-1.5 1.5v.68C7.64 5.36 6 7.92 6 11v5l-2 2v1h16v-1l-2-2z"/>
                  </svg>
                </button>
                <button
                  v-else
                  class="w-4 h-4 text-gray-300 flex-shrink-0 hover:text-red-500 transition-colors ml-1"
                  @click="handleToggleMute(conversation.id, $event)"
                  title="静音会话"
                >
                  <svg class="w-4 h-4" fill="currentColor" viewBox="0 0 24 24">
                    <path d="M12 22c1.1 0 2-.9 2-2h-4c0 1.1.9 2 2 2zm6-6v-5c0-3.07-1.63-5.64-4.5-6.32V4c0-.83-.67-1.5-1.5-1.5s-1.5.67-1.5 1.5v.68C7.64 5.36 6 7.92 6 11v5l-2 2v1h16v-1l-2-2zm-3.5-6.2l-2.8 3.4h2.8V11h1.7v2.2h2.8l-2.8 3.4V19H7v-2.8l5.5-6.6z"/>
                  </svg>
                </button>
                <span class="text-sm font-medium text-gray-800 truncate">
                  {{ getFriendInfo(conversation).name }}
                </span>
              </div>
              <span class="text-xs text-gray-400">
                {{ formatTime(conversation.updatedAt) }}
              </span>
            </div>
            <div class="flex items-center justify-between mt-1">
              <span class="text-xs text-gray-500 truncate">
                {{ formatLastMessage(conversation) }}
              </span>
              <span
                v-if="conversation.unreadCount > 0"
                class="flex-shrink-0 min-w-[18px] h-[18px] px-1 bg-primary-500 text-white text-xs rounded-full flex items-center justify-center"
              >
                {{ conversation.unreadCount > 99 ? '99+' : conversation.unreadCount }}
              </span>
              <span
                v-else-if="conversation.hasNewMessages"
                class="flex-shrink-0 w-2 h-2 bg-red-500 rounded-full"
              ></span>
            </div>
          </div>
        </div>
        </template>
      </div>
    </aside>

    <!-- Chat Area (Right) -->
    <main class="flex-1 flex flex-col min-w-0">
      <!-- Mobile toggle button -->
      <div class="md:hidden flex items-center px-3 py-2 bg-white border-b border-gray-200">
        <button
          class="flex items-center gap-2 text-gray-600 hover:text-gray-800"
          @click="toggleConversationList"
        >
          <svg class="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M4 6h16M4 12h16M4 18h16"></path>
          </svg>
          <span>会话列表</span>
        </button>
        <button
          class="p-1.5 text-gray-500 hover:text-gray-700 hover:bg-gray-100 rounded-lg"
          title="刷新会话"
          @click="conversationStore.loadFromApi()"
        >
          <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M4 4v5h.582m15.356 2A8.001 8.001 0 004.582 9m0 0H9m11 11v-5h-.581m0 0a8.003 8.003 0 01-15.357-2m15.357 2H15"></path>
          </svg>
        </button>
      </div>

      <!-- Chat Header -->
      <header
        v-if="conversationStore.activeConversation"
        class="flex items-center justify-between px-4 py-3 bg-white border-b border-gray-200"
      >
        <div class="flex items-center gap-3">
          <img
            v-if="getFriendInfo(conversationStore.activeConversation).avatar"
            :src="getFriendInfo(conversationStore.activeConversation).avatar"
            class="w-10 h-10 rounded-full object-cover flex-shrink-0"
            :alt="getFriendInfo(conversationStore.activeConversation).name"
          />
          <div
            v-else
            class="w-10 h-10 rounded-full flex items-center justify-center text-white font-medium flex-shrink-0"
            :class="conversationStore.activeConversation.type === 'group' ? 'bg-gradient-to-br from-green-400 to-green-600' : 'bg-gradient-to-br from-primary-400 to-primary-600'"
          >
            {{ getFriendInfo(conversationStore.activeConversation).name.charAt(0).toUpperCase() }}
          </div>
          <div>
            <h2 class="text-base font-semibold text-gray-800">
              {{ getFriendInfo(conversationStore.activeConversation).name }}
            </h2>
            <p class="text-xs text-gray-500">
              {{ conversationStore.activeConversation.type === 'group' ? conversationStore.activeConversation.participants.length + ' 位成员' : '私聊' }}
            </p>
          </div>
        </div>

        <div class="flex items-center gap-2">
          <!-- Group info button (only for group conversations) -->
          <button
            v-if="conversationStore.activeConversation.type === 'group'"
            class="p-2 text-gray-600 hover:text-gray-800 hover:bg-gray-100 rounded-lg transition-colors"
            title="群详情"
            @click="goToGroupManage"
          >
            <svg class="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M13 16h-1v-4h-1m1-4h.01M21 12a9 9 0 11-18 0 9 9 0 0118 0z"></path>
            </svg>
          </button>
        </div>
      </header>

      

      <!-- No conversation selected -->
      <div
        v-if="!conversationStore.activeConversation"
        class="flex-1 flex flex-col items-center justify-center text-gray-400"
      >
        <svg class="w-16 h-16 mb-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
          <path stroke-linecap="round" stroke-linejoin="round" stroke-width="1.5" d="M8 12h.01M12 12h.01M16 12h.01M21 12c0 4.418-4.03 8-9 8a9.863 9.863 0 01-4.255-.949L3 20l1.395-3.72C3.512 15.042 3 13.574 3 12c0-4.418 4.03-8 9-8s9 3.582 9 8z"></path>
        </svg>
        <p class="text-lg mb-2">选择一个会话开始聊天</p>
        <p class="text-sm">左侧选择或创建新的会话</p>
      </div>

      <!-- Messages -->
      <div
        v-else
        class="flex-1 overflow-hidden"
      >
        <VirtualMessageList
          :messages="activeMessages"
          :loading="isLoadingMessages"
          :loading-more="isLoadingMore"
          :has-more="hasMoreMessages"
          :participants="activeParticipants"
          @load-more="handleLoadMore"
          @retry="handleRetryMessage"
          @click-user="handleClickUser"
        />
      </div>

      <!-- Input -->
      <ChatInput
        v-if="conversationStore.activeConversationId"
        :conversation-id="conversationStore.activeConversationId"
        @send="handleSendMessage"
      />
    </main>

    <!-- User Info Modal -->
    <Teleport to="body">
      <div
        v-if="showUserInfoModal"
        class="fixed inset-0 z-50 flex items-center justify-center bg-black bg-opacity-50"
        @click="showUserInfoModal = false"
      >
        <div
          class="bg-white rounded-xl p-6 w-80 shadow-lg"
          @click.stop
        >
          <div class="flex flex-col items-center">
            <img
              v-if="selectedUserInfo?.avatar"
              :src="selectedUserInfo.avatar"
              class="w-20 h-20 rounded-full mb-4"
              :alt="selectedUserInfo?.nickname"
            />
            <div
              v-else
              class="w-20 h-20 rounded-full bg-gradient-to-br from-primary-400 to-primary-600 flex items-center justify-center text-white text-2xl font-medium mb-4"
            >
              {{ selectedUserInfo?.nickname?.charAt(0).toUpperCase() || '?' }}
            </div>
            
            <h3 class="text-lg font-semibold text-gray-800">
              {{ selectedUserInfo?.nickname || '用户' }}
            </h3>
            <p class="text-sm text-gray-500 mt-1">
              @{{ selectedUserInfo?.username }}
            </p>
            
            <div class="mt-6 flex gap-3">
              <button
                class="px-4 py-2 bg-primary-500 text-white rounded-lg hover:bg-primary-600"
                @click="showUserInfoModal = false"
              >
                关闭
              </button>
            </div>
          </div>
        </div>
      </div>
    </Teleport>
  </div>
</template>