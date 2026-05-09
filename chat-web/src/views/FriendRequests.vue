<script setup lang="ts">
import { ref, computed, onMounted } from 'vue';
import { useRouter } from 'vue-router';
import { useFriendStore } from '@/stores/friend';

const router = useRouter();
const friendStore = useFriendStore();

// Tab state
const activeTab = ref<'received' | 'sent'>('received');

// Received requests
const receivedPending = computed(() =>
  friendStore.receivedRequests.filter(r => r.status === 'pending')
);

const receivedProcessed = computed(() =>
  friendStore.receivedRequests.filter(r => r.status !== 'pending')
);

// Sent requests
const sentPending = computed(() =>
  friendStore.sentRequests.filter(r => r.status === 'pending')
);

const sentProcessed = computed(() =>
  friendStore.sentRequests.filter(r => r.status !== 'pending')
);

// Refresh on mount
onMounted(() => {
  friendStore.loadFriendRequests();
});

// Format time
function formatTime(timestamp?: number): string {
  if (!timestamp) return '';
  const date = new Date(timestamp);
  const now = new Date();
  const diff = now.getTime() - date.getTime();
  const hours = Math.floor(diff / (1000 * 60 * 60));
  const days = Math.floor(diff / (1000 * 60 * 60 * 24));

  if (hours < 1) {
    return '刚刚';
  } else if (hours < 24) {
    return `${hours}小时前`;
  } else if (days < 7) {
    return `${days}天前`;
  } else {
    return date.toLocaleDateString('zh-CN', { month: 'short', day: 'numeric' });
  }
}

// Accept request
function acceptRequest(requestId: string): void {
  friendStore.acceptRequest(requestId);
}

// Reject request
function rejectRequest(requestId: string): void {
  friendStore.rejectRequest(requestId);
}

// Go back
function goBack(): void {
  router.push('/friends');
}

// Get status text
function getStatusText(status: string): string {
  switch (status) {
    case 'pending': return '等待对方验证';
    case 'accepted': return '已同意';
    case 'rejected': return '已拒绝';
    default: return status;
  }
}

// Get status color
function getStatusColor(status: string): string {
  switch (status) {
    case 'pending': return 'bg-yellow-100 text-yellow-600';
    case 'accepted': return 'bg-green-100 text-green-600';
    case 'rejected': return 'bg-gray-100 text-gray-500';
    default: return 'bg-gray-100 text-gray-500';
  }
}
</script>

<template>
  <div class="min-h-screen bg-gray-100 flex flex-col">
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
        <h1 class="text-lg font-semibold text-gray-800">好友请求</h1>
      </div>

      <!-- Tabs -->
      <div class="flex gap-4 mt-3">
        <button
          class="text-sm font-medium pb-2 border-b-2 transition-colors"
          :class="activeTab === 'received' ? 'border-primary-500 text-primary-600' : 'border-transparent text-gray-500'"
          @click="activeTab = 'received'"
        >
          我收到的 {{ receivedPending.length > 0 ? `(${receivedPending.length})` : '' }}
        </button>
        <button
          class="text-sm font-medium pb-2 border-b-2 transition-colors"
          :class="activeTab === 'sent' ? 'border-primary-500 text-primary-600' : 'border-transparent text-gray-500'"
          @click="activeTab = 'sent'"
        >
          我发出的 {{ sentPending.length > 0 ? `(${sentPending.length})` : '' }}
        </button>
      </div>
    </header>

    <!-- Content -->
    <main class="flex-1 overflow-y-auto p-4">
      <!-- Received Requests -->
      <div v-if="activeTab === 'received'">
        <!-- Pending -->
        <div class="mb-6">
          <h2 class="text-sm font-medium text-gray-500 mb-3">
            待处理 {{ receivedPending.length > 0 ? `(${receivedPending.length})` : '' }}
          </h2>

          <div class="space-y-2">
            <div
              v-for="request in receivedPending"
              :key="request.id"
              class="bg-white rounded-xl p-4"
            >
              <div class="flex items-start gap-3">
                <img
                  v-if="request.fromAvatar"
                  :src="request.fromAvatar"
                  class="w-12 h-12 rounded-full flex-shrink-0"
                  :alt="request.fromNickname"
                />
                <div
                  v-else
                  class="w-12 h-12 rounded-full bg-gradient-to-br from-primary-400 to-primary-600 flex items-center justify-center text-white font-medium flex-shrink-0"
                >
                  {{ request.fromNickname?.charAt(0).toUpperCase() || '?' }}
                </div>

                <div class="flex-1 min-w-0">
                  <div class="flex items-center justify-between">
                    <span class="text-sm font-medium text-gray-800">{{ request.fromNickname || '用户' }}</span>
                    <span class="text-xs text-gray-400">{{ formatTime(request.createdAt) }}</span>
                  </div>
                </div>
              </div>

              <div class="flex gap-2 mt-3">
                <button
                  class="flex-1 py-2 text-sm text-gray-600 bg-gray-100 rounded-lg hover:bg-gray-200"
                  @click="rejectRequest(request.id)"
                >
                  拒绝
                </button>
                <button
                  class="flex-1 py-2 text-sm text-white bg-primary-500 rounded-lg hover:bg-primary-600"
                  @click="acceptRequest(request.id)"
                >
                  同意
                </button>
              </div>
            </div>

            <div
              v-if="receivedPending.length === 0"
              class="flex flex-col items-center justify-center py-8 text-gray-400"
            >
              <svg class="w-10 h-10 mb-2" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="1.5" d="M9 12l2 2 4-4m6 2a9 9 0 11-18 0 9 9 0 0118 0z"></path>
              </svg>
              <p class="text-sm">暂无待处理请求</p>
            </div>
          </div>
        </div>

        <!-- Processed -->
        <div v-if="receivedProcessed.length > 0">
          <h2 class="text-sm font-medium text-gray-500 mb-3">已处理</h2>

          <div class="space-y-2">
            <div
              v-for="request in receivedProcessed"
              :key="request.id"
              class="bg-white rounded-xl p-4"
            >
              <div class="flex items-center gap-3">
                <img
                  v-if="request.fromAvatar"
                  :src="request.fromAvatar"
                  class="w-10 h-10 rounded-full flex-shrink-0"
                  :alt="request.fromNickname"
                />
                <div
                  v-else
                  class="w-10 h-10 rounded-full bg-gray-200 flex items-center justify-center text-gray-500 font-medium flex-shrink-0"
                >
                  {{ request.fromNickname?.charAt(0).toUpperCase() || '?' }}
                </div>

                <div class="flex-1 min-w-0">
                  <span class="text-sm text-gray-800">{{ request.fromNickname || '用户' }}</span>
                </div>

                <span
                  class="text-xs px-2 py-1 rounded"
                  :class="getStatusColor(request.status)"
                >
                  {{ getStatusText(request.status) }}
                </span>
              </div>
            </div>
          </div>
        </div>
      </div>

      <!-- Sent Requests -->
      <div v-if="activeTab === 'sent'">
        <!-- Pending -->
        <div class="mb-6">
          <h2 class="text-sm font-medium text-gray-500 mb-3">
            等待对方确认 {{ sentPending.length > 0 ? `(${sentPending.length})` : '' }}
          </h2>

          <div class="space-y-2">
            <div
              v-for="request in sentPending"
              :key="request.id"
              class="bg-white rounded-xl p-4"
            >
              <div class="flex items-center gap-3">
                <img
                  v-if="request.toAvatar"
                  :src="request.toAvatar"
                  class="w-10 h-10 rounded-full flex-shrink-0"
                  :alt="request.toNickname"
                />
                <div
                  v-else
                  class="w-10 h-10 rounded-full bg-gray-200 flex items-center justify-center text-gray-500 font-medium flex-shrink-0"
                >
                  {{ request.toNickname?.charAt(0).toUpperCase() || '?' }}
                </div>

                <div class="flex-1 min-w-0">
                  <span class="text-sm text-gray-800">{{ request.toNickname || '用户' }}</span>
                  <p class="text-xs text-gray-400 mt-1">{{ formatTime(request.createdAt) }}</p>
                </div>

                <span class="text-xs px-2 py-1 rounded bg-yellow-100 text-yellow-600">
                  等待对方验证
                </span>
              </div>
            </div>

            <div
              v-if="sentPending.length === 0"
              class="flex flex-col items-center justify-center py-8 text-gray-400"
            >
              <svg class="w-10 h-10 mb-2" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="1.5" d="M12 8v4l3 3m6-3a9 9 0 11-18 0 9 9 0 0118 0z"></path>
              </svg>
              <p class="text-sm">暂无发出请求</p>
            </div>
          </div>
        </div>

        <!-- Processed -->
        <div v-if="sentProcessed.length > 0">
          <h2 class="text-sm font-medium text-gray-500 mb-3">已处理</h2>

          <div class="space-y-2">
            <div
              v-for="request in sentProcessed"
              :key="request.id"
              class="bg-white rounded-xl p-4"
            >
              <div class="flex items-center gap-3">
                <img
                  v-if="request.toAvatar"
                  :src="request.toAvatar"
                  class="w-10 h-10 rounded-full flex-shrink-0"
                  :alt="request.toNickname"
                />
                <div
                  v-else
                  class="w-10 h-10 rounded-full bg-gray-200 flex items-center justify-center text-gray-500 font-medium flex-shrink-0"
                >
                  {{ request.toNickname?.charAt(0).toUpperCase() || '?' }}
                </div>

                <div class="flex-1 min-w-0">
                  <span class="text-sm text-gray-800">{{ request.toNickname || '用户' }}</span>
                  <p class="text-xs text-gray-400 mt-1">{{ formatTime(request.createdAt) }}</p>
                </div>

                <span
                  class="text-xs px-2 py-1 rounded"
                  :class="getStatusColor(request.status)"
                >
                  {{ getStatusText(request.status) }}
                </span>
              </div>
            </div>
          </div>
        </div>
      </div>
    </main>
  </div>
</template>