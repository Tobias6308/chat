<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { adminApi } from '@/utils/api'
import MD5 from 'crypto-js/md5'

const adminInfo = computed(() => {
  const info = sessionStorage.getItem('admin_info')
  return info ? JSON.parse(info) : {}
})

const profileForm = ref({
  nickname: '',
  username: '',
  roles: [] as string[]
})

const passwordForm = ref({
  oldPassword: '',
  newPassword: '',
  confirmPassword: ''
})

const loading = ref(false)
const activeTab = ref('profile')

onMounted(async () => {
  await loadProfile()
})

async function loadProfile() {
  try {
    const data: any = await adminApi.getProfile()
    if (data.adminId) {
      profileForm.value.nickname = data.nickname || ''
      profileForm.value.username = data.username || ''
      profileForm.value.roles = data.roles || []
    }
  } catch (e) {
    console.error('Failed to load profile:', e)
  }
}

async function handleUpdateProfile() {
  if (!profileForm.value.nickname) {
    ElMessage.warning('昵称不能为空')
    return
  }

  loading.value = true
  try {
    const data: any = await adminApi.updateProfile({ nickname: profileForm.value.nickname })
    if (data.success) {
      ElMessage.success('昵称修改成功')
      sessionStorage.setItem('admin_info', JSON.stringify({
        ...adminInfo.value,
        nickname: profileForm.value.nickname
      }))
    } else {
      ElMessage.error(data.message || '修改失败')
    }
  } catch (e: any) {
    ElMessage.error(e.response?.data?.message || '修改失败')
  } finally {
    loading.value = false
  }
}

async function handleUpdatePassword() {
  if (!passwordForm.value.oldPassword || !passwordForm.value.newPassword) {
    ElMessage.warning('请填写完整密码信息')
    return
  }

  if (passwordForm.value.newPassword !== passwordForm.value.confirmPassword) {
    ElMessage.warning('两次输入的密码不一致')
    return
  }

  if (passwordForm.value.newPassword.length < 6) {
    ElMessage.warning('新密码长度至少6位')
    return
  }

  loading.value = true
  try {
    const oldPwd = MD5(passwordForm.value.oldPassword).toString()
    const newPwd = MD5(passwordForm.value.newPassword).toString()
    const data: any = await adminApi.updatePassword({ oldPassword: oldPwd, newPassword: newPwd })
    if (data.success) {
      ElMessage.success('密码修改成功')
      passwordForm.value.oldPassword = ''
      passwordForm.value.newPassword = ''
      passwordForm.value.confirmPassword = ''
    } else {
      ElMessage.error(data.message || '修改失败')
    }
  } catch (e: any) {
    ElMessage.error(e.response?.data?.message || '修改失败')
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="profile-container">
    <h2>账号设置</h2>
    <el-tabs v-model="activeTab" class="profile-tabs">
      <el-tab-pane label="基本信息" name="profile">
        <el-card class="profile-card">
          <el-form :model="profileForm" label-width="80px">
            <el-form-item label="头像">
              <div class="avatar-section">
                <el-avatar :size="80" class="profile-avatar">
                  {{ profileForm.nickname?.charAt(0) || profileForm.username?.charAt(0) || 'A' }}
                </el-avatar>
              </div>
            </el-form-item>
            <el-form-item label="用户名">
              <el-input v-model="profileForm.username" disabled />
            </el-form-item>
            <el-form-item label="昵称">
              <el-input v-model="profileForm.nickname" placeholder="请输入昵称" />
            </el-form-item>
            <el-form-item label="角色">
              <el-tag type="primary">{{ profileForm.roles?.includes('super') ? '超级管理员' : '管理员' }}</el-tag>
            </el-form-item>
            <el-form-item>
              <el-button type="primary" @click="handleUpdateProfile" :loading="loading">
                保存修改
              </el-button>
            </el-form-item>
          </el-form>
        </el-card>
      </el-tab-pane>

      <el-tab-pane label="修改密码" name="password">
        <el-card class="profile-card">
          <el-form :model="passwordForm" label-width="100px">
            <el-form-item label="当前密码">
              <el-input v-model="passwordForm.oldPassword" type="password" show-password placeholder="请输入当前密码" />
            </el-form-item>
            <el-form-item label="新密码">
              <el-input v-model="passwordForm.newPassword" type="password" show-password placeholder="请输入新密码" />
            </el-form-item>
            <el-form-item label="确认密码">
              <el-input v-model="passwordForm.confirmPassword" type="password" show-password placeholder="请再次输入新密码" />
            </el-form-item>
            <el-form-item>
              <el-button type="primary" @click="handleUpdatePassword" :loading="loading">
                修改密码
              </el-button>
            </el-form-item>
          </el-form>
        </el-card>
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<style scoped>
.profile-container {
  max-width: 800px;
}

.profile-container h2 {
  margin-bottom: 20px;
  color: #333;
}

.profile-tabs {
  background: #fff;
  border-radius: 8px;
  padding: 20px;
}

.profile-card {
  max-width: 500px;
}

.avatar-section {
  display: flex;
  align-items: center;
}

.profile-avatar {
  background: linear-gradient(135deg, #409eff 0%, #3373e6 100%);
  font-size: 28px;
}
</style>