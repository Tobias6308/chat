<script setup lang="ts">
import { onMounted, onUnmounted, watch } from 'vue';
import { useRouter, useRoute } from 'vue-router';
import { useWebSocket } from '@/composables/useWebSocket';
import { useConversationStore } from '@/stores/conversation';
import { useMessageStore } from '@/stores/message';
import { configApi, setFileServerUrl } from '@/utils/api';

const router = useRouter();
const route = useRoute();
const ws = useWebSocket();
const conversationStore = useConversationStore();
const messageStore = useMessageStore();

function connectIfLoggedIn(): void {
  const token = sessionStorage.getItem('chat_token');
  console.log('[App] connectIfLoggedIn, token exists:', !!token);
  if (token) {
    console.log('[App] Calling ws.connect()...');
    ws.connect(token);
    console.log('[App] ws.connect() called, status:', ws.status.value);
  }
}

async function loadConfig(): Promise<void> {
  try {
    const result = await configApi.getConfig();
    if (result.success && result.data?.fileServerUrl) {
      setFileServerUrl(result.data.fileServerUrl);
      console.log('[App] 文件服务器地址:', result.data.fileServerUrl);
    }
  } catch (error) {
    console.error('[App] 加载配置失败:', error);
  }
}

onMounted(() => {
  // Listen for auth errors
  ws.on('auth_error', ({ code }) => {
    console.error('[App] Auth error:', code);
    sessionStorage.removeItem('chat_token');
    router.push('/login');
  });

  // 连接成功后刷新会话和消息列表
  ws.on('open', () => {
    console.log('[App] WebSocket connected, refreshing conversations...');
    conversationStore.loadFromApi();
  });

  // 重连成功后也刷新会话列表
  ws.on('status_change', ({ status }) => {
    if (status === 'connected') {
      console.log('[App] WebSocket reconnected, refreshing conversations...');
      conversationStore.loadFromApi();
    }
  });

  // Auto-connect if token exists
  connectIfLoggedIn();

  // 加载配置 (文件服务器地址)
  loadConfig();

  // Listen for force logout from other tabs
  window.addEventListener('force-logout', handleForceLogout);
});

// Reconnect when route changes (e.g., after login)
watch(
  () => route.fullPath,
  () => {
    if (ws.status.value === 'disconnected') {
      connectIfLoggedIn();
    }
  }
);

onUnmounted(() => {
  ws.disconnect();
  window.removeEventListener('force-logout', handleForceLogout);
});

function handleForceLogout(): void {
  sessionStorage.removeItem('chat_token');
  ws.disconnect();
  router.push('/login');
}
</script>

<template>
  <router-view />
</template>