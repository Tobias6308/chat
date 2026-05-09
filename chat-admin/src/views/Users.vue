<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { adminApi } from '@/utils/api'

const users = ref<any[]>([])
const loading = ref(true)
const pagination = ref({ page: 1, limit: 20, total: 0 })

onMounted(async () => {
  await loadUsers()
})

async function loadUsers() {
  loading.value = true
  try {
    const data: any = await adminApi.getUsers({ limit: pagination.value.limit, skip: (pagination.value.page - 1) * pagination.value.limit })
    users.value = data?.users || []
    pagination.value.total = data?.total || 0
  } catch (e) {
    console.error('Failed to load users:', e)
  } finally {
    loading.value = false
  }
}

function handlePageChange(page: number) {
  pagination.value.page = page
  loadUsers()
}

function handleSizeChange(size: number) {
  pagination.value.limit = size
  pagination.value.page = 1
  loadUsers()
}

async function handleToggleEnable(user: any) {
  await adminApi.updateUserEnable(user.id, !user.enabled)
  user.enabled = !user.enabled
}
</script>

<template>
  <div>
    <h2>用户管理</h2>
    <el-table :data="users" v-loading="loading">
      <el-table-column prop="id" label="ID" width="180" />
      <el-table-column prop="username" label="用户名" />
      <el-table-column prop="nickname" label="昵称" />
      <el-table-column prop="avatar" label="头像" width="60">
        <template #default="{ row }">
          <img v-if="row.avatar" :src="row.avatar" style="width: 40px; height: 40px; border-radius: 50%" />
        </template>
      </el-table-column>
      <el-table-column prop="enabled" label="状态" width="80">
        <template #default="{ row }">
          <el-tag :type="row.enabled ? 'success' : 'danger'">{{ row.enabled ? '正常' : '禁用' }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="120">
        <template #default="{ row }">
          <el-button type="primary" text size="small" @click="handleToggleEnable(row)">
            {{ row.enabled ? '禁用' : '启用' }}
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