<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { adminApi } from '@/utils/api'

const conversations = ref<any[]>([])
const loading = ref(true)
const pagination = ref({ page: 1, limit: 20, total: 0 })

onMounted(async () => {
  await loadConversations()
})

async function loadConversations() {
  loading.value = true
  try {
    const data: any = await adminApi.getConversations({ limit: pagination.value.limit, skip: (pagination.value.page - 1) * pagination.value.limit })
    conversations.value = data?.conversations || []
    pagination.value.total = data?.total || 0
  } catch (e) {
    console.error('Failed to load conversations:', e)
  } finally {
    loading.value = false
  }
}

function handlePageChange(page: number) {
  pagination.value.page = page
  loadConversations()
}

function handleSizeChange(size: number) {
  pagination.value.limit = size
  pagination.value.page = 1
  loadConversations()
}

async function handleDeleteConversation(conv: any) {
  await adminApi.deleteConversation(conv.id)
  conversations.value = conversations.value.filter(c => c.id !== conv.id)
}
</script>

<template>
  <div>
    <h2>会话管理</h2>
    <el-table :data="conversations" v-loading="loading">
      <el-table-column prop="id" label="ID" width="180" />
      <el-table-column prop="type" label="类型" width="80">
        <template #default="{ row }">
          <el-tag>{{ row.type === 'private' ? '私聊' : '群聊' }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="name" label="名称" />
      <el-table-column prop="participants" label="参与者">
        <template #default="{ row }">
          {{ row.participants ? row.participants.join(', ') : '' }}
        </template>
      </el-table-column>
      <el-table-column prop="updatedAt" label="最后消息时间" width="180" />
      <el-table-column label="操作" width="120">
        <template #default="{ row }">
          <el-button type="danger" text size="small" @click="handleDeleteConversation(row)">
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