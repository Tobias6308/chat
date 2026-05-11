<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { adminApi } from '@/utils/api'

const stats = ref<any>({})
const performance = ref<any>({})
const loading = ref(true)

onMounted(async () => {
  await loadStats()
  await loadPerformance()
})

async function loadStats() {
  try {
    const data: any = await adminApi.getServiceStats()
    stats.value = data || {}
  } catch (e) {
    console.error('Failed to load stats:', e)
  } finally {
    loading.value = false
  }
}

async function loadPerformance() {
  try {
    const data: any = await adminApi.getServicePerformance()
    performance.value = data || {}
  } catch (e) {
    console.error('Failed to load performance:', e)
  }
}
</script>

<template>
  <div v-loading="loading">
    <h2>客服数据统计</h2>
    
    <el-row :gutter="20" class="stats-row">
      <el-col :span="6">
        <div class="stat-card">
          <div class="stat-value">{{ stats.totalSessions || 0 }}</div>
          <div class="stat-label">总会话数</div>
        </div>
      </el-col>
      <el-col :span="6">
        <div class="stat-card">
          <div class="stat-value">{{ stats.activeSessions || 0 }}</div>
          <div class="stat-label">进行中</div>
        </div>
      </el-col>
      <el-col :span="6">
        <div class="stat-card">
          <div class="stat-value">{{ stats.finishedSessions || 0 }}</div>
          <div class="stat-label">已完成</div>
        </div>
      </el-col>
      <el-col :span="6">
        <div class="stat-card">
          <div class="stat-value">{{ stats.queueSize || 0 }}</div>
          <div class="stat-label">等待队列</div>
        </div>
      </el-col>
    </el-row>

    <el-row :gutter="20" class="stats-row">
      <el-col :span="6">
        <div class="stat-card">
          <div class="stat-value">{{ stats.waitingSessions || 0 }}</div>
          <div class="stat-label">等待中</div>
        </div>
      </el-col>
      <el-col :span="6">
        <div class="stat-card">
          <div class="stat-value">{{ stats.onlineServices || 0 }}</div>
          <div class="stat-label">在线客服</div>
        </div>
      </el-col>
    </el-row>

    <el-divider />

    <h3>我的绩效</h3>
    <el-row :gutter="20" class="stats-row">
      <el-col :span="8">
        <div class="stat-card highlight">
          <div class="stat-value">{{ performance.totalSessions || 0 }}</div>
          <div class="stat-label">总会话数</div>
        </div>
      </el-col>
      <el-col :span="8">
        <div class="stat-card highlight">
          <div class="stat-value">{{ performance.finishedSessions || 0 }}</div>
          <div class="stat-label">已完成</div>
        </div>
      </el-col>
      <el-col :span="8">
        <div class="stat-card highlight">
          <div class="stat-value">{{ performance.avgDuration || 0 }}</div>
          <div class="stat-label">平均时长(分钟)</div>
        </div>
      </el-col>
    </el-row>
  </div>
</template>

<style scoped>
.stats-row {
  margin-bottom: 20px;
}

.stat-card {
  background: #fff;
  border-radius: 8px;
  padding: 24px;
  text-align: center;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.1);
}

.stat-card.highlight {
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  color: #fff;
}

.stat-value {
  font-size: 32px;
  font-weight: 600;
  margin-bottom: 8px;
}

.stat-label {
  font-size: 14px;
  color: #666;
}

.stat-card.highlight .stat-label {
  color: rgba(255, 255, 255, 0.8);
}
</style>