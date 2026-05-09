<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { adminApi } from '@/utils/api'

const friends = ref<any[]>([])
const loading = ref(true)
const pagination = ref({ page: 1, limit: 20, total: 0 })

onMounted(async () => {
  await loadFriends()
})

async function loadFriends() {
  loading.value = true
  try {
    const data: any = await adminApi.getFriends({ limit: pagination.value.limit, skip: (pagination.value.page - 1) * pagination.value.limit })
    friends.value = data?.friends || []
    pagination.value.total = data?.total || 0
  } catch (e) {
    console.error('Failed to load friends:', e)
  } finally {
    loading.value = false
  }
}

function handlePageChange(page: number) {
  pagination.value.page = page
  loadFriends()
}

function handleSizeChange(size: number) {
  pagination.value.limit = size
  pagination.value.page = 1
  loadFriends()
}

async function handleDeleteFriend(friend: any) {
  await adminApi.deleteFriend(friend.id)
  friends.value = friends.value.filter(f => f.id !== friend.id)
}
</script>

<template>
  <div>
    <h2>好友关系管理</h2>
    <el-table :data="friends" v-loading="loading">
      <el-table-column prop="id" label="ID" width="180" />
      <el-table-column prop="userId" label="用户ID" width="180" />
      <el-table-column prop="friendId" label="好友ID" width="180" />
      <el-table-column prop="createdAt" label="创建时间" width="180" />
      <el-table-column label="操作" width="120">
        <template #default="{ row }">
          <el-button type="danger" text size="small" @click="handleDeleteFriend(row)">
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