<script setup lang="ts">
import { onMounted, watch, computed } from 'vue';
import { ElMessage } from 'element-plus';
import { useWebSocket } from '@/composables/useWebSocket';
import { useMessageStore } from '@/stores/message';
import { useConversationStore } from '@/stores/conversation';
import { chatApi } from '@/utils/api';
import ChatLayout from '@/components/chat/ChatLayout.vue';

const ws = useWebSocket();
const messageStore = useMessageStore();
const conversationStore = useConversationStore();

// Get connection status - use computed to ensure reactivity
const wsStatus = computed(() => ws.status.value);

watch(wsStatus, (newVal) => {
  console.log('[ChatRoom] wsStatus changed to:', newVal);
});

// Handle WebSocket events (connection handled by App.vue)
onMounted(() => {
  // Handle incoming messages
  ws.on('message', ({ payload }) => {
    if (payload.type === 'message') {
      const messageData = payload.payload as any;
      const message = {
        id: messageData.id,
        conversationId: messageData.conversationId,
        senderId: messageData.senderId,
        content: messageData.content,
        contentType: messageData.contentType || 'text',
        status: messageData.status || 'delivered',
        createdAt: messageData.createdAt
      };

      // Add to store
      messageStore.addMessage(message.conversationId, message);

      // Update conversation's updatedAt and lastMessage
      const existingConv = conversationStore.getConversationById(message.conversationId);
      if (existingConv) {
        conversationStore.updateConversation(message.conversationId, {
          updatedAt: message.createdAt,
          lastMessage: {
            id: message.id,
            content: message.content,
            contentType: message.contentType,
            senderId: message.senderId,
            createdAt: message.createdAt
          }
        });
      } else {
        // If conversation doesn't exist, create a basic one
        conversationStore.upsertConversation({
          id: message.conversationId,
          type: 'private',
          name: '新会话',
          participants: [],
          unreadCount: 1,
          hasNewMessages: true,
          pinned: false,
          muted: false,
          createdAt: message.createdAt,
          updatedAt: message.createdAt,
          lastMessage: {
            id: message.id,
            content: message.content,
            contentType: message.contentType,
            senderId: message.senderId,
            createdAt: message.createdAt
          }
        });
      }

      // If not active conversation, increment unread
      if (conversationStore.activeConversationId !== message.conversationId) {
        conversationStore.incrementUnread(message.conversationId);
      }

      // Send ACK
      ws.sendAck(message.id, 'delivered');
    }
  });

  // Handle ACK
  ws.on('message', ({ payload }) => {
    if (payload.type === 'ack_ok') {
      const { messageId, status } = payload.payload;
      messageStore.updateMessageStatus(messageId, status);
    }
  });

  // Handle history
  ws.on('message', ({ payload }) => {
    if (payload.type === 'history') {
      const { messages } = payload.payload;

      // Add messages to store
      if (messages.length > 0) {
        // This would need conversation ID from the fetch request
        // For now, handled in store
      }
    }
  });
});

// Handle send message
async function handleSendMessage(content: string, conversationId: string, contentType: string = 'text'): Promise<void> {
  console.log('[ChatRoom] handleSendMessage called, content:', content, 'conversationId:', conversationId, 'contentType:', contentType, 'ws status:', ws.status.value);

  if (!ws.isConnected) {
    ElMessage.warning('网络未连接，消息将稍后发送')
  }

  // 创建乐观消息
  const optimisticMessage = messageStore.createOptimisticMessage(
    conversationId,
    content,
    contentType as any
  );

  // 通过 WebSocket 发送
  console.log('[ChatRoom] Calling ws.sendMessage...');
  const messageId = ws.sendMessage(conversationId, content, contentType as any);
  console.log('[ChatRoom] ws.sendMessage returned, messageId:', messageId);

  // 用真实 ID 替换乐观消息
  messageStore.replaceOptimisticMessage(
    optimisticMessage.id,
    {
      ...optimisticMessage,
      id: messageId
    } as any,
    conversationId
  );

  // 更新会话的 updatedAt 和 lastMessage
  const now = Date.now();
  conversationStore.updateConversation(conversationId, {
    updatedAt: now,
    lastMessage: {
      id: messageId,
      content: content,
      contentType: contentType,
      senderId: 'current_user',
      createdAt: now
    }
  });

  // 标记发送者已读
  await chatApi.markAsRead(conversationId);
}

// Handle retry failed message
function handleRetryMessage(messageId: string): void {
  if (conversationStore.activeConversationId) {
    messageStore.retryMessage(messageId, conversationStore.activeConversationId);
  }
}

// Handle load more
function handleLoadMore(conversationId: string): void {
  const cursor = messageStore.getMessages(conversationId).pop()?.createdAt;
  messageStore.fetchHistory(conversationId, cursor ? String(cursor) : undefined);
}

// Handle conversation select
async function handleSelectConversation(conversationId: string): Promise<void> {
  // Fetch history for selected conversation
  messageStore.fetchHistory(conversationId);
  // Mark as read
  await chatApi.markAsRead(conversationId);
}
</script>

<template>
  <ChatLayout
    :ws-status="wsStatus"
    @select-conversation="handleSelectConversation"
    @send-message="handleSendMessage"
    @retry-message="handleRetryMessage"
    @load-more="handleLoadMore"
  />
</template>