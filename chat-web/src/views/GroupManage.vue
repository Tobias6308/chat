<script setup lang="ts">
import { ref, computed, onMounted } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { useGroupStore } from '@/stores/group';
import { useConversationStore } from '@/stores/conversation';
import { friendApi, chatApi } from '@/utils/api';
import type { GroupMemberRole } from '@/types/chat';

const route = useRoute();
const router = useRouter();
const groupStore = useGroupStore();

const groupId = computed(() => route.params.id as string);

const group = computed(() => groupStore.currentGroup || groupStore.getGroupById(groupId.value));

// UI state
const activeTab = ref<'info' | 'members'>('info');
const showEditModal = ref(false);
const editName = ref('');
const editDescription = ref('');
const showDissolveConfirm = ref(false);
const showAddMembersModal = ref(false);
const showAvatarModal = ref(false);
const editAvatarUrl = ref('');
const friendsList = ref<any[]>([]);
const selectedFriends = ref<string[]>([]);
const isLoadingFriends = ref(false);
const friendUserIds = ref<Set<string>>(new Set());

// Check permissions
const canManage = computed(() => {
  if (!group.value) return false;
  const userId = getCurrentUserId();
  if (!userId) return false;
  return groupStore.isGroupAdmin(groupId.value, userId);
});

const isOwner = computed(() => {
  if (!group.value) return false;
  const userId = getCurrentUserId();
  if (!userId) return false;
  return groupStore.isGroupOwner(groupId.value, userId);
});

// Format time
function formatTime(timestamp: number): string {
  const date = new Date(timestamp);
  return date.toLocaleDateString('zh-CN', {
    year: 'numeric',
    month: 'short',
    day: 'numeric'
  });
}

// Get role label
function getRoleLabel(role: GroupMemberRole): string {
  switch (role) {
    case 'owner': return '群主';
    case 'admin': return '管理员';
    default: return '成员';
  }
}

// Get role badge color
function getRoleColor(role: GroupMemberRole): string {
  switch (role) {
    case 'owner': return 'bg-yellow-100 text-yellow-700';
    case 'admin': return 'bg-blue-100 text-blue-700';
    default: return 'bg-gray-100 text-gray-600';
  }
}

// Open edit modal
function openEditModal(): void {
  if (!group.value) return;
  editName.value = group.value.name;
  editDescription.value = group.value.description || '';
  showEditModal.value = true;
}

// Close edit modal
function closeEditModal(): void {
  showEditModal.value = false;
}

// Save group info
function saveGroupInfo(): void {
  groupStore.updateGroupInfo(groupId.value, editName.value, editDescription.value);
  closeEditModal();
}

// Set admin
function setAsAdmin(userId: string): void {
  groupStore.updateMemberRole(groupId.value, userId, 'admin');
}

// Remove admin
function removeAsAdmin(userId: string): void {
  groupStore.updateMemberRole(groupId.value, userId, 'member');
}

// Remove member
function removeMember(userId: string, nickname: string): void {
  if (confirm(`确定要移除成员 ${nickname} 吗？`)) {
    groupStore.removeMember(groupId.value, userId);
  }
}

// Toggle mute
function toggleMute(userId: string): void {
  groupStore.toggleMemberMute(groupId.value, userId);
}

// Start chat with member
async function chatWithMember(userId: string, nickname: string): Promise<void> {
  try {
    const result = await chatApi.getOrCreatePrivate(userId) as any;
    const conversationStore = useConversationStore();
    
    // 检查会话是否已存在
    const existing = conversationStore.getConversationById(result.id);
    if (!existing) {
      conversationStore.upsertConversation({
        id: result.id,
        type: 'private',
        name: result.name || nickname,
        avatar: result.avatar,
        participants: result.participants || [
          { userId, nickname },
          { userId: getCurrentUserId(), nickname: '我' }
        ],
        unreadCount: 0,
        hasNewMessages: false,
        pinned: false,
        muted: false,
        createdAt: result.createdAt || Date.now()
      });
    }

    conversationStore.setActiveConversation(result.id);
    router.push('/');
  } catch (error) {
    console.error('Failed to create conversation:', error);
  }
}

// Dissolve group
function dissolveGroup(): void {
  groupStore.dissolveGroup(groupId.value);
  router.push('/groups');
}

// Leave group
function leaveGroup(): void {
  groupStore.leaveGroup(groupId.value);
  router.push('/groups');
}

// Open add members modal
async function openAddMembersModal(): Promise<void> {
  showAddMembersModal.value = true;
  selectedFriends.value = [];
  isLoadingFriends.value = true;
  
  try {
    const response = await friendApi.getList() as any;
    const friends = response.friends || [];
    
    const existingMemberIds = group.value?.members.map(m => m.userId) || [];
    
    friendsList.value = friends.filter((f: any) => !existingMemberIds.includes(f.userId));
  } catch (error) {
    console.error('Failed to load friends:', error);
    friendsList.value = [];
  } finally {
    isLoadingFriends.value = false;
  }
}

// Close add members modal
function closeAddMembersModal(): void {
  showAddMembersModal.value = false;
  friendsList.value = [];
  selectedFriends.value = [];
}

// Toggle friend selection
function toggleFriendSelection(userId: string): void {
  const index = selectedFriends.value.indexOf(userId);
  if (index === -1) {
    selectedFriends.value.push(userId);
  } else {
    selectedFriends.value.splice(index, 1);
  }
}

// Add members to group
async function addMembers(): Promise<void> {
  if (selectedFriends.value.length === 0) return;
  
  try {
    await groupStore.addMembersToGroup(groupId.value, selectedFriends.value);
    closeAddMembersModal();
  } catch (error) {
    console.error('Failed to add members:', error);
  }
}

// Go back
function goBack(): void {
  router.push('/groups');
}

// Get current user ID
function getCurrentUserId(): string {
  return sessionStorage.getItem('chat_userId') || '';
}

// On mounted
onMounted(async () => {
  if (!group.value) {
    router.push('/groups');
    return;
  }
  // 加载成员列表
  await groupStore.setCurrentGroup(groupId.value);
  // 加载好友列表
  await loadFriendsList();
});

// Load friends list
async function loadFriendsList(): Promise<void> {
  try {
    const response = await friendApi.getList() as any;
    const friends = response.friends || [];
    friendUserIds.value = new Set(friends.map((f: any) => f.userId));
  } catch (error) {
    console.error('Failed to load friends:', error);
  }
}

// Check if user is friend
function isFriend(userId: string): boolean {
  return friendUserIds.value.has(userId);
}

// Add friend
async function addFriend(userId: string): Promise<void> {
  try {
    await friendApi.sendRequest(userId);
    alert('已发送好友请求');
  } catch (error) {
    console.error('Failed to add friend:', error);
  }
}

// Open avatar edit modal
function openAvatarModal(): void {
  editAvatarUrl.value = group.value?.avatar || '';
  showAvatarModal.value = true;
}

// Close avatar modal
function closeAvatarModal(): void {
  showAvatarModal.value = false;
  editAvatarUrl.value = '';
}

// Save avatar
function saveAvatar(): void {
  if (group.value) {
    groupStore.updateGroupInfo(groupId.value, group.value.name, group.value.description, editAvatarUrl.value);
  }
  closeAvatarModal();
}
</script>

<template>
  <div v-if="group" class="min-h-screen bg-gray-100 flex flex-col">
    <!-- Header -->
    <header class="bg-white border-b border-gray-200 px-4 py-3">
      <div class="flex items-center gap-3">
        <button
          class="p-2 hover:bg-gray-100 rounded-lg"
          @click="goBack"
        >
          <svg class="w-5 h-5 text-gray-600" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M15 19l-7-7 7-7"></path>
          </svg>
        </button>
        <h1 class="text-lg font-semibold text-gray-800">群管理</h1>
      </div>
    </header>

    <!-- Tabs -->
    <div class="bg-white border-b border-gray-200 px-4">
      <div class="flex gap-4">
        <button
          class="py-3 text-sm font-medium border-b-2 transition-colors"
          :class="activeTab === 'info' ? 'border-primary-500 text-primary-600' : 'border-transparent text-gray-500'"
          @click="activeTab = 'info'"
        >
          群信息
        </button>
        <button
          class="py-3 text-sm font-medium border-b-2 transition-colors"
          :class="activeTab === 'members' ? 'border-primary-500 text-primary-600' : 'border-transparent text-gray-500'"
          @click="activeTab = 'members'"
        >
          成员管理 ({{ group.members.length }})
        </button>
      </div>
    </div>

    <!-- Content -->
    <main class="flex-1 overflow-y-auto">
      <!-- Info Tab -->
      <div v-if="activeTab === 'info'" class="p-4 space-y-4">
        <!-- Group Avatar & Name -->
        <div class="bg-white rounded-xl p-4">
          <div class="flex items-center gap-4">
            <!-- Avatar with edit -->
            <div class="relative">
              <img
                v-if="group.avatar"
                :src="group.avatar"
                class="w-16 h-16 rounded-full object-cover"
                :alt="group.name"
              />
              <div
                v-else
                class="w-16 h-16 rounded-full bg-gradient-to-br from-green-400 to-green-600 flex items-center justify-center text-white text-xl font-medium"
              >
                {{ group.name.charAt(0).toUpperCase() }}
              </div>
              <!-- Edit avatar button -->
              <button
                v-if="canManage"
                class="absolute bottom-0 right-0 w-6 h-6 bg-primary-500 rounded-full flex items-center justify-center text-white hover:bg-primary-600"
                @click="openAvatarModal"
              >
                <svg class="w-3 h-3" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M15.232 5.232l3.536 3.536m-2.036-5.036a2.5 2.5 0 113.536 3.536L6.5 21.036H3v-3.572L16.732 3.732z"></path>
                </svg>
              </button>
            </div>
            <div class="flex-1">
              <h2 class="text-lg font-semibold text-gray-800">{{ group.name }}</h2>
              <p class="text-sm text-gray-500">{{ group.memberCount }} 位成员</p>
            </div>
            <button
              v-if="canManage"
              class="p-2 hover:bg-gray-100 rounded-lg"
              @click="openEditModal"
            >
              <svg class="w-5 h-5 text-gray-500" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M15.232 5.232l3.536 3.536m-2.036-5.036a2.5 2.5 0 113.536 3.536L6.5 21.036H3v-3.572L16.732 3.732z"></path>
              </svg>
            </button>
          </div>
        </div>

        <!-- Group Description -->
        <div class="bg-white rounded-xl p-4">
          <h3 class="text-sm font-medium text-gray-500 mb-2">群简介</h3>
          <p class="text-sm text-gray-800">{{ group.description || '暂无简介' }}</p>
        </div>

        <!-- Group Info -->
        <div class="bg-white rounded-xl p-4">
          <h3 class="text-sm font-medium text-gray-500 mb-3">群信息</h3>
          <div class="space-y-3">
            <div class="flex justify-between">
              <span class="text-sm text-gray-500">群号</span>
              <span class="text-sm text-gray-800">{{ group.id }}</span>
            </div>
            <div class="flex justify-between">
              <span class="text-sm text-gray-500">创建时间</span>
              <span class="text-sm text-gray-800">{{ formatTime(group.createdAt) }}</span>
            </div>
            <div class="flex justify-between">
              <span class="text-sm text-gray-500">群主</span>
              <span class="text-sm text-gray-800">
                {{ group.members.find(m => m.role === 'owner')?.nickname || '未知' }}
              </span>
            </div>
          </div>
        </div>

        <!-- Actions -->
        <div class="bg-white rounded-xl p-4 space-y-3">
          <button
            v-if="isOwner"
            class="w-full py-2 text-sm text-red-600 bg-red-50 rounded-lg hover:bg-red-100"
            @click="showDissolveConfirm = true"
          >
            解散群聊
          </button>
          <button
            v-else
            class="w-full py-2 text-sm text-red-600 bg-red-50 rounded-lg hover:bg-red-100"
            @click="leaveGroup"
          >
            退出群聊
          </button>
        </div>
      </div>

      <!-- Members Tab -->
      <div v-if="activeTab === 'members'" class="p-4">
        <!-- Add Members Button -->
        <div
          v-if="canManage"
          class="mb-4"
        >
          <button
            class="w-full py-2 text-sm text-primary-600 bg-primary-50 rounded-lg hover:bg-primary-100 flex items-center justify-center gap-2"
            @click="openAddMembersModal"
          >
            <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 4v16m8-8H4"></path>
            </svg>
            邀请好友入群
          </button>
        </div>

        <!-- Pending requests -->
        <div
          v-if="groupStore.pendingJoinRequestsCount > 0 && canManage"
          class="mb-4 bg-yellow-50 rounded-xl p-4 border border-yellow-200"
        >
          <div class="flex items-center justify-between">
            <div>
              <h3 class="text-sm font-medium text-gray-800">入群申请</h3>
              <p class="text-xs text-gray-500">{{ groupStore.pendingJoinRequestsCount }} 条待处理</p>
            </div>
            <button
              class="text-sm text-primary-600 hover:underline"
              @click="router.push('/group-requests/' + groupId)"
            >
              查看
            </button>
          </div>
        </div>

        <!-- Members list -->
        <div class="space-y-2">
          <div
            v-for="member in group.members"
            :key="member.userId"
            class="flex items-center gap-3 p-3 bg-white rounded-xl"
          >
            <!-- Avatar -->
            <div class="w-10 h-10 rounded-full bg-gradient-to-br from-primary-400 to-primary-600 flex items-center justify-center text-white font-medium">
              {{ member.nickname.charAt(0).toUpperCase() }}
            </div>

            <!-- Info -->
            <div class="flex-1 min-w-0">
              <div class="flex items-center gap-2">
                <span class="text-sm font-medium text-gray-800">{{ member.nickname }}</span>
                <span
                  class="text-xs px-1.5 py-0.5 rounded"
                  :class="getRoleColor(member.role)"
                >
                  {{ getRoleLabel(member.role) }}
                </span>
                <span
                  v-if="member.muted"
                  class="text-xs text-gray-400"
                >
                  (已被禁言)
                </span>
              </div>
            </div>

            <!-- Actions (only for admins) -->
            <div v-if="canManage && member.userId !== getCurrentUserId()" class="flex items-center gap-1">
              <!-- Set/remove admin -->
              <button
                v-if="isOwner && member.role === 'member'"
                class="p-2 text-xs text-blue-600 hover:bg-blue-50 rounded"
                title="设为管理员"
                @click="setAsAdmin(member.userId)"
              >
                升管理
              </button>
              <button
                v-if="isOwner && member.role === 'admin'"
                class="p-2 text-xs text-gray-600 hover:bg-gray-100 rounded"
                title="取消管理员"
                @click="removeAsAdmin(member.userId)"
              >
                取消
              </button>

              <!-- Mute -->
              <button
                class="p-2 text-xs text-gray-600 hover:bg-gray-100 rounded"
                :title="member.muted ? '解除禁言' : '禁言'"
                @click="toggleMute(member.userId)"
              >
                {{ member.muted ? '解禁' : '禁言' }}
              </button>

              <!-- Remove -->
              <button
                class="p-2 text-xs text-red-600 hover:bg-red-50 rounded"
                title="移除群聊"
                @click="removeMember(member.userId, member.nickname)"
              >
                移除
              </button>
            </div>

            <!-- Chat / Add Friend (only for other members, not self) -->
            <div v-if="member.userId !== getCurrentUserId()" class="flex items-center gap-1">
              <!-- Chat (only for friends) -->
              <button
                v-if="isFriend(member.userId)"
                class="p-2 text-gray-500 hover:bg-gray-100 rounded-lg"
                title="发起聊天"
                @click="chatWithMember(member.userId, member.nickname)"
              >
                <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M8 12h.01M12 12h.01M16 12h.01M21 12c0 4.418-4.03 8-9 8a9.863 9.863 0 01-4.255-.949L3 20l1.395-3.72C3.512 15.042 3 13.574 3 12c0-4.418 4.03-8 9-8s9 3.582 9 8z"></path>
                </svg>
              </button>
              <!-- Add friend (only for non-friends) -->
              <button
                v-else
                class="p-2 text-primary-500 hover:bg-primary-50 rounded-lg"
                title="添加好友"
                @click="addFriend(member.userId)"
              >
                <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M18 9v3m0 0v3m0-3h3m-3 0h-3m-2-5a4 4 0 11-8 0 4 4 0 018 0zM3 20a6 6 0 0112 0v1H3v-1z"></path>
                </svg>
              </button>
            </div>
          </div>
        </div>
      </div>
    </main>

    <!-- Edit Modal -->
    <Teleport to="body">
      <div
        v-if="showEditModal"
        class="fixed inset-0 bg-black/50 flex items-center justify-center z-50 p-4"
        @click.self="closeEditModal"
      >
        <div class="bg-white rounded-2xl w-full max-w-sm p-6">
          <h3 class="text-lg font-semibold text-gray-800 mb-4">编辑群信息</h3>

          <div class="space-y-4">
            <div>
              <label class="block text-sm font-medium text-gray-700 mb-1">群名称</label>
              <input
                v-model="editName"
                type="text"
                class="w-full px-4 py-2 border border-gray-200 rounded-xl text-sm focus:outline-none focus:ring-2 focus:ring-primary-500"
              >
            </div>

            <div>
              <label class="block text-sm font-medium text-gray-700 mb-1">群简介</label>
              <textarea
                v-model="editDescription"
                rows="3"
                class="w-full px-4 py-2 border border-gray-200 rounded-xl text-sm focus:outline-none focus:ring-2 focus:ring-primary-500 resize-none"
              ></textarea>
            </div>
          </div>

          <div class="flex gap-3 mt-6">
            <button
              class="flex-1 py-2 text-sm text-gray-600 bg-gray-100 rounded-xl hover:bg-gray-200"
              @click="closeEditModal"
            >
              取消
            </button>
            <button
              class="flex-1 py-2 text-sm text-white bg-primary-500 rounded-xl hover:bg-primary-600"
              @click="saveGroupInfo"
            >
              保存
            </button>
          </div>
        </div>
      </div>
    </Teleport>

    <!-- Dissolve Confirm -->
    <Teleport to="body">
      <div
        v-if="showDissolveConfirm"
        class="fixed inset-0 bg-black/50 flex items-center justify-center z-50 p-4"
        @click.self="showDissolveConfirm = false"
      >
        <div class="bg-white rounded-2xl w-full max-w-sm p-6">
          <h3 class="text-lg font-semibold text-gray-800 mb-2">解散群聊</h3>
          <p class="text-sm text-gray-500 mb-6">确定要解散该群吗？此操作不可恢复。</p>
          <div class="flex gap-3">
            <button
              class="flex-1 py-2 text-sm text-gray-600 bg-gray-100 rounded-xl hover:bg-gray-200"
              @click="showDissolveConfirm = false"
            >
              取消
            </button>
            <button
              class="flex-1 py-2 text-sm text-white bg-red-500 rounded-xl hover:bg-red-600"
              @click="dissolveGroup"
            >
              确认解散
            </button>
          </div>
        </div>
      </div>
    </Teleport>

    <!-- Add Members Modal -->
    <Teleport to="body">
      <div
        v-if="showAddMembersModal"
        class="fixed inset-0 bg-black/50 flex items-center justify-center z-50 p-4"
        @click.self="closeAddMembersModal"
      >
        <div class="bg-white rounded-2xl w-full max-w-sm p-6 max-h-[80vh] flex flex-col">
          <h3 class="text-lg font-semibold text-gray-800 mb-4">邀请好友入群</h3>
          
          <div v-if="isLoadingFriends" class="flex-1 flex items-center justify-center py-8">
            <div class="text-gray-400">加载中...</div>
          </div>
          
          <div v-else-if="friendsList.length === 0" class="flex-1 flex items-center justify-center py-8">
            <div class="text-gray-400">暂无好友可邀请</div>
          </div>
          
          <div v-else class="flex-1 overflow-y-auto space-y-2 mb-4">
            <div
              v-for="friend in friendsList"
              :key="friend.userId"
              class="flex items-center gap-3 p-2 rounded-lg hover:bg-gray-50 cursor-pointer"
              :class="selectedFriends.includes(friend.userId) ? 'bg-primary-50' : ''"
              @click="toggleFriendSelection(friend.userId)"
            >
              <div class="w-10 h-10 rounded-full bg-gradient-to-br from-primary-400 to-primary-600 flex items-center justify-center text-white font-medium">
                {{ friend.nickname?.charAt(0).toUpperCase() || '?' }}
              </div>
              <div class="flex-1 min-w-0">
                <div class="text-sm font-medium text-gray-800">{{ friend.nickname }}</div>
                <div class="text-xs text-gray-500">@{{ friend.username }}</div>
              </div>
              <div
                class="w-5 h-5 rounded-full border-2 flex items-center justify-center"
                :class="selectedFriends.includes(friend.userId) ? 'border-primary-500 bg-primary-500' : 'border-gray-300'"
              >
                <svg v-if="selectedFriends.includes(friend.userId)" class="w-3 h-3 text-white" fill="currentColor" viewBox="0 0 20 20">
                  <path fill-rule="evenodd" d="M16.707 5.293a1 1 0 010 1.414l-8 8a1 1 0 01-1.414 0l-4-4a1 1 0 011.414-1.414L8 12.586l7.293-7.293a1 1 0 011.414 0z" clip-rule="evenodd"></path>
                </svg>
              </div>
            </div>
          </div>
          
          <div class="flex gap-3">
            <button
              class="flex-1 py-2 text-sm text-gray-600 bg-gray-100 rounded-xl hover:bg-gray-200"
              @click="closeAddMembersModal"
            >
              取消
            </button>
            <button
              class="flex-1 py-2 text-sm text-white bg-primary-500 rounded-xl hover:bg-primary-600 disabled:opacity-50"
              :disabled="selectedFriends.length === 0"
              @click="addMembers"
            >
              邀请 ({{ selectedFriends.length }})
            </button>
          </div>
        </div>
      </div>
    </Teleport>

    <!-- Avatar Edit Modal -->
    <Teleport to="body">
      <div
        v-if="showAvatarModal"
        class="fixed inset-0 bg-black/50 flex items-center justify-center z-50 p-4"
        @click.self="closeAvatarModal"
      >
        <div class="bg-white rounded-2xl w-full max-w-sm p-6">
          <h3 class="text-lg font-semibold text-gray-800 mb-4">修改群头像</h3>
          
          <!-- Avatar preview -->
          <div class="flex justify-center mb-4">
            <div class="relative">
              <img
                v-if="editAvatarUrl"
                :src="editAvatarUrl"
                class="w-24 h-24 rounded-full object-cover"
                alt="群头像"
                @error="editAvatarUrl = ''"
              />
              <div
                v-else
                class="w-24 h-24 rounded-full bg-gradient-to-br from-green-400 to-green-600 flex items-center justify-center text-white text-2xl font-medium"
              >
                ?
              </div>
            </div>
          </div>
          
          <div>
            <label class="block text-sm font-medium text-gray-700 mb-1">头像链接</label>
            <input
              v-model="editAvatarUrl"
              type="text"
              placeholder="输入图片链接"
              class="w-full px-4 py-2 border border-gray-200 rounded-xl text-sm focus:outline-none focus:ring-2 focus:ring-primary-500"
            >
          </div>
          
          <div class="flex gap-3 mt-6">
            <button
              class="flex-1 py-2 text-sm text-gray-600 bg-gray-100 rounded-xl hover:bg-gray-200"
              @click="closeAvatarModal"
            >
              取消
            </button>
            <button
              class="flex-1 py-2 text-sm text-white bg-primary-500 rounded-xl hover:bg-primary-600"
              @click="saveAvatar"
            >
              保存
            </button>
          </div>
        </div>
      </div>
    </Teleport>
  </div>
</template>