<script setup lang="ts">
import { computed, ref } from 'vue';
import dayjs from 'dayjs';
import relativeTime from 'dayjs/plugin/relativeTime';
import zhCn from 'dayjs/locale/zh-cn';
import type { Message } from '@/types/chat';
import { sanitizeHtml, createSafeHtml } from '@/utils/sanitize';
import { getFullFileUrl } from '@/utils/api';

dayjs.extend(relativeTime);
dayjs.locale(zhCn);

interface Props {
  message: Message;
  participants?: { userId: string; nickname: string; avatar?: string }[];
}

const props = defineProps<Props>();

const emit = defineEmits<{
  (e: 'retry', messageId: string): void;
  (e: 'clickUser', userId: string): void;
}>();

// Get current user ID
const currentUserId = computed(() => {
  return sessionStorage.getItem('chat_userId') || '';
});

// Get sender info from participants
const senderInfo = computed(() => {
  if (!props.participants) return null;
  return props.participants.find(p => p.userId === props.message.senderId);
});

// Check if message is from current user
const isOwn = computed(() => {
  const userId = currentUserId.value;
  return props.message.senderId === userId || props.message.senderId === 'current_user';
});

// Current user nickname
const currentUserNickname = computed(() => {
  return sessionStorage.getItem('chat_nickname') || '我';
});

// Sender nickname
const senderNickname = computed(() => {
  if (isOwn.value) {
    return currentUserNickname.value;
  }
  const sender = senderInfo.value;
  if (sender) return sender.nickname;
  // Fallback to user ID
  return `用户 ${props.message.senderId.slice(0, 8)}`;
});

// Sender avatar
const senderAvatar = computed(() => {
  if (isOwn.value) {
    return undefined; // Show default avatar for self
  }
  const sender = senderInfo.value;
  return sender?.avatar;
});

// Format time
const formattedTime = computed(() => {
  const msgTime = dayjs(props.message.createdAt);
  const now = dayjs();

  // If today, show time
  if (msgTime.isSame(now, 'day')) {
    return msgTime.format('HH:mm');
  }

  // If yesterday, show "昨天 HH:mm"
  if (msgTime.isSame(now.subtract(1, 'day'), 'day')) {
    return `昨天 ${msgTime.format('HH:mm')}`;
  }

  // If this year, show month and day
  if (msgTime.isSame(now, 'year')) {
    return msgTime.format('MM月DD日 HH:mm');
  }

  // Otherwise show full date
  return msgTime.format('YYYY年MM月DD日 HH:mm');
});

// Status icon
const statusIcon = computed(() => {
  switch (props.message.status) {
    case 'sending':
      return '⏳';
    case 'sent':
      return '✓';
    case 'delivered':
      return '✓✓';
    case 'read':
      return '✓✓';
    case 'failed':
      return '❌';
    default:
      return '';
  }
});

// Status color
const statusColor = computed(() => {
  switch (props.message.status) {
    case 'sending':
      return 'text-gray-400';
    case 'sent':
      return 'text-gray-400';
    case 'delivered':
      return 'text-primary-500';
    case 'read':
      return 'text-primary-500';
    case 'failed':
      return 'text-red-500';
    default:
      return 'text-gray-400';
  }
});

// 是否为图片消息
const isImageMessage = computed(() => {
  return props.message.contentType === 'image';
});

// 是否为语音消息
const isAudioMessage = computed(() => {
  return props.message.contentType === 'audio';
});

// 是否为文件消息
const isFileMessage = computed(() => {
  return props.message.contentType === 'file';
});

// 图片 URL (拼接完整地址)
const imageUrl = computed(() => {
  if (isImageMessage.value) {
    return getFullFileUrl(props.message.content);
  }
  return '';
});

// 语音 URL (拼接完整地址)
const audioUrl = computed(() => {
  if (isAudioMessage.value) {
    return getFullFileUrl(props.message.content);
  }
  return '';
});

// 语音时长 (从 metadata 获取，或估算)
// const audioDuration = computed(() => {
//   if (isAudioMessage.value && props.message.metadata?.duration) {
//     return props.message.metadata.duration as number;
//   }
//   return 0;
// });

// 文件名
const fileName = computed(() => {
  if (isFileMessage.value) {
    const url = props.message.content;
    const parts = url.split('/');
    return parts[parts.length - 1] || '文件';
  }
  return '';
});

// Rendered content (sanitized)
const renderedContent = computed(() => {
  if (props.message.contentType === 'text') {
    return createSafeHtml(props.message.content, true);
  }

  // For other content types, return plain text
  return sanitizeHtml(props.message.content);
});

// Handle retry
function handleRetry(): void {
  if (props.message.status === 'failed') {
    emit('retry', props.message.id);
  }
}

// 图片预览状态
const showImagePreview = ref(false);
const previewImageUrl = ref('');

// 图片点击放大 - 弹窗预览
function handleImageClick(): void {
  if (imageUrl.value) {
    previewImageUrl.value = imageUrl.value;
    showImagePreview.value = true;
  }
}

// 关闭图片预览
function closeImagePreview(): void {
  showImagePreview.value = false;
  previewImageUrl.value = '';
}

// 下载图片
function downloadImage(): void {
  const link = document.createElement('a');
  link.href = previewImageUrl.value;
  link.download = 'image';
  link.click();
}

// 图片加载失败处理
function handleImageError(event: Event): void {
  const img = event.target as HTMLImageElement;
  img.src = 'data:image/svg+xml,%3Csvg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 100 100"%3E%3Crect fill="%23f3f4f6" width="100" height="100"/%3E%3Ctext x="50" y="50" text-anchor="middle" dy=".3em" fill="%239ca3af" font-size="14"%3E图片加载失败%3C/text%3E%3C/svg%3E';
}

// 格式化文件大小
function formatFileSize(bytes: number): string {
  if (bytes < 1024) return bytes + ' B';
  if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + ' KB';
  return (bytes / (1024 * 1024)).toFixed(1) + ' MB';
}

// Get content type icon
const contentTypeIcon = computed(() => {
  switch (props.message.contentType) {
    case 'image':
      return '🖼️';
    case 'file':
      return '📎';
    case 'audio':
      return '🎤';
    case 'video':
      return '🎬';
    default:
      return '';
  }
});
</script>

<template>
  <div
    class="group px-4 py-2 hover:bg-gray-50/50 transition-colors"
    :class="{
      'flex flex-row-reverse': isOwn,
      'flex': !isOwn
    }"
  >
    <!-- Avatar -->
    <img
      v-if="senderAvatar && !isOwn"
      :src="senderAvatar"
      class="flex-shrink-0 w-10 h-10 rounded-full cursor-pointer hover:opacity-80"
      :alt="senderNickname"
      @click="emit('clickUser', message.senderId)"
    />
    <div
      v-else
      class="flex-shrink-0 w-10 h-10 rounded-full flex items-center justify-center text-lg font-medium cursor-pointer"
      :class="{
        'ml-3 bg-gray-200 text-gray-500 hover:opacity-80': !isOwn,
        'mr-3 bg-primary-500 text-white': isOwn
      }"
      @click="!isOwn && emit('clickUser', message.senderId)"
    >
      {{ isOwn ? '我' : senderNickname?.charAt(0).toUpperCase() || '?' }}
    </div>

    <!-- Message content -->
    <div
      class="flex flex-col max-w-[70%]"
      :class="{
        'items-end': isOwn,
        'items-start': !isOwn
      }"
    >
      <!-- Sender name (for group chats) -->
      <span
        v-if="!isOwn"
        class="text-xs text-gray-500 mb-1"
      >
        {{ senderNickname }}
      </span>

      <!-- Bubble -->
      <div
        class="relative px-4 py-2 rounded-2xl"
        :class="{
          'bg-primary-500 text-white': isOwn,
          'bg-white border border-gray-200 text-gray-800': !isOwn
        }"
      >
        <!-- 图片消息 -->
        <div v-if="isImageMessage" class="max-w-[220px]">
          <div class="relative group" @click="handleImageClick">
            <img
              :src="imageUrl"
              alt="图片"
              class="rounded-xl max-w-full max-h-[200px] object-cover shadow-sm transition-transform duration-200 group-hover:scale-[1.02]"
              loading="lazy"
              @error="handleImageError"
            />
            <!-- 悬停放大图标 -->
            <div class="absolute inset-0 flex items-center justify-center opacity-0 group-hover:opacity-100 bg-black/20 rounded-xl transition-opacity duration-200">
              <svg class="w-8 h-8 text-white drop-shadow-lg" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M21 21l-6-6m2-5a7 7 0 11-14 0 7 7 0 0114 0zM10 7v3m0 0v3m0-3h3m-3 0H7" />
              </svg>
            </div>
          </div>
          <!-- 文件大小 -->
          <div v-if="message.metadata?.size" class="text-xs text-gray-400 mt-1 text-right">
            {{ formatFileSize(message.metadata.size as number) }}
          </div>
        </div>

        <!-- 语音消息 -->
        <div v-else-if="isAudioMessage" class="flex items-center gap-2 min-w-[120px]">
          <svg class="w-5 h-5 flex-shrink-0" fill="currentColor" viewBox="0 0 24 24">
            <path d="M12 14c1.66 0 3-1.34 3-3V5c0-1.66-1.34-3-3-3S9 3.34 9 5v6c0 1.66 1.34 3 3 3zm-1 1.93c-3.94-.49-7-3.85-7-7.93h2c0 3.31 2.69 6 6 6s6-2.69 6-6h2c0 4.08-2.06 7.44-5 7.93V19h-2v-3.07z"/>
          </svg>
          <audio :src="audioUrl" controls class="h-8 max-w-[150px]" />
        </div>

        <!-- 文件消息 -->
        <div v-else-if="isFileMessage" class="flex items-center gap-2">
          <svg class="w-8 h-8 text-gray-400" fill="currentColor" viewBox="0 0 24 24">
            <path d="M14 2H6c-1.1 0-2 .9-2 2v16c0 1.1.9 2 2 2h12c1.1 0 2-.9 2-2V8l-6-6zm4 18H6V4h7v5h5v11z"/>
          </svg>
          <span class="text-sm truncate max-w-[150px]">{{ fileName }}</span>
        </div>

        <!-- 文本消息 -->
        <div v-else class="flex items-center">
          <!-- Content type icon -->
          <span v-if="contentTypeIcon" class="mr-2">{{ contentTypeIcon }}</span>
          <!-- Message content -->
          <p
            class="text-sm break-words whitespace-pre-wrap"
            v-html="renderedContent"
          ></p>
        </div>

        <!-- Failed indicator -->
        <button
          v-if="message.status === 'failed'"
          class="absolute -bottom-6 right-0 text-xs text-red-500 hover:text-red-700 flex items-center gap-1"
          @click="handleRetry"
        >
          <span>发送失败</span>
          <span class="underline">重试</span>
        </button>
      </div>

      <!-- Time and status -->
      <div
        class="flex items-center gap-1 mt-1"
        :class="{
          'justify-end': isOwn,
          'justify-start': !isOwn
        }"
      >
        <span class="text-xs text-gray-400">{{ formattedTime }}</span>
        <span
          v-if="isOwn"
          class="text-xs"
          :class="statusColor"
          :title="message.status"
        >
          {{ statusIcon }}
        </span>
      </div>
    </div>
  </div>

  <!-- 图片预览弹窗 -->
  <div v-if="showImagePreview" class="image-preview-modal">
    <div
      class="fixed inset-0 z-50 flex items-center justify-center bg-black/90"
      @click="closeImagePreview"
    >
      <!-- 工具栏 -->
      <div class="absolute top-4 right-4 flex items-center gap-2 z-10">
        <button
          class="p-2 bg-white/10 hover:bg-white/20 rounded-lg text-white transition-colors"
          title="下载"
          @click.stop="downloadImage"
        >
          <svg class="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M4 16v1a3 3 0 003 3h10a3 3 0 003-3v-1m-4-4l-4 4m0 0l-4-4m4 4V4" />
          </svg>
        </button>
        <button
          class="p-2 bg-white/10 hover:bg-white/20 rounded-lg text-white transition-colors"
          title="关闭"
          @click.stop="closeImagePreview"
        >
          <svg class="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M6 18L18 6M6 6l12 12" />
          </svg>
        </button>
      </div>

      <!-- 图片 -->
      <img
        :src="previewImageUrl"
        class="max-w-[90vw] max-h-[90vh] object-contain"
        @click.stop
      />
    </div>
  </div>
</template>

<style scoped>
.image-preview-modal {
  position: fixed;
  top: 0;
  left: 0;
  width: 100vw;
  height: 100vh;
  z-index: 9999;
}

/* Prevent XSS even with v-html */
:deep(a) {
  color: inherit;
  text-decoration: underline;
}

:deep(a:hover) {
  opacity: 0.8;
}

:deep(p) {
  margin: 0;
}

:deep(code) {
  background: rgba(0, 0, 0, 0.1);
  padding: 2px 4px;
  border-radius: 3px;
  font-family: monospace;
}

:deep(pre) {
  background: rgba(0, 0, 0, 0.1);
  padding: 8px;
  border-radius: 4px;
  overflow-x: auto;
}
</style>