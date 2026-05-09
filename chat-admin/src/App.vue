<script setup lang="ts">
import { ref, computed } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import {
  ChatDotRound,
  DataAnalysis,
  UserFilled,
  Setting,
  User,
  Files,
  Message,
  DArrowLeft,
  DArrowRight
} from '@element-plus/icons-vue'

const router = useRouter()
const route = useRoute()

const isCollapse = ref(false)

const showLayout = computed(() => route.path !== '/login')

const adminInfo = computed(() => {
  const info = sessionStorage.getItem('admin_info')
  return info ? JSON.parse(info) : { nickname: '管理员', roles: [] }
})

const adminRole = computed(() => {
  const roles = adminInfo.value.roles || []
  if (roles.includes('super')) return '超级管理员'
  if (roles.includes('admin')) return '管理员'
  return '管理员'
})

function handleLogout() {
  sessionStorage.removeItem('admin_token')
  sessionStorage.removeItem('admin_info')
  router.push('/login')
}
</script>

<template>
  <router-view v-if="!showLayout" />
  <el-container v-else class="layout-container">
    <!-- Sidebar -->
    <el-aside :width="isCollapse ? '64px' : '220px'" class="sidebar">
      <div class="logo" :class="{ 'logo-collapse': isCollapse }">
        <div class="logo-icon">
          <el-icon><ChatDotRound /></el-icon>
        </div>
        <transition name="fade">
          <div v-if="!isCollapse" class="logo-text">
            <h3>Chat Admin</h3>
            <span>管理系统</span>
          </div>
        </transition>
      </div>
      <div class="collapse-btn" @click="isCollapse = !isCollapse">
        <el-icon><DArrowRight v-if="isCollapse" /><DArrowLeft v-else /></el-icon>
      </div>
      <el-menu
        :default-active="route.path"
        class="sidebar-menu"
        :collapse="isCollapse"
        :collapse-transition="false"
        router
        background-color="#1f2d3d"
        text-color="#bfcbd9"
        active-text-color="#409eff"
      >
        <div class="menu-title">数据统计</div>
        <el-menu-item index="/">
          <el-icon><DataAnalysis /></el-icon>
          <span>数据概览</span>
        </el-menu-item>

        <div class="menu-title">系统管理</div>
        <el-menu-item index="/profile">
          <el-icon><UserFilled /></el-icon>
          <span>账号设置</span>
        </el-menu-item>
        <el-menu-item index="/admins">
          <el-icon><Setting /></el-icon>
          <span>管理员</span>
        </el-menu-item>

        <div class="menu-title">业务管理</div>
        <el-menu-item index="/users">
          <el-icon><User /></el-icon>
          <span>用户管理</span>
        </el-menu-item>
        <el-menu-item index="/groups">
          <el-icon><Files /></el-icon>
          <span>群组管理</span>
        </el-menu-item>
        <el-menu-item index="/conversations">
          <el-icon><ChatDotRound /></el-icon>
          <span>会话管理</span>
        </el-menu-item>
        <el-menu-item index="/friends">
          <el-icon><UserFilled /></el-icon>
          <span>好友管理</span>
        </el-menu-item>
        <el-menu-item index="/messages">
          <el-icon><Message /></el-icon>
          <span>消息管理</span>
        </el-menu-item>
      </el-menu>
    </el-aside>

    <!-- Main -->
    <el-container>
      <el-header class="header">
        <div class="header-left">
          <el-button :icon="isCollapse ? 'Expand' : 'Fold'" @click="isCollapse = !isCollapse" text />
          <span class="page-title">{{ route.meta?.title || '后台管理' }}</span>
        </div>
        <div class="header-right">
          <div class="admin-profile" @click="router.push('/profile')">
            <el-avatar :size="32" class="admin-avatar">
              {{ adminInfo.nickname?.charAt(0) || adminInfo.username?.charAt(0) || 'A' }}
            </el-avatar>
            <div class="admin-info">
              <span class="admin-name">{{ adminInfo.nickname || adminInfo.username }}</span>
              <span class="admin-role">{{ adminRole }}</span>
            </div>
          </div>
          <el-divider direction="vertical" />
          <el-button type="danger" text @click="handleLogout">退出</el-button>
        </div>
      </el-header>
      <el-main class="main">
        <router-view />
      </el-main>
    </el-container>
  </el-container>
</template>

<style>
* {
  font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", "Microsoft YaHei", "微软雅黑", "PingFang SC", "Hiragino Sans GB", "Helvetica Neue", Helvetica, Arial, sans-serif;
}
</style>
<style scoped>
.layout-container {
  height: 100vh;
}

.sidebar {
  background: linear-gradient(180deg, #1f2d3d 0%, #1a2332 100%);
  display: flex;
  flex-direction: column;
}

.logo {
  height: 64px;
  display: flex;
  align-items: center;
  padding: 0 20px;
  background: rgba(0, 0, 0, 0.2);
  border-bottom: 1px solid rgba(255, 255, 255, 0.05);
}

.logo-collapse {
  justify-content: center;
  padding: 0;
}

.collapse-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 40px;
  margin: 8px 12px;
  background: rgba(255, 255, 255, 0.05);
  border-radius: 8px;
  color: #8b9bb4;
  cursor: pointer;
  transition: all 0.2s;
}

.collapse-btn:hover {
  background: rgba(64, 158, 255, 0.15);
  color: #409eff;
}

.fade-enter-active,
.fade-leave-active {
  transition: opacity 0.2s;
}

.fade-enter,
.fade-leave-to {
  opacity: 0;
}

.logo-icon {
  width: 40px;
  height: 40px;
  background: linear-gradient(135deg, #409eff 0%, #3373e6 100%);
  border-radius: 10px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  margin-right: 12px;
}

.logo-text h3 {
  color: #fff;
  font-size: 16px;
  margin: 0;
  font-weight: 600;
}

.logo-text span {
  color: #8b9bb4;
  font-size: 11px;
}

.sidebar-menu {
  border-right: none;
  flex: 1;
  overflow-y: auto;
}

.sidebar-menu::-webkit-scrollbar {
  width: 4px;
}

.sidebar-menu::-webkit-scrollbar-thumb {
  background: rgba(255, 255, 255, 0.1);
  border-radius: 2px;
}

.menu-title {
  padding: 20px 20px 8px;
  color: #5c6b7f;
  font-size: 12px;
  font-weight: 500;
  letter-spacing: 0.5px;
}

.is-collapse + .menu-title,
.is-collapse ~ .menu-title {
  display: none;
}

:deep(.el-menu--collapse) .menu-title {
  display: none;
}

:deep(.el-menu-item) {
  height: 48px;
  line-height: 48px;
  margin: 4px 12px;
  border-radius: 8px;
  transition: all 0.2s;
}

:deep(.el-menu-item:hover) {
  background: rgba(64, 158, 255, 0.15) !important;
}

:deep(.el-menu-item.is-active) {
  background: linear-gradient(90deg, rgba(64, 158, 255, 0.25) 0%, rgba(64, 158, 255, 0.1) 100%) !important;
}

:deep(.el-menu-item.is-active::before) {
  content: '';
  position: absolute;
  left: 0;
  top: 50%;
  transform: translateY(-50%);
  width: 3px;
  height: 20px;
  background: #409eff;
  border-radius: 0 3px 3px 0;
}

.header {
  background: #fff;
  display: flex;
  justify-content: space-between;
  align-items: center;
  border-bottom: 1px solid #e6e6e6;
  padding: 0 24px;
}

.header-left {
  display: flex;
  align-items: center;
  gap: 12px;
}

.page-title {
  color: #333;
  font-size: 16px;
  font-weight: 500;
}

.header-right {
  display: flex;
  align-items: center;
}

.admin-profile {
  display: flex;
  align-items: center;
  gap: 8px;
  cursor: pointer;
  padding: 4px 8px;
  border-radius: 8px;
  transition: background 0.2s;
}

.admin-profile:hover {
  background: #f5f7fa;
}

.admin-avatar {
  background: linear-gradient(135deg, #409eff 0%, #3373e6 100%);
  font-size: 14px;
}

.admin-info {
  display: flex;
  flex-direction: column;
}

.admin-name {
  color: #333;
  font-size: 14px;
  font-weight: 500;
  line-height: 1.2;
}

.admin-role {
  color: #909399;
  font-size: 12px;
  line-height: 1.2;
}

.main {
  background: #f0f2f5;
  padding: 20px;
}

:deep(.el-divider--vertical) {
  height: 20px;
  margin: 0 12px;
}
</style>