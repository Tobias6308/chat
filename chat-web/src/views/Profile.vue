<script setup lang="ts">
import { ref, onMounted } from 'vue';
import { useRouter } from 'vue-router';
import { userApi } from '@/utils/api';

const router = useRouter();

const userInfo = ref<{
  userId: string;
  username: string;
  nickname: string;
  avatar: string;
  createdAt: number;
  lastLoginAt?: number;
} | null>(null);

const isEditing = ref(false);
const isChangingPassword = ref(false);
const isLoading = ref(false);
const error = ref('');
const success = ref('');

const form = ref({
  nickname: '',
  avatar: '',
});

const passwordForm = ref({
  oldPassword: '',
  newPassword: '',
  confirmPassword: '',
});

onMounted(async () => {
  await loadUserInfo();
});

async function loadUserInfo() {
  try {
    isLoading.value = true;
    userInfo.value = await userApi.getInfo();
    form.value.nickname = userInfo.value?.nickname || '';
    form.value.avatar = userInfo.value?.avatar || '';
  } catch (err) {
    error.value = err instanceof Error ? err.message : '加载失败';
  } finally {
    isLoading.value = false;
  }
}

async function handleSaveProfile() {
  if (!form.value.nickname.trim()) {
    error.value = '昵称不能为空';
    return;
  }

  try {
    isLoading.value = true;
    error.value = '';
    success.value = '';
    
    await userApi.updateProfile({
      nickname: form.value.nickname.trim(),
      avatar: form.value.avatar,
    });
    
    success.value = '保存成功';
    isEditing.value = false;
    
    const token = sessionStorage.getItem('chat_token');
    if (token && form.value.nickname) {
      sessionStorage.setItem('chat_nickname', form.value.nickname);
    }
    
    setTimeout(() => {
      success.value = '';
    }, 2000);
  } catch (err) {
    error.value = err instanceof Error ? err.message : '保存失败';
  } finally {
    isLoading.value = false;
  }
}

async function handleChangePassword() {
  if (!passwordForm.value.oldPassword || !passwordForm.value.newPassword) {
    error.value = '请填写所有密码字段';
    return;
  }

  if (passwordForm.value.newPassword.length < 6) {
    error.value = '新密码长度至少6位';
    return;
  }

  if (passwordForm.value.newPassword !== passwordForm.value.confirmPassword) {
    error.value = '两次输入的密码不一致';
    return;
  }

  try {
    isLoading.value = true;
    error.value = '';
    success.value = '';
    
    await userApi.updatePassword({
      oldPassword: passwordForm.value.oldPassword,
      newPassword: passwordForm.value.newPassword,
    });
    
    success.value = '密码修改成功';
    isChangingPassword.value = false;
    passwordForm.value = { oldPassword: '', newPassword: '', confirmPassword: '' };
    
    setTimeout(() => {
      success.value = '';
    }, 2000);
  } catch (err) {
    error.value = err instanceof Error ? err.message : '修改密码失败';
  } finally {
    isLoading.value = false;
  }
}

function formatDate(timestamp: number): string {
  return new Date(timestamp).toLocaleString('zh-CN');
}

function logout() {
  sessionStorage.clear();
  router.push('/login');
}
</script>

<template>
  <div class="min-h-screen bg-gray-50">
    <!-- Header -->
    <div class="bg-white shadow-sm">
      <div class="max-w-4xl mx-auto px-4 py-4 flex items-center justify-between">
        <button
          class="text-gray-600 hover:text-gray-800"
          @click="router.push('/')"
        >
          <svg class="w-6 h-6" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M15 19l-7-7 7-7" />
          </svg>
        </button>
        <h1 class="text-lg font-semibold text-gray-800">个人设置</h1>
        <div class="w-8"></div>
      </div>
    </div>

    <div class="max-w-4xl mx-auto px-4 py-6">
      <!-- Loading -->
      <div v-if="isLoading && !userInfo" class="flex justify-center py-12">
        <div class="animate-spin rounded-full h-8 w-8 border-b-2 border-primary-500"></div>
      </div>

      <template v-if="userInfo">
        <!-- User Info Card -->
        <div class="bg-white rounded-xl shadow-sm p-6 mb-4">
          <div class="flex items-center gap-4">
            <img
              :src="form.avatar || userInfo.avatar || 'https://api.dicebear.com/7.x/avataaars/svg?seed=' + userInfo.username"
              class="w-16 h-16 rounded-full"
              :alt="userInfo.nickname"
            />
            <div>
              <div class="text-lg font-semibold text-gray-800">{{ userInfo.nickname || userInfo.username }}</div>
              <div class="text-sm text-gray-500">@{{ userInfo.username }}</div>
            </div>
          </div>
        </div>

        <!-- Error/Success Messages -->
        <div v-if="error" class="mb-4 p-4 bg-red-50 border border-red-200 rounded-lg text-red-600">
          {{ error }}
        </div>
        <div v-if="success" class="mb-4 p-4 bg-green-50 border border-green-200 rounded-lg text-green-600">
          {{ success }}
        </div>

        <!-- Avatar Section -->
        <div class="bg-white rounded-xl shadow-sm p-6 mb-4">
          <h2 class="text-lg font-semibold text-gray-800 mb-4">头像</h2>
          <div class="flex items-center gap-6">
            <img
              :src="form.avatar || userInfo.avatar || 'https://api.dicebear.com/7.x/avataaars/svg?seed=' + userInfo.username"
              class="w-24 h-24 rounded-full"
              :alt="userInfo.nickname"
            />
            <div>
              <p class="text-gray-600 text-sm mb-2">输入头像 URL 修改头像</p>
              <input
                v-model="form.avatar"
                type="text"
                placeholder="输入头像 URL"
                class="w-64 px-4 py-2 border border-gray-200 rounded-lg focus:outline-none focus:ring-2 focus:ring-primary-500"
              />
              <button
                v-if="form.avatar && form.avatar !== userInfo.avatar"
                class="ml-2 px-3 py-2 bg-primary-500 text-white text-sm rounded-lg hover:bg-primary-600"
                @click="handleSaveProfile"
              >
                保存
              </button>
            </div>
          </div>
        </div>

        <!-- Profile Info Section -->
        <div class="bg-white rounded-xl shadow-sm p-6 mb-4">
          <div class="flex items-center justify-between mb-4">
            <h2 class="text-lg font-semibold text-gray-800">基本信息</h2>
            <button
              v-if="!isEditing"
              class="px-4 py-2 bg-primary-500 text-white rounded-lg hover:bg-primary-600"
              @click="isEditing = true"
            >
              编辑
            </button>
          </div>

          <div class="space-y-4">
            <div>
              <label class="block text-sm text-gray-500 mb-1">用户名</label>
              <div class="text-gray-800">{{ userInfo.username }}</div>
            </div>

            <div>
              <label class="block text-sm text-gray-500 mb-1">昵称</label>
              <input
                v-model="form.nickname"
                type="text"
                placeholder="请输入昵称"
                class="w-full px-4 py-2 border border-gray-200 rounded-lg focus:outline-none focus:ring-2 focus:ring-primary-500"
                :disabled="!isEditing"
              />
            </div>

            <div>
              <label class="block text-sm text-gray-500 mb-1">注册时间</label>
              <div class="text-gray-800">{{ formatDate(userInfo.createdAt) }}</div>
            </div>

            <div v-if="userInfo.lastLoginAt">
              <label class="block text-sm text-gray-500 mb-1">上次登录</label>
              <div class="text-gray-800">{{ formatDate(userInfo.lastLoginAt) }}</div>
            </div>
          </div>

          <div v-if="isEditing" class="mt-6 flex gap-3">
            <button
              class="px-4 py-2 bg-primary-500 text-white rounded-lg hover:bg-primary-600"
              @click="handleSaveProfile"
            >
              保存
            </button>
            <button
              class="px-4 py-2 border border-gray-200 text-gray-600 rounded-lg hover:bg-gray-50"
              @click="isEditing = false; loadUserInfo()"
            >
              取消
            </button>
          </div>
        </div>

        <!-- Change Password Section -->
        <div class="bg-white rounded-xl shadow-sm p-6 mb-4">
          <div class="flex items-center justify-between mb-4">
            <h2 class="text-lg font-semibold text-gray-800">修改密码</h2>
            <button
              v-if="!isChangingPassword"
              class="px-4 py-2 border border-gray-200 text-gray-600 rounded-lg hover:bg-gray-50"
              @click="isChangingPassword = true"
            >
              修改密码
            </button>
          </div>

          <div v-if="isChangingPassword" class="space-y-4">
            <div>
              <label class="block text-sm text-gray-500 mb-1">当前密码</label>
              <input
                v-model="passwordForm.oldPassword"
                type="password"
                placeholder="请输入当前密码"
                class="w-full px-4 py-2 border border-gray-200 rounded-lg focus:outline-none focus:ring-2 focus:ring-primary-500"
              />
            </div>

            <div>
              <label class="block text-sm text-gray-500 mb-1">新密码</label>
              <input
                v-model="passwordForm.newPassword"
                type="password"
                placeholder="请输入新密码（至少6位）"
                class="w-full px-4 py-2 border border-gray-200 rounded-lg focus:outline-none focus:ring-2 focus:ring-primary-500"
              />
            </div>

            <div>
              <label class="block text-sm text-gray-500 mb-1">确认新密码</label>
              <input
                v-model="passwordForm.confirmPassword"
                type="password"
                placeholder="请再次输入新密码"
                class="w-full px-4 py-2 border border-gray-200 rounded-lg focus:outline-none focus:ring-2 focus:ring-primary-500"
              />
            </div>

            <div class="flex gap-3">
              <button
                class="px-4 py-2 bg-primary-500 text-white rounded-lg hover:bg-primary-600"
                @click="handleChangePassword"
              >
                确认修改
              </button>
              <button
                class="px-4 py-2 border border-gray-200 text-gray-600 rounded-lg hover:bg-gray-50"
                @click="isChangingPassword = false; passwordForm = { oldPassword: '', newPassword: '', confirmPassword: '' }"
              >
                取消
              </button>
            </div>
          </div>
        </div>

        <!-- Logout Section -->
        <div class="bg-white rounded-xl shadow-sm p-6">
          <button
            class="w-full px-4 py-2 bg-red-500 text-white rounded-lg hover:bg-red-600"
            @click="logout"
          >
            退出登录
          </button>
        </div>
      </template>
    </div>
  </div>
</template>