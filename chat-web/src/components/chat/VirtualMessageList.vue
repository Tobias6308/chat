<script setup lang="ts">
import { ref, computed, watch, onMounted, nextTick, shallowRef } from 'vue';
import { useVirtualizer } from '@tanstack/vue-virtual';
import type { Message } from '@/types/chat';
import MessageBubble from './MessageBubble.vue';

interface Props {
  messages: Message[];
  loading?: boolean;
  loadingMore?: boolean;
  hasMore?: boolean;
  participants?: { userId: string; nickname: string; avatar?: string }[];
}

const props = withDefaults(defineProps<Props>(), {
  loading: false,
  loadingMore: false,
  hasMore: true
});

const emit = defineEmits<{
  (e: 'loadMore'): void;
  (e: 'retry', messageId: string): void;
  (e: 'scrollTop'): void;
  (e: 'clickUser', userId: string): void;
}>();

// Container ref
const containerRef = ref<HTMLElement | null>(null);

// User scroll state - if user manually scrolled up, don't auto-scroll to bottom
const userScrolled = ref(false);

// Item heights cache (estimate for better initial positioning)
const estimatedItemHeight = 80;

// Virtual list - using shallowRef to avoid deep reactivity
const virtualizerRef = shallowRef<any>(null);
const isInitialized = ref(false);

// Initialize virtualizer when container is ready
function initVirtualizer(): void {
  if (!containerRef.value || isInitialized.value) return;

  const virtualizer = useVirtualizer({
    count: props.messages.length,
    getScrollElement: () => containerRef.value,
    estimateSize: () => estimatedItemHeight,
    overscan: 5,
    scrollToFn: (top) => {
      if (containerRef.value) {
        containerRef.value.scrollTop = top;
      }
    }
  });

  virtualizerRef.value = virtualizer as any;
  isInitialized.value = true;
}

// Virtual items
const virtualItems = computed(() => {
  const v = virtualizerRef.value;
  if (!v) return [];
  return v.getVirtualItems?.() ?? [];
});

// Total size
const totalSize = computed(() => {
  const v = virtualizerRef.value;
  if (!v) return 0;
  return v.getTotalSize?.() ?? 0;
});

// Initialize on mount and when conversation changes
onMounted(() => {
  initVirtualizer();
});

// Reset when conversation changes (messages are cleared and refilled)
watch(
  () => props.messages,
  () => {
    // Only reinitialize when messages go from empty to non-empty (conversation change)
    if (props.messages.length > 0 && !isInitialized.value) {
      initVirtualizer();
    }
  },
  { immediate: true }
);

// Check if scrolled to bottom (with threshold)
function isScrolledToBottom(): boolean {
  if (!containerRef.value) return true;
  const threshold = 100;
  return (
    containerRef.value.scrollHeight - containerRef.value.scrollTop - containerRef.value.clientHeight
  ) < threshold;
}

// Handle scroll event
function handleScroll(): void {
  if (!containerRef.value) return;

  const scrolledToBottom = isScrolledToBottom();

  // Update user scroll state
  if (!scrolledToBottom) {
    userScrolled.value = true;
  } else {
    userScrolled.value = false;
  }

  // Load more when scrolling near top
  if (containerRef.value.scrollTop < 200 && !props.loadingMore && props.hasMore) {
    emit('loadMore');
  }

  // Emit scroll top event for header visibility
  if (containerRef.value.scrollTop < 50) {
    emit('scrollTop');
  }
}

// Scroll to bottom
function scrollToBottom(smooth = false): void {
  if (!containerRef.value) return;

  if (userScrolled.value) return;

  nextTick(() => {
    if (containerRef.value) {
      if (smooth) {
        containerRef.value.scrollTo({
          top: containerRef.value.scrollHeight,
          behavior: 'smooth'
        });
      } else {
        containerRef.value.scrollTop = containerRef.value.scrollHeight;
      }
    }
  });
}

// Scroll to specific message
function scrollToMessage(messageId: string): void {
  const index = props.messages.findIndex(m => m.id === messageId);
  const v = virtualizerRef.value;
  if (index !== -1 && v && v.scrollToIndex) {
    v.scrollToIndex(index, { align: 'center' });
  }
}

// Handle new message (auto-scroll)
watch(
  () => props.messages.length,
  () => {
    if (props.messages.length > 0) {
      const lastMessage = props.messages[0];
      if (lastMessage.senderId === 'current_user') {
        userScrolled.value = false;
        scrollToBottom();
      } else if (!userScrolled.value) {
        scrollToBottom();
      }
    }
  }
);

// Scroll to bottom on mount
onMounted(() => {
  initVirtualizer();
  nextTick(() => {
    scrollToBottom();
  });
});

// Expose methods for parent
defineExpose({
  scrollToBottom,
  scrollToMessage,
  isScrolledToBottom
});

// Click on "jump to bottom" button
function handleJumpToBottom(): void {
  userScrolled.value = false;
  scrollToBottom(true);
}

// Detect manual scroll
let scrollTimeout: number | null = null;
function handleWheel(): void {
  userScrolled.value = true;
  clearTimeout(scrollTimeout!);
  scrollTimeout = window.setTimeout(() => {
    if (isScrolledToBottom()) {
      userScrolled.value = false;
    }
  }, 300);
}
</script>

<template>
  <div class="relative h-full w-full">
    <!-- Loading state -->
    <div
      v-if="loading"
      class="absolute top-4 left-1/2 -translate-x-1/2 z-10 flex items-center gap-2 px-3 py-1.5 bg-gray-100 rounded-full"
    >
      <svg class="animate-spin h-4 w-4 text-gray-500" fill="none" viewBox="0 0 24 24">
        <circle class="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" stroke-width="4"></circle>
        <path class="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4z"></path>
      </svg>
      <span class="text-sm text-gray-600">加载中...</span>
    </div>

    <!-- Virtual scroll container -->
    <div
      ref="containerRef"
      class="h-full w-full overflow-y-auto"
      @scroll="handleScroll"
      @wheel="handleWheel"
    >
      <!-- Virtual list wrapper -->
      <div
        v-if="virtualizerRef && totalSize > 0"
        class="relative w-full"
        :style="{ height: `${totalSize}px` }"
      >
        <!-- Virtual items -->
        <div
          v-for="virtualItem in virtualItems"
          :key="virtualItem.key"
          class="absolute w-full"
          :style="{
            transform: `translateY(${virtualItem.start}px)`,
            height: `${virtualItem.size}px`
          }"
        >
          <MessageBubble
              :message="messages[virtualItem.index]"
              :participants="participants"
              @retry="emit('retry', $event)"
              @click-user="emit('clickUser', $event)"
            />
        </div>
      </div>

      <!-- Fallback (non-virtual) for empty state -->
      <div v-if="!virtualizerRef || totalSize === 0">
        <MessageBubble
          v-for="message in messages"
          :key="message.id"
          :message="message"
          :participants="participants"
          @retry="emit('retry', $event)"
          @click-user="emit('clickUser', $event)"
        />
      </div>

      <!-- Loading more indicator -->
      <div
        v-if="loadingMore"
        class="py-4 flex justify-center"
      >
        <div class="flex items-center gap-2">
          <svg class="animate-spin h-4 w-4 text-gray-400" fill="none" viewBox="0 0 24 24">
            <circle class="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" stroke-width="4"></circle>
            <path class="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4z"></path>
          </svg>
          <span class="text-sm text-gray-500">加载更多...</span>
        </div>
      </div>

      <!-- No more messages -->
      <div
        v-if="!hasMore && messages.length > 0"
        class="py-4 text-center text-sm text-gray-400"
      >
        已加载全部消息
      </div>
    </div>

    <!-- Jump to bottom button -->
    <Transition name="fade">
      <button
        v-if="userScrolled && messages.length > 0"
        class="absolute bottom-4 right-4 flex items-center gap-2 px-4 py-2 bg-primary-600 text-white rounded-full shadow-lg hover:bg-primary-700 transition-colors"
        @click="handleJumpToBottom"
      >
        <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
          <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M19 14l-7 7m0 0l-7-7m7 7V3"></path>
        </svg>
        <span class="text-sm font-medium">返回底部</span>
      </button>
    </Transition>
  </div>
</template>

<style scoped>
.fade-enter-active,
.fade-leave-active {
  transition: opacity 0.2s ease;
}

.fade-enter-from,
.fade-leave-to {
  opacity: 0;
}
</style>