<script setup lang="ts">
import { ref, onMounted, onUnmounted, nextTick } from 'vue';
import { useRouter } from 'vue-router';
import { ElMessage } from 'element-plus';
import { serviceApi, uploadApi, getFullFileUrl } from '@/utils/api';
import { useWebSocket } from '@/composables/useWebSocket';

const router = useRouter();
const ws = useWebSocket();

interface ServiceStatus {
  hasAvailableService: boolean;
  onlineCount: number;
  waitingCount: number;
  availableServices: Array<{ id: string; nickname: string; avatar: string; availableSlots: number }>;
}

interface QueueStatus {
  status: 'offline' | 'waiting' | 'chatting';
  position?: number;
  queueSize?: number;
  estimatedWait?: string;
  sessionId?: string;
  serviceId?: string;
  serviceName?: string;
}

interface Message {
  id: string;
  senderId: string;
  content: string;
  contentType: string;
  status: string;
  createdAt: number;
}

const serviceStatus = ref<ServiceStatus | null>(null);
const queueStatus = ref<QueueStatus | null>(null);
const messages = ref<Message[]>([]);
const inputMessage = ref('');
const isLoading = ref(false);
const isSending = ref(false);
const messagesContainer = ref<HTMLElement | null>(null);
let pollInterval: number | null = null;

const showRatingDialog = ref(false);
const ratingValue = ref(5);
const ratingComment = ref('');
const serviceTyping = ref(false);
const previewImageUrl = ref<string | null>(null);

const showHistory = ref(false);
const historyLoading = ref(false);
const historySessions = ref<any[]>([]);

const historyDetailSession = ref<any>(null);
const historyDetailMessages = ref<any[]>([]);
const historyDetailLoading = ref(false);

const currentUserId = sessionStorage.getItem('chat_userId') || '';

async function loadServiceStatus() {
  try {
    serviceStatus.value = await serviceApi.getStatus();
  } catch (e) {
    console.error('获取客服状态失败:', e);
  }
}

async function loadMessages() {
  try {
    const result = await serviceApi.getConversation(50, 0);
    messages.value = result.messages || [];
    scrollToBottom();
    ws.sendServiceRead();
  } catch (e) {
    console.error('获取消息失败:', e);
  }
}

async function loadHistory() {
  historyLoading.value = true;
  try {
    const result = await serviceApi.getHistory(20, 0);
    historySessions.value = result.sessions || [];
  } catch (e) {
    console.error('获取历史会话失败:', e);
  } finally {
    historyLoading.value = false;
  }
}

function openHistory() {
  showHistory.value = true;
  loadHistory();
}

function closeHistory() {
  showHistory.value = false;
}

function getStatusLabel(status: string) {
  switch (status) {
    case 'chatting': return '进行中';
    case 'finished': return '已结束';
    case 'waiting': return '排队中';
    default: return status;
  }
}

async function viewHistorySession(session: any) {
  historyDetailLoading.value = true;
  historyDetailSession.value = session;
  try {
    const result = await serviceApi.getHistoryMessages(session.id, 50, 0);
    if (result.success) {
      historyDetailMessages.value = result.messages || [];
    } else {
      ElMessage.error(result.message || '加载失败');
    }
  } catch (e) {
    console.error('获取历史消息失败:', e);
    ElMessage.error('加载失败');
  } finally {
    historyDetailLoading.value = false;
  }
}

function closeHistoryDetail() {
  historyDetailSession.value = null;
  historyDetailMessages.value = [];
}

async function joinQueue() {
  isLoading.value = true;
  try {
    const userNickname = sessionStorage.getItem('chat_nickname') || '用户';
    const userAvatar = '';
    const result = await serviceApi.joinQueue(userNickname, userAvatar);
    if (result) {
      queueStatus.value = {
        status: result.status || 'waiting',
        position: result.position,
        queueSize: result.queueSize,
        estimatedWait: result.estimatedWait
      };
    }
  } catch (e: any) {
    console.error('加入队列失败:', e.message);
  } finally {
    isLoading.value = false;
  }
}

async function leaveQueue() {
  try {
    await serviceApi.leaveQueue();
    // 状态通过 WebSocket service_queue_update 推送
  } catch (e) {
    console.error('离开队列失败:', e);
  }
}

async function sendMessage() {
  if (!inputMessage.value.trim() || isSending.value) return;

  if (!ws.isConnected) {
    ElMessage.warning('网络未连接，消息将稍后发送')
  }

  isSending.value = true;
  try {
    ws.sendServiceMessage(inputMessage.value.trim());
    inputMessage.value = '';
  } catch (e) {
    console.error('发送消息失败:', e);
    ElMessage.error('发送失败，请重试')
  } finally {
    isSending.value = false;
  }
}

const imageInputRef = ref<HTMLInputElement | null>(null);

function selectImage() {
  imageInputRef.value?.click();
}

async function handleImageUpload(event: Event) {
  const target = event.target as HTMLInputElement;
  const file = target.files?.[0];
  if (!file) return;

  if (!file.type.startsWith('image/')) {
    ElMessage.warning('请选择图片文件');
    return;
  }

  if (file.size > 10 * 1024 * 1024) {
    ElMessage.warning('图片大小不能超过10MB');
    return;
  }

  isSending.value = true;
  try {
    const result = await uploadApi.uploadImage(file);
    const fullUrl = getFullFileUrl(result.url);
    ws.sendServiceMessage(fullUrl, 'image');
  } catch (e) {
    console.error('上传图片失败:', e);
    ElMessage.error('上传失败，请重试');
  } finally {
    isSending.value = false;
    target.value = '';
  }
}

let typingTimer: number | null = null;
function handleInput() {
  if (typingTimer) clearTimeout(typingTimer);
  typingTimer = window.setTimeout(() => {
    // 用户停止输入，可以通知客服
  }, 1000);
}

function handleMessageClick() {
  if (queueStatus.value?.status === 'chatting') {
    ws.sendServiceRead();
  }
}

async function endSession() {
  if (!confirm('确定要结束会话吗？')) return;
  try {
    ws.sendServiceEnd();
    showRatingDialog.value = true;
  } catch (e) {
    console.error('结束会话失败:', e);
  }
}

async function submitRating() {
  try {
    await serviceApi.rateSession(ratingValue.value, ratingComment.value || undefined);
    showRatingDialog.value = false;
    queueStatus.value = null;
    messages.value = [];
    ratingValue.value = 5;
    ratingComment.value = '';
  } catch (e) {
    console.error('提交评价失败:', e);
  }
}

function skipRating() {
  showRatingDialog.value = false;
  queueStatus.value = null;
  messages.value = [];
}

function scrollToBottom() {
  nextTick(() => {
    if (messagesContainer.value) {
      messagesContainer.value.scrollTop = messagesContainer.value.scrollHeight;
    }
  });
}

function formatTime(timestamp: number): string {
  return new Date(timestamp).toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' });
}

function previewImage(url: string) {
  previewImageUrl.value = url;
}

function closePreview() {
  previewImageUrl.value = null;
}

function getStatusText(): string {
  if (!serviceStatus.value) return '加载中...';
  if (!serviceStatus.value.hasAvailableService) return '客服离线';
  if (queueStatus.value?.status === 'waiting') return '排队中';
  if (queueStatus.value?.status === 'chatting') return '会话中';
  return '在线';
}

onMounted(async () => {
  // 确保 WebSocket 已连接
  if (!ws.isConnected) {
    const token = sessionStorage.getItem('chat_token');
    if (token) {
      ws.connect(token);
    }
  }

  await loadServiceStatus();

  // 检查是否有进行中的会话，有则加载最近消息
  try {
    const status = await serviceApi.getQueueStatus()
    if (status.status === 'waiting' || status.status === 'chatting') {
      queueStatus.value = status
      if (status.status === 'chatting') {
        loadMessages()
      }
    }
  } catch (e) {
    console.error('检查会话状态失败:', e)
  }

  // 轮询 (保留作为备用)
  pollInterval = window.setInterval(async () => {
    // 不再自动标记已读，改为用户操作触发
  }, 3000);

  // WebSocket 重连后自动恢复状态
  ws.on('open', async () => {
    try {
      const status = await serviceApi.getQueueStatus()
      if (status.status === 'waiting' || status.status === 'chatting') {
        queueStatus.value = status
        if (status.status === 'chatting') {
          loadMessages()
        }
      }
    } catch (e) {
      console.error('恢复会话状态失败:', e)
    }
  });

  // WebSocket 事件处理
  ws.on('service_session_started', ({ payload }: any) => {
    const { sessionId, serviceId, serviceName } = payload.payload || {};
    queueStatus.value = {
      status: 'chatting',
      sessionId,
      serviceId,
      serviceName
    };
    loadMessages();
    ws.sendServiceRead();
  });

  ws.on('service_session_ended', () => {
    queueStatus.value = { status: 'offline' };
    messages.value = [];
    showRatingDialog.value = true;
  });

  ws.on('service_new_message', ({ payload }: any) => {
    const { message } = payload.payload || {};
    messages.value.push(message);
    scrollToBottom();
    ws.sendServiceRead();
  });

  ws.on('service_typing', () => {
    serviceTyping.value = true;
    setTimeout(() => {
      serviceTyping.value = false;
    }, 3000);
  });

  ws.on('service_session_transferred', ({ payload }: any) => {
    const { serviceId, serviceName } = payload.payload || {};
    queueStatus.value = {
      status: 'chatting',
      serviceId,
      serviceName
    };
  });

  ws.on('service_queue_update', ({ payload }: any) => {
    const { status, position, sessionId, serviceId, serviceName } = payload.payload || {};
    queueStatus.value = {
      status,
      position,
      sessionId,
      serviceId,
      serviceName
    };
  });

  ws.on('service_message_sent', ({ payload }: any) => {
    const { message } = payload.payload || {};
    if (message) {
      messages.value.push(message);
      scrollToBottom();
      ws.sendServiceRead();
    }
  });
});

onUnmounted(() => {
  if (pollInterval) {
    clearInterval(pollInterval);
  }
  // 清理 WebSocket 事件监听
  ws.off('service_session_started', null as any);
  ws.off('service_session_ended', null as any);
  ws.off('service_new_message', null as any);
  ws.off('service_typing', null as any);
  ws.off('service_session_transferred', null as any);
  ws.off('service_queue_update', null as any);
  ws.off('service_message_sent', null as any);
});
</script>

<template>
  <div class="min-h-screen bg-gray-50 flex flex-col">
    <!-- Header -->
    <div class="bg-white shadow-sm">
      <div class="max-w-2xl mx-auto px-4 py-4 flex items-center justify-between">
        <button class="text-gray-600 hover:text-gray-800" @click="router.push('/')">
          <svg class="w-6 h-6" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M15 19l-7-7 7-7" />
          </svg>
        </button>
        <h1 class="text-lg font-semibold text-gray-800">联系客服</h1>
        <button
          @click="openHistory"
          class="text-sm text-primary-500 hover:text-primary-600"
        >
          历史记录
        </button>
      </div>
    </div>

    <!-- Content -->
    <div class="flex-1 max-w-2xl mx-auto w-full p-4">
      <!-- 未在会话中 -->
      <div v-if="!queueStatus || queueStatus.status === 'offline'" class="bg-white rounded-lg shadow p-6">
        <div class="text-center mb-6">
          <div class="inline-flex items-center justify-center w-16 h-16 bg-primary-100 rounded-full mb-4">
            <svg class="w-8 h-8 text-primary-600" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M18.364 5.636l-3.536 3.536m0 5.656l3.536 3.536M9.172 9.172L5.636 5.636m3.536 9.192l-3.536 3.536M21 12a9 9 0 11-18 0 9 9 0 0118 0zm-5 0a4 4 0 11-8 0 4 4 0 018 0z" />
            </svg>
          </div>
          <h2 class="text-xl font-semibold text-gray-800 mb-2">{{ getStatusText() }}</h2>
          <p class="text-gray-500 text-sm">
            <span v-if="serviceStatus?.onlineCount">当前有 {{ serviceStatus.onlineCount }} 位客服在线</span>
            <span v-else>暂无客服在线</span>
          </p>
        </div>

        <!-- 等待人数 -->
        <div v-if="serviceStatus?.waitingCount" class="text-center text-sm text-gray-500 mb-4">
          当前等待人数: {{ serviceStatus.waitingCount }} 人
        </div>

        <!-- 联系方式 -->
        <div v-if="!serviceStatus?.hasAvailableService" class="border-t pt-4 mt-4">
          <p class="text-sm text-gray-500 text-center mb-3">客服不在线，请选择其他联系方式</p>
          <div class="flex justify-center gap-4 text-sm">
            <span class="text-gray-600">电话: 400-xxx-xxxx</span>
            <span class="text-gray-600">邮箱: support@example.com</span>
          </div>
        </div>

        <!-- 联系按钮 -->
        <div v-else class="text-center">
          <button
            @click="joinQueue"
            :disabled="isLoading"
            class="px-8 py-3 bg-primary-500 text-white font-medium rounded-lg hover:bg-primary-600 disabled:opacity-50"
          >
            {{ isLoading ? '加入中...' : '联系客服' }}
          </button>
        </div>
      </div>

      <!-- 排队中 -->
      <div v-else-if="queueStatus.status === 'waiting'" class="bg-white rounded-lg shadow p-6">
        <div class="text-center">
          <div class="inline-flex items-center justify-center w-16 h-16 bg-yellow-100 rounded-full mb-4">
            <svg class="w-8 h-8 text-yellow-600 animate-pulse" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 8v4l3 3m6-3a9 9 0 11-18 0 9 9 0 0118 0z" />
            </svg>
          </div>
          <h2 class="text-xl font-semibold text-gray-800 mb-2">排队中</h2>
          <p class="text-gray-500 mb-2">当前排名: 第 {{ queueStatus.position }} 位</p>
          <p class="text-gray-500 text-sm mb-4">预计等待: {{ queueStatus.estimatedWait }}</p>

          <!-- 取消排队按钮已禁用，由心跳超时机制处理 -->
        </div>
      </div>

      <!-- 聊天中 -->
      <div v-else-if="queueStatus.status === 'chatting'" class="bg-white rounded-lg shadow flex flex-col h-[calc(100vh-120px)]">
        <!-- 客服信息 -->
        <div class="px-4 py-3 border-b flex items-center justify-between">
          <div class="flex items-center gap-3">
            <div class="w-10 h-10 rounded-full bg-primary-500 flex items-center justify-center text-white font-medium">
              客
            </div>
            <div>
              <p class="font-medium text-gray-800">{{ queueStatus.serviceName || '客服' }}</p>
              <p class="text-xs text-green-500">在线</p>
            </div>
          </div>
          <button
            @click="endSession"
            class="px-4 py-2 text-sm border border-gray-300 text-gray-700 rounded-lg hover:bg-gray-50"
          >
            结束会话
          </button>
        </div>

        <!-- 消息列表 -->
        <div ref="messagesContainer" class="flex-1 overflow-y-auto p-4 space-y-3" @click="handleMessageClick">
          <div
            v-for="msg in messages"
            :key="msg.id"
            class="flex"
            :class="msg.senderId === currentUserId ? 'justify-end' : 'justify-start'"
          >
            <div
              class="max-w-[70%] rounded-lg"
              :class="msg.senderId === currentUserId
                ? 'bg-primary-500 text-white rounded-br-none'
                : 'bg-gray-100 text-gray-800 rounded-bl-none'"
            >
              <!-- 文本消息 -->
              <template v-if="msg.contentType === 'text' || !msg.contentType">
                <p class="px-4 py-2">{{ msg.content }}</p>
              </template>
              <!-- 图片消息 -->
              <template v-else-if="msg.contentType === 'image'">
                <img
                  :src="getFullFileUrl(msg.content)"
                  class="max-w-[200px] max-h-[180px] rounded-lg cursor-pointer"
                  style="object-fit: cover;"
                  @click="previewImage(getFullFileUrl(msg.content))"
                />
              </template>
              <!-- 其他类型 -->
              <template v-else>
                <p class="px-4 py-2">{{ msg.content }}</p>
              </template>
              <p class="text-xs px-4 pb-2" :class="msg.senderId === currentUserId ? 'text-white/70' : 'text-gray-400'">
                {{ formatTime(msg.createdAt) }}
              </p>
            </div>
          </div>

          <div v-if="messages.length === 0" class="text-center text-gray-400 py-8">
            暂无消息，开始对话吧
          </div>
          <div v-if="serviceTyping" class="text-sm text-gray-400 py-2">
            客服正在输入...
          </div>
        </div>

        <!-- 输入框 -->
        <div class="px-4 py-3 border-t">
          <div class="flex gap-2 items-center">
            <input
              ref="imageInputRef"
              type="file"
              accept="image/*"
              class="hidden"
              @change="handleImageUpload"
            />
            <button
              @click="selectImage"
              :disabled="isSending"
              class="p-2 text-gray-500 hover:text-primary-500 disabled:opacity-50"
              title="发送图片"
            >
              <svg class="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M4 16l4.586-4.586a2 2 0 012.828 0L16 16m-2-2l1.586-1.586a2 2 0 012.828 0L20 14m-6-6h.01M6 20h12a2 2 0 002-2V6a2 2 0 00-2-2H6a2 2 0 00-2 2v12a2 2 0 002 2z"></path>
              </svg>
            </button>
            <input
              v-model="inputMessage"
              type="text"
              placeholder="输入消息..."
              class="flex-1 px-4 py-2 border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-primary-500"
              @keydown.enter="sendMessage"
              @input="handleInput"
            />
            <button
              @click="sendMessage"
              :disabled="isSending || !inputMessage.trim()"
              class="px-6 py-2 bg-primary-500 text-white rounded-lg hover:bg-primary-600 disabled:opacity-50"
            >
              发送
            </button>
          </div>
        </div>
      </div>
    </div>

    <!-- 图片预览弹窗 -->
    <div
      v-if="previewImageUrl"
      class="fixed inset-0 bg-black/80 flex items-center justify-center z-50"
      @click="closePreview"
    >
      <img
        :src="previewImageUrl"
        class="max-w-[90vw] max-h-[90vh] object-contain"
        @click.stop
      />
    </div>

<!-- 评价弹窗 -->
    <div v-if="showRatingDialog" class="fixed inset-0 bg-black/50 flex items-center justify-center z-50">
      <div class="bg-white rounded-lg p-6 w-80">
        <h3 class="text-lg font-semibold mb-4">评价本次服务</h3>
        <div class="flex justify-center gap-1 mb-4">
          <button
            v-for="i in 5"
            :key="i"
            @click="ratingValue = i"
            class="text-2xl"
            :class="i <= ratingValue ? 'text-yellow-400' : 'text-gray-300'"
          >
            ★
          </button>
        </div>
        <textarea
          v-model="ratingComment"
          placeholder="评价备注（可选）"
          rows="2"
          class="w-full border rounded p-2 mb-4 resize-none"
        />
        <div class="flex justify-end gap-2">
          <button @click="skipRating" class="px-4 py-2 text-gray-600">跳过</button>
          <button @click="submitRating" class="px-4 py-2 bg-primary-500 text-white rounded-lg">提交</button>
        </div>
      </div>
    </div>

    <!-- 历史会话弹窗 -->
    <div v-if="showHistory" class="fixed inset-0 bg-black/50 flex items-center justify-center z-50" @click="closeHistory">
      <div class="bg-white rounded-lg w-full max-w-md max-h-[80vh] flex flex-col" @click.stop>
        <div class="px-4 py-3 border-b flex items-center justify-between">
          <h3 class="font-semibold">历史会话</h3>
          <button @click="closeHistory" class="text-gray-500 hover:text-gray-700">
            <svg class="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M6 18L18 6M6 6l12 12" />
            </svg>
          </button>
        </div>
        <div class="flex-1 overflow-y-auto p-4">
          <div v-if="historyLoading" class="text-center py-8 text-gray-500">加载中...</div>
          <div v-else-if="historySessions.length === 0" class="text-center py-8 text-gray-500">暂无历史会话</div>
          <div v-else class="space-y-3">
            <div
              v-for="session in historySessions"
              :key="session.id"
              class="border rounded-lg p-3 hover:bg-gray-50 cursor-pointer"
              @click="viewHistorySession(session)"
            >
              <div class="flex items-center justify-between mb-2">
                <span class="font-medium text-gray-800">{{ session.serviceName || '客服' }}</span>
                <span class="text-xs px-2 py-1 rounded"
                  :class="session.status === 'finished' ? 'bg-gray-100 text-gray-500' : 'bg-green-100 text-green-600'"
                >
                  {{ getStatusLabel(session.status) }}
                </span>
              </div>
              <div class="text-sm text-gray-500">
                {{ formatTime(session.createdAt) }}
              </div>
              <div v-if="session.rating" class="mt-1 text-yellow-500 text-sm">
                评分: {{ '★'.repeat(session.rating) }}
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- 历史会话详情弹窗 -->
    <div v-if="historyDetailSession" class="fixed inset-0 bg-black/50 flex items-center justify-center z-50" @click="closeHistoryDetail">
      <div class="bg-white rounded-lg w-full max-w-lg max-h-[80vh] flex flex-col" @click.stop>
        <div class="px-4 py-3 border-b flex items-center justify-between">
          <h3 class="font-semibold">{{ historyDetailSession.serviceName || '客服' }} 的会话</h3>
          <button @click="closeHistoryDetail" class="text-gray-500 hover:text-gray-700">
            <svg class="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M6 18L18 6M6 6l12 12" />
            </svg>
          </button>
        </div>
        <div class="flex-1 overflow-y-auto p-4">
          <div v-if="historyDetailLoading" class="text-center py-8 text-gray-500">加载中...</div>
          <div v-else-if="historyDetailMessages.length === 0" class="text-center py-8 text-gray-500">暂无消息</div>
          <div v-else class="space-y-3">
            <div
              v-for="msg in historyDetailMessages"
              :key="msg.id"
              class="flex"
              :class="msg.senderId === currentUserId ? 'justify-end' : 'justify-start'"
            >
              <div
                class="max-w-[70%] rounded-lg"
                :class="msg.senderId === currentUserId
                  ? 'bg-primary-500 text-white rounded-br-none'
                  : 'bg-gray-100 text-gray-800 rounded-bl-none'"
              >
                <template v-if="msg.contentType === 'text' || !msg.contentType">
                  <p class="px-4 py-2">{{ msg.content }}</p>
                </template>
                <template v-else-if="msg.contentType === 'image'">
                  <img
                    :src="getFullFileUrl(msg.content)"
                    class="max-w-[200px] max-h-[180px] rounded-lg cursor-pointer"
                    style="object-fit: cover;"
                    @click="previewImage(getFullFileUrl(msg.content))"
                  />
                </template>
                <p class="text-xs px-4 pb-2" :class="msg.senderId === currentUserId ? 'text-white/70' : 'text-gray-400'">
                  {{ formatTime(msg.createdAt) }}
                </p>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>