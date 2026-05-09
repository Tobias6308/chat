<script setup lang="ts">
import { ref } from 'vue';
import { useRouter } from 'vue-router';
import { useWebSocket } from '@/composables/useWebSocket';
import { useConversationStore } from '@/stores/conversation';
import { authApi } from '@/utils/api';

const router = useRouter();
const ws = useWebSocket();
const conversationStore = useConversationStore();

const mode = ref<'login' | 'register'>('login');
const username = ref('');
const password = ref('');
const nickname = ref('');
const isLoading = ref(false);
const error = ref('');

async function handleLogin(): Promise<void> {
  if (!username.value.trim() || !password.value.trim()) {
    error.value = '请输入用户名和密码';
    return;
  }

  isLoading.value = true;
  error.value = '';

  try {
    const result = await authApi.login({
      username: username.value.trim(),
      password: password.value.trim(),
    });

    sessionStorage.setItem('chat_token', result.token);
    sessionStorage.setItem('chat_userId', result.userId);
    sessionStorage.setItem('chat_nickname', result.nickname);

    ws.connect(result.token);

    await new Promise<void>((resolve, reject) => {
      const timeout = setTimeout(() => {
        reject(new Error('连接超时'));
      }, 10000);

      ws.on('status_change', ({ status }) => {
        if (status === 'connected') {
          clearTimeout(timeout);
          resolve();
        } else if (status === 'disconnected') {
          clearTimeout(timeout);
          reject(new Error('连接失败'));
        }
      });
    });

    router.push('/');
  } catch (err) {
    error.value = err instanceof Error ? err.message : '登录失败';
  } finally {
    isLoading.value = false;
  }
}

async function handleRegister(): Promise<void> {
  if (!username.value.trim() || !password.value.trim()) {
    error.value = '请输入用户名和密码';
    return;
  }

  if (password.value.trim().length < 6) {
    error.value = '密码长度至少6位';
    return;
  }

  isLoading.value = true;
  error.value = '';

  try {
    const result = await authApi.register({
      username: username.value.trim(),
      password: password.value.trim(),
      nickname: nickname.value.trim() || username.value.trim(),
    });

    sessionStorage.setItem('chat_token', result.token);
    sessionStorage.setItem('chat_userId', result.userId);
    sessionStorage.setItem('chat_nickname', result.nickname);

    ws.connect(result.token);

    await new Promise<void>((resolve, reject) => {
      const timeout = setTimeout(() => {
        reject(new Error('连接超时'));
      }, 10000);

      ws.on('status_change', ({ status }) => {
        if (status === 'connected') {
          clearTimeout(timeout);
          resolve();
        } else if (status === 'disconnected') {
          clearTimeout(timeout);
          reject(new Error('连接失败'));
        }
      });
    });

    router.push('/');
  } catch (err) {
    error.value = err instanceof Error ? err.message : '注册失败';
  } finally {
    isLoading.value = false;
  }
}

function handleSubmit() {
  if (mode.value === 'login') {
    handleLogin();
  } else {
    handleRegister();
  }
}

function switchMode() {
  mode.value = mode.value === 'login' ? 'register' : 'login';
  error.value = '';
}

// Demo login (for testing without real backend)
function handleDemoLogin(): void {
  const demoToken = 'demo_token_' + Math.random().toString(36).substr(2, 16);
  sessionStorage.setItem('chat_token', demoToken);

  // Create demo conversations
  const demoConversations = [
    {
      id: 'conv_1',
      type: 'private' as const,
      name: '张三',
      avatar: undefined,
      participants: [
        { userId: 'user_1', nickname: '张三' },
        { userId: 'current_user', nickname: '我' }
      ],
      unreadCount: 2,
      hasNewMessages: true,
      pinned: false,
      muted: false,
      createdAt: Date.now() - 86400000,
      updatedAt: Date.now() - 60000
    },
    {
      id: 'conv_2',
      type: 'group' as const,
      name: '前端开发群',
      avatar: undefined,
      participants: [
        { userId: 'user_2', nickname: '李四' },
        { userId: 'user_3', nickname: '王五' },
        { userId: 'current_user', nickname: '我' }
      ],
      unreadCount: 0,
      hasNewMessages: false,
      pinned: true,
      muted: false,
      createdAt: Date.now() - 172800000,
      updatedAt: Date.now() - 3600000
    },
    {
      id: 'conv_3',
      type: 'private' as const,
      name: '产品经理',
      avatar: undefined,
      participants: [
        { userId: 'user_4', nickname: '产品经理' },
        { userId: 'current_user', nickname: '我' }
      ],
      unreadCount: 1,
      hasNewMessages: true,
      pinned: false,
      muted: false,
      createdAt: Date.now() - 259200000,
      updatedAt: Date.now() - 7200000
    }
  ];

  // Directly load demo conversations into the store
  conversationStore.upsertConversations(demoConversations);

  router.push('/');
}
</script>

<template>
  <div class="min-h-screen bg-gradient-to-br from-primary-500 to-primary-700 flex items-center justify-center p-4">
    <div class="w-full max-w-md">
      <!-- Logo & Title -->
      <div class="text-center mb-8">
        <div class="inline-flex items-center justify-center w-16 h-16 bg-white/20 rounded-2xl mb-4">
          <svg class="w-10 h-10 text-white" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M8 12h.01M12 12h.01M16 12h.01M21 12c0 4.418-4.03 8-9 8a9.863 9.863 0 01-4.255-.949L3 20l1.395-3.72C3.512 15.042 3 13.574 3 12c0-4.418 4.03-8 9-8s9 3.582 9 8z"></path>
          </svg>
        </div>
        <h1 class="text-3xl font-bold text-white mb-2">实时聊天</h1>
        <p class="text-primary-100">连接您的团队，即时沟通</p>
      </div>

      <!-- Login Card -->
      <div class="bg-white rounded-2xl shadow-xl p-8">
        <h2 class="text-xl font-semibold text-gray-800 mb-6">{{ mode === 'login' ? '登录' : '注册' }}</h2>

        <!-- Username Input -->
        <div class="mb-4">
          <label class="block text-sm font-medium text-gray-700 mb-2">
            用户名
          </label>
          <input
            v-model="username"
            type="text"
            placeholder="请输入用户名"
            class="w-full px-4 py-3 border border-gray-200 rounded-xl focus:outline-none focus:ring-2 focus:ring-primary-500 focus:border-transparent transition-all"
            :disabled="isLoading"
            @keydown.enter="handleSubmit"
          >
        </div>

        <!-- Password Input -->
        <div class="mb-4">
          <label class="block text-sm font-medium text-gray-700 mb-2">
            密码
          </label>
          <input
            v-model="password"
            type="password"
            placeholder="请输入密码"
            class="w-full px-4 py-3 border border-gray-200 rounded-xl focus:outline-none focus:ring-2 focus:ring-primary-500 focus:border-transparent transition-all"
            :disabled="isLoading"
            @keydown.enter="handleSubmit"
          >
        </div>

        <!-- Nickname Input (Register mode only) -->
        <div v-if="mode === 'register'" class="mb-4">
          <label class="block text-sm font-medium text-gray-700 mb-2">
            昵称 (可选)
          </label>
          <input
            v-model="nickname"
            type="text"
            placeholder="请输入昵称"
            class="w-full px-4 py-3 border border-gray-200 rounded-xl focus:outline-none focus:ring-2 focus:ring-primary-500 focus:border-transparent transition-all"
            :disabled="isLoading"
            @keydown.enter="handleSubmit"
          >
        </div>

        <!-- Error Message -->
        <div
          v-if="error"
          class="mb-4 p-3 bg-red-50 border border-red-200 rounded-lg text-sm text-red-600"
        >
          {{ error }}
        </div>

        <!-- Submit Button -->
        <button
          class="w-full py-3 px-4 bg-primary-500 text-white font-medium rounded-xl hover:bg-primary-600 focus:outline-none focus:ring-2 focus:ring-primary-500 focus:ring-offset-2 transition-all disabled:opacity-50 disabled:cursor-not-allowed"
          :disabled="isLoading"
          @click="handleSubmit"
        >
          <span v-if="isLoading" class="flex items-center justify-center gap-2">
            <svg class="animate-spin h-5 w-5" fill="none" viewBox="0 0 24 24">
              <circle class="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" stroke-width="4"></circle>
              <path class="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4z"></path>
            </svg>
            {{ mode === 'login' ? '登录中...' : '注册中...' }}
          </span>
          <span v-else>{{ mode === 'login' ? '登录' : '注册' }}</span>
        </button>

        <!-- Mode Switch -->
        <p class="text-center mt-4 text-sm text-gray-600">
          {{ mode === 'login' ? '没有账号？' : '已有账号？' }}
          <button
            type="button"
            class="text-primary-500 hover:text-primary-600 font-medium ml-1"
            @click="switchMode"
          >
            {{ mode === 'login' ? '立即注册' : '立即登录' }}
          </button>
        </p>

        <!-- Divider -->
        <div class="relative my-6">
          <div class="absolute inset-0 flex items-center">
            <div class="w-full border-t border-gray-200"></div>
          </div>
          <div class="relative flex justify-center text-sm">
            <span class="px-4 bg-white text-gray-400">或</span>
          </div>
        </div>

        <!-- Demo Login -->
        <button
          class="w-full py-3 px-4 bg-gray-100 text-gray-700 font-medium rounded-xl hover:bg-gray-200 focus:outline-none focus:ring-2 focus:ring-gray-500 focus:ring-offset-2 transition-all"
          @click="handleDemoLogin"
        >
          演示模式
        </button>
      </div>

      <!-- Footer -->
      <p class="text-center mt-8 text-primary-100 text-sm">
        &copy; 2024 实时聊天应用
      </p>
    </div>
  </div>
</template>