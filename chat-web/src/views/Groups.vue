<script setup lang="ts">
import { ref, onMounted } from 'vue';
import { useRouter } from 'vue-router';
import { useGroupStore } from '@/stores/group';
import { useConversationStore } from '@/stores/conversation';

const router = useRouter();
const groupStore = useGroupStore();
const conversationStore = useConversationStore();

// UI state
const showCreateModal = ref(false);
const newGroupName = ref('');
const newGroupDescription = ref('');

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

// Open create modal
function openCreateModal(): void {
  newGroupName.value = '';
  newGroupDescription.value = '';
  showCreateModal.value = true;
}

// Close create modal
function closeCreateModal(): void {
  showCreateModal.value = false;
}

// Get role label
function getRoleLabel(role?: string): string {
  switch (role) {
    case 'owner': return '群主';
    case 'admin': return '管理员';
    default: return '成员';
  }
}

// Get role badge class
function getRoleBadgeClass(role?: string): string {
  switch (role) {
    case 'owner': return 'bg-yellow-100 text-yellow-700';
    case 'admin': return 'bg-blue-100 text-blue-700';
    default: return 'bg-gray-100 text-gray-500';
  }
}

// Go to group manage
function goToGroupManage(groupId: string): void {
  router.push(`/group-manage/${groupId}`);
}

// Create group
function handleCreateGroup(): void {
  if (!newGroupName.value.trim()) return;

  groupStore.createGroup(newGroupName.value.trim(), newGroupDescription.value.trim() || undefined);
  closeCreateModal();
}

// Start chat with group
async function startGroupChat(groupId: string): Promise<void> {
  const group = groupStore.getGroupById(groupId);
  if (!group) return;

  // Use conversationId from group to avoid duplicates
  const conversationId = group.conversationId || groupId;

  // Check if conversation already exists in local store
  let conv = conversationStore.getConversationById(conversationId);
  
  if (!conv) {
    // Not in local - fetch from API but don't auto-add to avoid duplicates
    try {
      const response = await fetch(`http://localhost:8082/api/chat/conversations`, {
        headers: { 'Authorization': `Bearer ${sessionStorage.getItem('chat_token')}` }
      });
      if (response.ok) {
        const data = await response.json();
        const apiConv = (data.conversations || []).find((c: any) => c.id === conversationId);
        if (apiConv) {
          // Use API data directly
          conversationStore.upsertConversation({
            id: apiConv.id,
            type: apiConv.type,
            name: apiConv.name,
            avatar: apiConv.avatar,
            participants: (apiConv.participants || []).map((p: any) => {
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
            createdAt: apiConv.createdAt
          });
        }
      }
    } catch (e) {
      console.error('Failed to fetch conversation:', e);
    }
    
    // Check again after API call
    conv = conversationStore.getConversationById(conversationId);
  }
  
  // If still not found, create from group data
  if (!conv) {
    conversationStore.upsertConversation({
      id: conversationId,
      type: 'group',
      name: group.name,
      avatar: group.avatar,
      participants: [],
      unreadCount: 0,
      hasNewMessages: false,
      pinned: group.pinned || false,
      muted: group.muted || false,
      createdAt: group.createdAt
    });
  }

  // Set active conversation
  conversationStore.setActiveConversation(conversationId);
  router.push('/');
}

// Go back
function goBack(): void {
  router.push('/');
}

// Toggle pin
async function handleTogglePin(groupId: string, event: Event): Promise<void> {
  event.stopPropagation();
  await groupStore.togglePin(groupId);
}

// Toggle mute
async function handleToggleMute(groupId: string, event: Event): Promise<void> {
  event.stopPropagation();
  await groupStore.toggleMute(groupId);
}

// On mounted - load groups from API
onMounted(() => {
  groupStore.loadGroups();
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
          <h1 class="text-lg font-semibold text-gray-800">群聊</h1>
        </div>
        <button
          class="p-2 hover:bg-gray-100 rounded-lg"
          title="创建群聊"
          @click="openCreateModal"
        >
          <svg class="w-5 h-5 text-gray-600" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 4v16m8-8H4"></path>
          </svg>
        </button>
      </div>
    </header>

    <!-- Content -->
    <main class="flex-1 overflow-y-auto p-4">
      <!-- All groups -->
      <div>
        <h2 class="text-sm font-medium text-gray-500 mb-3">我的群聊</h2>
        <div class="space-y-2">
          <div
            v-for="group in groupStore.groups"
            :key="group.id"
            class="flex items-center gap-3 p-3 bg-white rounded-xl"
          >
            <!-- Avatar -->
            <div
              class="w-12 h-12 rounded-full bg-gradient-to-br from-green-400 to-green-600 flex items-center justify-center text-white font-medium relative flex-shrink-0 cursor-pointer"
              @click="startGroupChat(group.id)"
            >
              {{ group.name.charAt(0).toUpperCase() }}
              <!-- Muted icon -->
              <svg
                v-if="group.isMuted"
                class="absolute -bottom-1 -right-1 w-4 h-4 bg-gray-500 rounded-full p-0.5"
                fill="currentColor"
                viewBox="0 0 24 24"
              >
                <path d="M5.586 15H4a1 1 0 01-1-1v-4a1 1 0 011-1h1.586l4.707-4.707C10.923 3.663 12 4.109 12 5v14c0 .891-1.077 1.337-1.707.707L5.586 15z" clip-rule="evenodd" />
                <path d="M17 14l2-2m0 0l2-2m-2 2l-2-2m2 2l2 2" stroke="white" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" />
              </svg>
            </div>

            <!-- Info -->
            <div class="flex-1 min-w-0 cursor-pointer" @click="startGroupChat(group.id)">
              <div class="flex items-center justify-between">
                <div class="flex items-center gap-1">
                  <span class="text-sm font-medium text-gray-800">{{ group.name }}</span>
                  <button 
                    v-if="group.pinned" 
                    class="text-xs text-yellow-500 hover:text-yellow-600"
                    @click="handleTogglePin(group.id, $event)"
                    title="取消置顶"
                  >📌</button>
                  <button 
                    v-if="group.muted" 
                    class="text-xs text-red-500 hover:text-red-600"
                    @click="handleToggleMute(group.id, $event)"
                    title="取消静音"
                  >🔕</button>
                  <button 
                    v-if="!group.pinned" 
                    class="text-xs text-gray-400 hover:text-yellow-500"
                    @click="handleTogglePin(group.id, $event)"
                    title="置顶"
                  >📍</button>
                  <button 
                    v-if="!group.muted" 
                    class="text-xs text-gray-400 hover:text-red-500"
                    @click="handleToggleMute(group.id, $event)"
                    title="静音"
                  >🔔</button>
                </div>
                <span class="text-xs text-gray-400">{{ formatTime(group.updatedAt) }}</span>
              </div>
              <div class="flex items-center justify-between mt-1">
                <div class="flex items-center gap-2">
                  <span class="text-xs text-gray-500">{{ group.memberCount }}人</span>
                  <!-- My role badge -->
                  <span
                    v-if="group.myRole"
                    class="text-xs px-1.5 py-0.5 rounded"
                    :class="getRoleBadgeClass(group.myRole)"
                  >
                    {{ getRoleLabel(group.myRole) }}
                  </span>
                </div>
                <span
                  v-if="group.unreadCount > 0"
                  class="min-w-[18px] h-[18px] px-1 bg-primary-500 text-white text-xs rounded-full flex items-center justify-center"
                >
                  {{ group.unreadCount > 99 ? '99+' : group.unreadCount }}
                </span>
                <span
                  v-else-if="group.hasNewMessages"
                  class="w-2 h-2 bg-red-500 rounded-full"
                ></span>
              </div>
            </div>

            <!-- Group details button -->
            <button
              class="p-2 text-gray-400 hover:text-gray-600 hover:bg-gray-100 rounded-lg flex-shrink-0"
              title="群详情"
              @click="goToGroupManage(group.id)"
            >
              <svg class="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M13 16h-1v-4h-1m1-4h.01M21 12a9 9 0 11-18 0 9 9 0 0118 0z"></path>
              </svg>
            </button>
          </div>

          <!-- Empty state -->
          <div
            v-if="groupStore.groupsCount === 0"
            class="flex flex-col items-center justify-center py-12 text-gray-400"
          >
            <svg class="w-12 h-12 mb-2" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="1.5" d="M17 20h5v-2a3 3 0 00-5.356-1.857M17 20H7m10 0v-2c0-.656-.126-1.283-.356-1.857M7 20H2v-2a3 3 0 015.356-1.857M7 20v-2c0-.656.126-1.283.356-1.857m0 0a5.002 5.002 0 019.288 0M15 7a3 3 0 11-6 0 3 3 0 016 0zm6 3a4 4 0 11-8 0 4 4 0 018 0zM17 20a4 4 0 01-8 0"></path>
            </svg>
            <p class="text-sm">暂无群聊</p>
            <button
              class="mt-2 px-4 py-2 text-sm text-primary-600 hover:bg-primary-50 rounded-lg"
              @click="openCreateModal"
            >
              创建第一个群
            </button>
          </div>
        </div>
      </div>
    </main>

    <!-- Create Group Modal -->
    <Teleport to="body">
      <div
        v-if="showCreateModal"
        class="fixed inset-0 bg-black/50 flex items-center justify-center z-50 p-4"
        @click.self="closeCreateModal"
      >
        <div class="bg-white rounded-2xl w-full max-w-sm p-6">
          <h3 class="text-lg font-semibold text-gray-800 mb-4">创建群聊</h3>

          <div class="space-y-4">
            <div>
              <label class="block text-sm font-medium text-gray-700 mb-1">群名称</label>
              <input
                v-model="newGroupName"
                type="text"
                placeholder="请输入群名称"
                class="w-full px-4 py-2 border border-gray-200 rounded-xl text-sm focus:outline-none focus:ring-2 focus:ring-primary-500"
              >
            </div>

            <div>
              <label class="block text-sm font-medium text-gray-700 mb-1">群简介 (可选)</label>
              <textarea
                v-model="newGroupDescription"
                placeholder="请输入群简介"
                rows="3"
                class="w-full px-4 py-2 border border-gray-200 rounded-xl text-sm focus:outline-none focus:ring-2 focus:ring-primary-500 resize-none"
              ></textarea>
            </div>
          </div>

          <div class="flex gap-3 mt-6">
            <button
              class="flex-1 py-2 text-sm text-gray-600 bg-gray-100 rounded-xl hover:bg-gray-200"
              @click="closeCreateModal"
            >
              取消
            </button>
            <button
              class="flex-1 py-2 text-sm text-white bg-primary-500 rounded-xl hover:bg-primary-600 disabled:opacity-50"
              :disabled="!newGroupName.trim()"
              @click="handleCreateGroup"
            >
              创建
            </button>
          </div>
        </div>
      </div>
    </Teleport>
  </div>
</template>