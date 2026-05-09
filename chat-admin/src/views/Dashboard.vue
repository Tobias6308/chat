<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { adminApi } from '@/utils/api'

const stats = ref<any>(null)
const loading = ref(true)

onMounted(async () => {
  try {
    const data = await adminApi.getStats()
    stats.value = data
  } catch (e) {
    console.error('Failed to load stats:', e)
  } finally {
    loading.value = false
  }
})
</script>

<template>
  <div>
    <h2>数据概览</h2>
    <div v-if="loading">加载中...</div>
    <el-row v-else :gutter="20">
      <el-col :span="6">
        <el-card class="stat-card">
          <div class="stat-value">{{ stats?.userCount || 0 }}</div>
          <div class="stat-label">用户总数</div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card class="stat-card">
          <div class="stat-value">{{ stats?.groupCount || 0 }}</div>
          <div class="stat-label">群组总数</div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card class="stat-card">
          <div class="stat-value">{{ stats?.conversationCount || 0 }}</div>
          <div class="stat-label">会话总数</div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card class="stat-card">
          <div class="stat-value">{{ stats?.messageCount || 0 }}</div>
          <div class="stat-label">消息总数</div>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<style scoped>
.stat-card {
  text-align: center;
  padding: 20px;
}
.stat-value {
  font-size: 32px;
  font-weight: bold;
  color: #409eff;
}
.stat-label {
  margin-top: 10px;
  color: #666;
}
</style>