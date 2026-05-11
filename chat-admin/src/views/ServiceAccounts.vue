<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { adminApi } from '@/utils/api'

interface Service {
  id: string
  username: string
  nickname: string
  status: string
  maxChats: number
  currentChats: number
}

const services = ref<Service[]>([])
const loading = ref(true)
const showDialog = ref(false)
const form = ref({ username: '', password: '', nickname: '' })

onMounted(async () => {
  await loadServices()
})

async function loadServices() {
  loading.value = true
  try {
    const data: any = await adminApi.getServiceList()
    services.value = data?.services || []
  } catch (e) {
    console.error('Failed to load services:', e)
  } finally {
    loading.value = false
  }
}

async function handleCreate() {
  if (!form.value.username || !form.value.password) {
    ElMessage.warning('请填写用户名和密码')
    return
  }
  const result: any = await adminApi.createService(form.value)
  if (result.success) {
    ElMessage.success('创建成功')
    showDialog.value = false
    form.value = { username: '', password: '', nickname: '' }
    await loadServices()
  } else {
    ElMessage.error(result.message)
  }
}

async function handleUpdateStatus(_id: string, status: string) {
  try {
    await adminApi.updateServiceStatus({ status })
    ElMessage.success('状态已更新')
    await loadServices()
  } catch (e) {
    console.error('Failed to update status:', e)
    ElMessage.error('更新失败')
  }
}

async function handleResetPassword(id: string) {
  try {
    await ElMessageBox.confirm('确定要将该客服密码重置为 123456 吗？', '提示')
    await adminApi.resetServicePassword(id)
    ElMessage.success('密码已重置为 123456')
  } catch (e) {
    console.error('Failed to reset password:', e)
  }
}
</script>

<template>
  <div>
    <div class="toolbar">
      <h2>客服账号管理</h2>
      <el-button type="primary" @click="showDialog = true">添加客服</el-button>
    </div>

    <el-table :data="services" v-loading="loading">
      <el-table-column prop="nickname" label="昵称" />
      <el-table-column prop="username" label="用户名" />
      <el-table-column prop="status" label="状态" width="100">
        <template #default="{ row }">
          <el-tag :type="row.status === 'online' ? 'success' : row.status === 'busy' ? 'warning' : 'info'">
            {{ row.status === 'online' ? '在线' : row.status === 'busy' ? '忙碌' : '离线' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="maxChats" label="最大接待数" width="100" />
      <el-table-column prop="currentChats" label="当前接待" width="100" />
      <el-table-column label="操作" width="200">
        <template #default="{ row }">
          <el-button 
            v-if="row.status !== 'online'" 
            type="success" 
            size="small" 
            text
            @click="handleUpdateStatus(row.id, 'online')"
          >
            设为在线
          </el-button>
          <el-button 
            v-if="row.status === 'online'" 
            type="warning" 
            size="small" 
            text
            @click="handleUpdateStatus(row.id, 'busy')"
          >
            设为忙碌
          </el-button>
          <el-button 
            type="primary" 
            size="small" 
            text
            @click="handleResetPassword(row.id)"
          >
            重置密码
          </el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="showDialog" title="添加客服" width="400px">
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