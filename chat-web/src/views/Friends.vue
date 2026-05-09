<script setup lang="ts">
import { ref, onMounted } from 'vue';
import { useRouter } from 'vue-router';
import { useFriendStore } from '@/stores/friend';
import { useConversationStore } from '@/stores/conversation';
import { chatApi } from '@/utils/api';

const router = useRouter();
const friendStore = useFriendStore();
const conversationStore = useConversationStore();

// Search
const searchQuery = ref('');
const isSearching = ref(false);

// Search users
async function handleSearch(): Promise<void> {
  if (!searchQuery.value.trim()) {
    friendStore.clearSearchResults();
    return;
  }

  isSearching.value = true;
  await friendStore.searchUsers(searchQuery.value.trim());
  isSearching.value = false;
}

// Send friend request
function sendFriendRequest(userId: string): void {
  friendStore.sendFriendRequest(userId, '你好，加个好友吧');
  // Update search results
  const results = friendStore.searchResults.map(r =>
    r.userId === userId ? { ...r, hasPendingRequest: true } : r
  );
  friendStore.setSearchResults(results);
}

// Start chat with friend
async function startChat(friendId: string): Promise<void> {
  try {
    // Call backend to get or create conversation (returns actual conversation ID)
    const result = await chatApi.getOrCreatePrivate(friendId) as any;
    const conversationId = result.id;
    
    // Check if already in local first
    let conv = conversationStore.getConversationById(conversationId);
    
    if (!conv) {
      // Not in local - check API directly to avoid adding to store here
      try {
        const response = await fetch(`http://localhost:8082/api/chat/conversations`, {
          headers: { 'Authorization': `Bearer ${sessionStorage.getItem('chat_token')}` }
        });
        if (response.ok) {
          const data = await response.json();
          const apiConv = (data.conversations || []).find((c: any) => c.id === conversationId);
          if (apiConv) {
            conversationStore.upsertConversation({
              id: apiConv.id,
              type: apiConv.type,
              name: apiConv.name || result.name,
              avatar: apiConv.avatar || result.avatar,
              participants: (apiConv.participants || result.participants || []).map((p: any) => {
                const userId = typeof p === 'string' ? p : (p.userId || '');
                return {
                  userId,
                  nickname: typeof p === 'object' && p.nickname ? p.nickname : userId,
                  avatar: typeof p === 'object' ? p.avatar : undefined
                };
              }),
              unreadCount: apiConv.unreadCount || 0,
              hasNewMessages: apiConv.hasNewMessages || false,
              pinned: apiConv.pinned || false,
              muted: apiConv.muted || false,
              createdAt: apiConv.createdAt || result.createdAt
            });
          }
        }
      } catch (e) {
        console.error('Failed to fetch conversation:', e);
      }
      
      conv = conversationStore.getConversationById(conversationId);
    }
    
    // Still not found - create from result
    if (!conv && result) {
      conversationStore.upsertConversation({
        id: conversationId,
        type: 'private',
        name: result.name || '会话',
        avatar: result.avatar,
        participants: result.participants || [],
        unreadCount: 0,
      hasNewMessages: false,
        pinned: false,
        muted: false,
        createdAt: result.createdAt || Date.now()
      });
    }

    conversationStore.setActiveConversation(conversationId);
    router.push('/');
  } catch (e) {
    console.error('Failed to create conversation:', e);
  }
}

// Go back
function goBack(): void {
  router.push('/');
}

// Go to friend requests
function goToRequests(): void {
  router.push('/friend-requests');
}

// On mounted - load friends from API
onMounted(() => {
  friendStore.loadFriends();
});
</script>

<template>
  <div class="min-h-screen bg-gray-100 flex flex-col">
    <!-- Header -->
    <header class="bg-white border-b border-gray-200 px-4 py-3">
      <div class="flex items-center justify-between">
        <div class="flex items-center gap-3">
          <button
            class="p-2 hover:bg-gray-100 rounded-lg"
            @click="goBack"
          >
            <svg class="w-5 h-5 text-gray-600" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M15 19l-7-7 7-7"></path>
            </svg>
          </button>
          <h1 class="text-lg font-semibold text-gray-800">好友</h1>
        </div>
        <button
          class="p-2 hover:bg-gray-100 rounded-lg"
          @click="goToRequests"
        >
          <svg class="w-5 h-5 text-gray-600" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 4v16m8-8H4"></path>
          </svg>
        </button>
      </div>
    </header>

    <!-- Content -->
    <main class="flex-1 overflow-y-auto p-4">
      <!-- Search -->
      <div class="relative mb-4">
        <input
          v-model="searchQuery"
          type="text"
          placeholder="搜索好友..."
          class="w-full px-4 py-2 pl-10 bg-white border border-gray-200 rounded-xl text-sm focus:outline-none focus:ring-2 focus:ring-primary-500"
          @keydown.enter="handleSearch"
        >
        <svg
          class="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-gray-400"
          fill="none"
          stroke="currentColor"
          viewBox="0 0 24 24"
        >
          <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M21 21l-6-6m2-5a7 7 0 11-14 0 7 7 0 0114 0z"></path>
        </svg>
      </div>

      <!-- Search results -->
      <div v-if="searchQuery.trim() && friendStore.searchResults.length > 0" class="space-y-2 mb-4">
        <div
          v-for="result in friendStore.searchResults"
          :key="result.userId"
          class="flex items-center gap-3 p-3 bg-white rounded-xl"
        >
          <img
            v-if="result.avatar"
            :src="result.avatar"
            class="w-10 h-10 rounded-full"
          />
          <div
            v-else
            class="w-10 h-10 rounded-full bg-gradient-to-br from-primary-400 to-primary-600 flex items-center justify-center text-white text-sm"
          >
            {{ result.nickname?.charAt(0).toUpperCase() || '?' }}
          </div>
          <div class="flex-1">
            <span class="text-sm text-gray-800">{{ result.nickname }}</span>
          </div>
          <button
            v-if="result.isFriend"
            class="text-sm text-primary-600"
            @click="startChat(result.userId)"
          >
            发消息
          </button>
          <button
            v-else-if="!result.hasPendingRequest"
            class="text-sm text-white bg-primary-500 px-3 py-1 rounded-lg"
            @click="sendFriendRequest(result.userId)"
          >
            添加
          </button>
          <span v-else class="text-xs text-gray-400">已发送</span>
        </div>
      </div>

      <!-- Friend list from API -->
      <div class="space-y-2">
        <div
          v-for="friend in friendStore.friends"
          :key="friend.id"
          class="flex items-center gap-3 p-3 bg-white rounded-xl cursor-pointer hover:bg-gray-50"
          @click="startChat(friend.userId)"
        >
          <img
            v-if="friend.avatar"
            :src="friend.avatar"
            class="w-12 h-12 rounded-full"
          />
          <div
            v-else
            class="w-12 h-12 rounded-full bg-gradient-to-br from-primary-400 to-primary-600 flex items-center justify-center text-white font-medium"
          >
            {{ friend.nickname?.charAt(0).toUpperCase() || '?' }}
          </div>
          <div class="flex-1">
            <span class="text-sm font-medium text-gray-800">{{ friend.nickname || '未知' }}</span>
          </div>
        </div>

        <!-- Empty state -->
        <div
          v-if="friendStore.friendsCount === 0 && !searchQuery.trim()"
          class="flex flex-col items-center justify-center py-12 text-gray-400"
        >
          <svg class="w-12 h-12 mb-2" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="1.5" d="M17 20h5v-2a3 3 0 00-5.356-1.857M17 20H7m10 0v-2c0-.656-.126-1.283-.356-1.857M7 20H2v-2a3 3 0 015.356-1.857M7 20v-2c0-.656.126-1.283.356-1.857m0 0a5.002 5.002 0 019.288 0M15 7a3 3 0 11-6 0 3 3 0 016 0z"></path>
          </svg>
          <p class="text-sm">暂无好友</p>
        </div>
      </div>
    </main>
  </div>
</template>