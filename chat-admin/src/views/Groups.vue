<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { adminApi } from '@/utils/api'

const groups = ref<any[]>([])
const loading = ref(true)
const pagination = ref({ page: 1, limit: 20, total: 0 })

onMounted(async () => {
  await loadGroups()
})

async function loadGroups() {
  loading.value = true
  try {
    const data: any = await adminApi.getGroups({ limit: pagination.value.limit, skip: (pagination.value.page - 1) * pagination.value.limit })
    groups.value = data?.groups || []
    pagination.value.total = data?.total || 0
  } catch (e) {
    console.error('Failed to load groups:', e)
  } finally {
    loading.value = false
  }
}

function handlePageChange(page: number) {
  pagination.value.page = page
  loadGroups()
}

function handleSizeChange(size: number) {
  pagination.value.limit = size
  pagination.value.page = 1
  loadGroups()
}

async function handleDeleteGroup(group: any) {
  await adminApi.deleteGroup(group.id)
  groups.value = groups.value.filter(g => g.id !== group.id)
}
</script>

<template>
  <div>
    <h2>群组管理</h2>
    <el-table :data="groups" v-loading="loading">
      <el-table-column prop="id" label="ID" width="180" />
      <el-table-column prop="name" label="群名称" />
      <el-table-column prop="avatar" label="头像" width="60">
        <template #default="{ row }">
          <img v-if="row.avatar" :src="row.avatar" style="width: 40px; height: 40px; border-radius: 50%" />
        </template>
      </el-table-column>
      <el-table-column prop="ownerId" label="群主ID" width="180" />
      <el-table-column prop="memberCount" label="成员数" width="80" />
      <el-table-column label="操作" width="120">
        <template #default="{ row }">
          <el-button type="danger" text size="small" @click="handleDeleteGroup(row)">
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