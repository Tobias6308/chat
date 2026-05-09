<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { adminApi } from '@/utils/api'

const messages = ref<any[]>([])
const loading = ref(true)
const searchForm = ref({ conversationId: '', senderId: '' })
const pagination = ref({ page: 1, limit: 20, total: 0 })

onMounted(async () => {
  await loadMessages()
})

async function loadMessages() {
  loading.value = true
  try {
    const data: any = await adminApi.getMessages({
      limit: pagination.value.limit,
      skip: (pagination.value.page - 1) * pagination.value.limit,
      conversationId: searchForm.value.conversationId || undefined,
      senderId: searchForm.value.senderId || undefined
    })
    messages.value = data?.messages || []
    pagination.value.total = data?.total || 0
  } catch (e) {
    console.error('Failed to load messages:', e)
  } finally {
    loading.value = false
  }
}

async function handleSearch() {
  pagination.value.page = 1
  await loadMessages()
}

function handlePageChange(page: number) {
  pagination.value.page = page
  loadMessages()
}

function handleSizeChange(size: number) {
  pagination.value.limit = size
  pagination.value.page = 1
  loadMessages()
}

async function handleDeleteMessage(msg: any) {
  await adminApi.deleteMessage(msg.id)
  messages.value = messages.value.filter(m => m.id !== msg.id)
}
</script>

<template>
  <div>
    <h2>消息管理</h2>
    <el-form :inline="true" :model="searchForm" style="margin-bottom: 16px">
      <el-form-item label="会话ID">
        <el-input v-model="searchForm.conversationId" placeholder="请输入会话ID" clearable style="width: 200px" />
      </el-form-item>
      <el-form-item label="发送者ID">
        <el-input v-model="searchForm.senderId" placeholder="请输入发送者ID" clearable style="width: 200px" />
      </el-form-item>
      <el-form-item>
        <el-button type="primary" @click="handleSearch">搜索</el-button>
      </el-form-item>
    </el-form>
    <el-table :data="messages" v-loading="loading">
      <el-table-column prop="id" label="ID" width="180" />
      <el-table-column prop="conversationId" label="会话ID" width="180" />
      <el-table-column prop="senderId" label="发送者ID" width="180" />
      <el-table-column prop="contentType" label="类型" width="80">
        <template #default="{ row }">
          <el-tag v-if="row.contentType === 'text'">文本</el-tag>
          <el-tag v-else-if="row.contentType === 'image'">图片</el-tag>
          <el-tag v-else-if="row.contentType === 'file'">文件</el-tag>
          <el-tag v-else>{{ row.contentType }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="content" label="内容" />
      <el-table-column prop="createdAt" label="发送时间" width="180" />
      <el-table-column label="操作" width="100">
        <template #default="{ row }">
          <el-button type="danger" text size="small" @click="handleDeleteMessage(row)">
            删除
          </el-button>
        </template>
      </el-table-column>
    </el-table>
    <div class="pagination-wrap">
      <el-pagination
        v-model:current-page="pagination.page"
        v-model:page-size="pagination.limit"
        :page-sizes="[10, 20, 50, 100]"
        :total="pagination.total"
        layout="total, sizes, prev, pager, next, jumper"
        @current-change="handlePageChange"
        @size-change="handleSizeChange"
      />
    </div>
  </div>
</template>

<style scoped>
.pagination-wrap {
  margin-top: 20px;
  display: flex;
  justify-content: flex-end;
}
</style>