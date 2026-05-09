<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { adminApi } from '@/utils/api'

const admins = ref<any[]>([])
const loading = ref(true)
const showDialog = ref(false)
const form = ref({ username: '', password: '', nickname: '' })

onMounted(async () => {
  await loadAdmins()
})

async function loadAdmins() {
  loading.value = true
  try {
    const data: any = await adminApi.getAdmins()
    admins.value = data?.admins || []
  } catch (e) {
    console.error('Failed to load admins:', e)
  } finally {
    loading.value = false
  }
}

async function handleCreate() {
  if (!form.value.username || !form.value.password) {
    ElMessage.warning('请填写用户名和密码')
    return
  }
  const result: any = await adminApi.createAdmin(form.value)
  if (result.success) {
    ElMessage.success('创建成功')
    showDialog.value = false
    form.value = { username: '', password: '', nickname: '' }
    await loadAdmins()
  } else {
    ElMessage.error(result.message)
  }
}

async function handleDelete(id: string) {
  await ElMessageBox.confirm('确定删除该管理员吗？', '提示')
  const result: any = await adminApi.deleteAdmin(id)
  if (result.success) {
    ElMessage.success('删除成功')
    await loadAdmins()
  } else {
    ElMessage.error(result.message)
  }
}
</script>

<template>
  <div>
    <div class="toolbar">
      <h2>管理员管理</h2>
      <el-button type="primary" @click="showDialog = true">添加管理员</el-button>
    </div>

    <el-table :data="admins" v-loading="loading">
      <el-table-column prop="username" label="用户名" />
      <el-table-column prop="nickname" label="昵称" />
      <el-table-column prop="roles" label="角色">
        <template #default="{ row }">{{ row.roles?.join(', ') }}</template>
      </el-table-column>
      <el-table-column prop="createdAt" label="创建时间">
        <template #default="{ row }">{{ new Date(row.createdAt).toLocaleString() }}</template>
      </el-table-column>
      <el-table-column label="操作" width="120">
        <template #default="{ row }">
          <el-button type="danger" text @click="handleDelete(row.id)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="showDialog" title="添加管理员" width="400px">
      <el-form label-width="80px">
        <el-form-item label="用户名">
          <el-input v-model="form.username" />
        </el-form-item>
        <el-form-item label="密码">
          <el-input v-model="form.password" type="password" />
        </el-form-item>
        <el-form-item label="昵称">
          <el-input v-model="form.nickname" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showDialog = false">取消</el-button>
        <el-button type="primary" @click="handleCreate">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
}
</style>