<script setup lang="ts">
import { ref, computed, watch, nextTick } from 'vue';
import { uploadApi } from '@/utils/api';

interface Props {
  conversationId: string | null;
  disabled?: boolean;
  placeholder?: string;
}

const props = withDefaults(defineProps<Props>(), {
  disabled: false,
  placeholder: '输入消息...'
});

const emit = defineEmits<{
  (e: 'send', content: string, conversationId: string, contentType: string): void;
}>();

// 上传中状态
const isUploading = ref(false);
const uploadType = ref('');

// Text input ref
const textareaRef = ref<HTMLTextAreaElement | null>(null);

// Input content
const content = ref('');

// Is sending (for optimistic UI)
const isSending = ref(false);

// Computed: can send
const canSend = computed(() => {
  return (
    content.value.trim().length > 0 &&
    props.conversationId !== null &&
    !props.disabled &&
    !isSending.value
  );
});

// Handle input
function handleInput(): void {
  // Auto-resize textarea
  adjustTextareaHeight();
}

// Adjust textarea height
function adjustTextareaHeight(): void {
  const textarea = textareaRef.value;
  if (!textarea) return;

  // Reset height
  textarea.style.height = 'auto';

  // Set new height (max 200px)
  const maxHeight = 200;
  const newHeight = Math.min(textarea.scrollHeight, maxHeight);
  textarea.style.height = `${newHeight}px`;

  // Show scrollbar if needed
  textarea.style.overflowY = newHeight >= maxHeight ? 'auto' : 'hidden';
}

// Handle keydown
function handleKeydown(event: KeyboardEvent): void {
  // Enter to send, Shift+Enter to wrap
  if (event.key === 'Enter' && !event.shiftKey) {
    event.preventDefault();
    handleSend();
  }
}

// 上传图片
async function handleUploadImage(): Promise<void> {
  if (!props.conversationId || isUploading.value) return;
  
  const input = document.createElement('input');
  input.type = 'file';
  input.accept = 'image/*';
  input.onchange = async (e) => {
    const file = (e.target as HTMLInputElement).files?.[0];
    if (!file) return;
    
    isUploading.value = true;
    uploadType.value = 'image';
    
    try {
      const result = await uploadApi.uploadImage(file);
      if (result.success && props.conversationId) {
        emit('send', result.url, props.conversationId, 'image');
      }
    } catch (error) {
      console.error('图片上传失败:', error);
    } finally {
      isUploading.value = false;
      uploadType.value = '';
    }
  };
  input.click();
}

// 上传语音
async function handleUploadAudio(): Promise<void> {
  if (!props.conversationId || isUploading.value) return;
  
  const input = document.createElement('input');
  input.type = 'file';
  input.accept = 'audio/*';
  input.onchange = async (e) => {
    const file = (e.target as HTMLInputElement).files?.[0];
    if (!file) return;
    
    isUploading.value = true;
    uploadType.value = 'audio';
    
    try {
      const result = await uploadApi.uploadAudio(file);
      if (result.success && props.conversationId) {
        emit('send', result.url, props.conversationId, 'audio');
      }
    } catch (error) {
      console.error('语音上传失败:', error);
    } finally {
      isUploading.value = false;
      uploadType.value = '';
    }
  };
  input.click();
}

// 上传文件
async function handleUploadFile(): Promise<void> {
  if (!props.conversationId || isUploading.value) return;
  
  const input = document.createElement('input');
  input.type = 'file';
  input.onchange = async (e) => {
    const file = (e.target as HTMLInputElement).files?.[0];
    if (!file) return;
    
    isUploading.value = true;
    uploadType.value = 'file';
    
    try {
      const result = await uploadApi.uploadFile(file);
      if (result.success && props.conversationId) {
        emit('send', result.url, props.conversationId, 'file');
      }
    } catch (error) {
      console.error('文件上传失败:', error);
    } finally {
      isUploading.value = false;
      uploadType.value = '';
    }
  };
  input.click();
}

// Handle send
async function handleSend(): Promise<void> {
  if (!canSend.value) return;

  const messageContent = content.value.trim();
  if (!messageContent || !props.conversationId) return;

  // Set sending state
  isSending.value = true;

  // Clear input
  content.value = '';

  // Reset textarea height
  await nextTick();
  adjustTextareaHeight();

  // Emit send event
  emit('send', messageContent, props.conversationId, 'text');

  // Reset sending state after a delay (for optimistic UI)
  setTimeout(() => {
    isSending.value = false;
  }, 1000);
}

// Expose focus method
function focus(): void {
  textareaRef.value?.focus();
}

defineExpose({
  focus
});

// Watch content change to adjust height
watch(content, () => {
  nextTick(adjustTextareaHeight);
});
</script>

<template>
  <div class="border-t border-gray-200 bg-white px-4 py-3">
    <div class="flex items-end gap-3">
      <!-- Textarea -->
      <div class="flex-1 relative">
        <textarea
          ref="textareaRef"
          v-model="content"
          class="w-full px-4 py-2.5 border border-gray-200 rounded-2xl resize-none text-sm focus:outline-none focus:border-primary-500 focus:ring-1 focus:ring-primary-500 transition-colors"
          :class="{
            'bg-gray-50 cursor-not-allowed opacity-60': disabled || isSending || isUploading
          }"
          :placeholder="placeholder"
          :disabled="disabled || isSending || isUploading"
          :rows="1"
          @input="handleInput"
          @keydown="handleKeydown"
        ></textarea>

        <!-- Loading indicator -->
        <div
          v-if="isSending"
          class="absolute right-3 bottom-2.5"
        >
          <svg class="animate-spin h-4 w-4 text-gray-400" fill="none" viewBox="0 0 24 24">
            <circle class="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" stroke-width="4"></circle>
            <path class="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4z"></path>
          </svg>
        </div>

        <!-- Upload loading -->
        <div
          v-if="isUploading"
          class="absolute right-3 bottom-2.5 flex items-center gap-1 text-xs text-gray-500"
        >
          <svg class="animate-spin h-4 w-4" fill="none" viewBox="0 0 24 24">
            <circle class="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" stroke-width="4"></circle>
            <path class="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4z"></path>
          </svg>
          <span>上传{{ uploadType === 'image' ? '图片' : uploadType === 'audio' ? '语音' : '文件' }}中...</span>
        </div>
      </div>

      <!-- Upload buttons -->
      <div class="flex-shrink-0 flex items-center gap-1">
        <!-- Image upload -->
        <button
          type="button"
          class="w-10 h-10 rounded-full flex items-center justify-center text-gray-500 hover:bg-gray-100 hover:text-gray-700 transition-colors"
          :disabled="isUploading"
          title="发送图片"
          @click="handleUploadImage"
        >
          <svg class="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M4 16l4.586-4.586a2 2 0 012.828 0L16 16m-2-2l1.586-1.586a2 2 0 012.828 0L20 14m-6-6h.01M6 20h12a2 2 0 002-2V6a2 2 0 00-2-2H6a2 2 0 00-2 2v12a2 2 0 002 2z"></path>
          </svg>
        </button>

        <!-- Audio upload -->
        <button
          type="button"
          class="w-10 h-10 rounded-full flex items-center justify-center text-gray-500 hover:bg-gray-100 hover:text-gray-700 transition-colors"
          :disabled="isUploading"
          title="发送语音"
          @click="handleUploadAudio"
        >
          <svg class="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M19 11a7 7 0 01-7 7m0 0a7 7 0 01-7-7m7 7v4m0 0H8m4 0h4m-4-8a3 3 0 01-3-3V5a3 3 0 116 0v6a3 3 0 01-3 3z"></path>
          </svg>
        </button>

        <!-- File upload -->
        <button
          type="button"
          class="w-10 h-10 rounded-full flex items-center justify-center text-gray-500 hover:bg-gray-100 hover:text-gray-700 transition-colors"
          :disabled="isUploading"
          title="发送文件"
          @click="handleUploadFile"
        >
          <svg class="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M15.172 7l-6.586 6.586a2 2 0 102.828 2.828l6.414-6.586a4 4 0 00-5.656-5.656l-6.415 6.585a6 6 0 108.486 8.486L20.5 13"></path>
          </svg>
        </button>
      </div>

      <!-- Send button -->
      <button
        class="flex-shrink-0 w-10 h-10 rounded-full flex items-center justify-center transition-colors"
        :class="{
          'bg-primary-500 text-white hover:bg-primary-600': canSend,
          'bg-gray-200 text-gray-400 cursor-not-allowed': !canSend
        }"
        :disabled="!canSend"
        @click="handleSend"
      >
        <svg class="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
          <path
            stroke-linecap="round"
            stroke-linejoin="round"
            stroke-width="2"
            d="M12 19l9 2-9-18-9 18 9-2zm0 0v-8"
          ></path>
        </svg>
      </button>
    </div>

    <!-- Hint -->
    <div class="mt-2 text-xs text-gray-400 text-center">
      按 <kbd class="px-1 py-0.5 bg-gray-100 rounded text-gray-600">Enter</kbd> 发送，
      <kbd class="px-1 py-0.5 bg-gray-100 rounded text-gray-600">Shift + Enter</kbd> 换行
    </div>
  </div>
</template>

<style scoped>
textarea {
  min-height: 40px;
  max-height: 200px;
}

textarea::placeholder {
  color: #9ca3af;
}

kbd {
  font-family: inherit;
}
</style>