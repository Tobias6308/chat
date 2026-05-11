<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { adminApi, getFullFileUrl } from '@/utils/api'

interface Session {
  id: string
  userId: string
  userName: string
  serviceId: string
  serviceName: string
  status: string
  chatStartAt: number
  endedAt: number
  createdAt: number
  rating?: number
  ratingComment?: string
  internalNote?: string
  tags?: string[]
}

interface Message {
  id: string
  senderId: string
  content: string
  contentType: string
  status: string
  createdAt: number
  isService?: boolean
}

const sessions = ref<Session[]>([])
const loading = ref(true)
const pagination = ref({ page: 1, limit: 20, total: 0 })
const statusFilter = ref('')

const showDetail = ref(false)
const detailSession = ref<Session | null>(null)
const detailMessages = ref<Message[]>([])
const detailLoading = ref(false)
const imagePreviewVisible = ref(false)
const previewImageUrl = ref('')

function previewImage(url: string) {
  previewImageUrl.value = getImageUrl(url)
  imagePreviewVisible.value = true
}

onMounted(async () => {
  await loadHistory()
})

async function loadHistory() {
  loading.value = true
  try {
    const data: any = await adminApi.getServiceSessions({
      page: pagination.value.page - 1,
      limit: pagination.value.limit,
      status: statusFilter.value || undefined
    })
    sessions.value = data?.sessions || []
    pagination.value.total = data?.total || 0
  } catch (e) {
    console.error('Failed to load history:', e)
  } finally {
    loading.value = false
  }
}

function handleStatusChange() {
  pagination.value.page = 1
  loadHistory()
}

function handlePageChange(newPage: number) {
  pagination.value.page = newPage
  loadHistory()
}

async function viewSession(session: Session) {
  detailSession.value = session
  detailMessages.value = []
  showDetail.value = true
  detailLoading.value = true
  try {
    const data: any = await adminApi.getSessionMessages(session.id, { limit: 100 })
    detailMessages.value = data?.messages || []
  } catch (e) {
    console.error('Failed to load messages:', e)
  } finally {
    detailLoading.value = false
  }
}

function formatTime(timestamp: number) {
  if (!timestamp) return '-'
  return new Date(timestamp).toLocaleString('zh-CN')
}

function getDuration(start: number, end: number) {
  if (!start || !end) return '-'
  const minutes = Math.floor((end - start) / 1000 / 60)
  return minutes + ' 分钟'
}

function getImageUrl(url: string) {
  return getFullFileUrl(url)
}
</script>

<template>
  <div>
    <h2>会话历史</h2>

    <div class="filter-bar">
      <el-select v-model="statusFilter" placeholder="选择状态" @change="handleStatusChange" clearable>
        <el-option label="全部" value="" />
        <el-option label="排队中" value="waiting" />
        <el-option label="进行中" value="chatting" />
        <el-option label="已结束" value="ended" />
      </el-select>
    </div>

    <el-table :data="sessions" v-loading="loading" @row-click="viewSession">
      <el-table-column prop="id" label="会话ID" width="180" />
      <el-table-column prop="userName" label="用户" />
      <el-table-column prop="serviceName" label="客服" />
      <el-table-column prop="status" label="状态" width="100">
        <template #default="{ row }">
          <el-tag :type="row.status === 'chatting' ? 'success' : row.status === 'waiting' ? 'warning' : 'info'">
            {{ row.status === 'waiting' ? '排队中' : row.status === 'chatting' ? '进行中' : row.status === 'ended' ? '已结束' : row.status }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="开始时间" width="180">
        <template #default="{ row }">{{ formatTime(row.chatStartAt || row.createdAt) }}</template>
      </el-table-column>
      <el-table-column label="结束时间" width="180">
        <template #default="{ row }">{{ formatTime(row.endedAt) }}</template>
      </el-table-column>
      <el-table-column label="时长" width="100">
        <template #default="{ row }">{{ getDuration(row.chatStartAt, row.endedAt) }}</template>
      </el-table-column>
      <el-table-column label="评分" width="80">
        <template #default="{ row }">
          <span v-if="row.rating">{{ row.rating }}⭐</span>
          <span v-else class="text-gray-400">-</span>
        </template>
      </el-table-column>
      <el-table-column label="备注" min-width="150">
        <template #default="{ row }">
          <el-tooltip :content="row.ratingComment || row.internalNote || '-'" placement="top">
            <span class="truncate block max-w-[120px]">{{ row.internalNote || row.ratingComment || '-' }}</span>
          </el-tooltip>
        </template>
      </el-table-column>
    </el-table>

    <div class="pagination-wrap">
      <el-pagination
        v-model:current-page="pagination.page"
        v-model:page-size="pagination.limit"
        :total="pagination.total"
        layout="total, prev, pager, next"
        @current-change="handlePageChange"
      />
    </div>

    <!-- 详情弹窗 -->
    <el-dialog
      v-model="showDetail"
      :title="`会话详情 - ${detailSession?.userName}`"
      width="700px"
    >
      <div v-if="detailLoading" class="text-center py-4">加载中...</div>
      <div v-else-if="detailMessages.length === 0" class="text-center py-4 text-gray-500">暂无消息</div>
      <div v-else class="message-list max-h-96 overflow-y-auto flex flex-col gap-2 p-2">
        <div
          v-for="msg in detailMessages"
          :key="msg.id"
          class="flex"
          :class="msg.isService ? 'justify-end' : 'justify-start'"
        >
          <div
            class="max-w-[70%] rounded-lg p-2"
            :class="msg.isService ? 'bg-blue-500 text-white' : 'bg-gray-100'"
          >
            <div v-if="msg.contentType === 'text' || !msg.contentType">{{ msg.content }}</div>
            <img
              v-else-if="msg.contentType === 'image'"
              :src="getImageUrl(msg.content)"
              class="w-20 h-20 object-cover rounded cursor-pointer hover:opacity-80 transition-opacity inline-block"
              style="width: 80px; height: 80px; object-fit: cover;"
              @click="previewImage(msg.content)"
            />
            <div class="text-xs mt-1" :class="msg.isService ? 'text-white/70' : 'text-gray-400'">
              {{ formatTime(msg.createdAt) }}
            </div>
          </div>
        </div>
      </div>
    </el-dialog>

    <!-- 图片预览弹窗 -->
    <el-dialog v-model="imagePreviewVisible" title="图片预览" width="600px" center>
      <div class="text-center">
        <img :src="previewImageUrl" class="max-w-full max-h-[500px] object-contain" />
      </div>
    </el-dialog>
  </div>
</template>

<style scoped>
.pagination-wrap {
  margin-top: 20px;
  display: flex;
  justify-content: flex-end;
}
.message-list {
  display: flex;
  flex-direction: column;
}
.message-list .flex {
  display: flex;
}
.message-list .justify-end {
  justify-content: flex-end;
}
.message-list .justify-start {
  justify-content: flex-start;
}
.message-list img {
  width: 80px !important;
  height: 80px !important;
  object-fit: cover !important;
}
</style>