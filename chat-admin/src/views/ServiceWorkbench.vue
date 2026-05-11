<script setup lang="ts">
import { ref, onMounted, onUnmounted, computed, nextTick } from 'vue'
import { ElMessage } from 'element-plus'
import { Document, Picture } from '@element-plus/icons-vue'
import { adminApi, configApi, getFullFileUrl, setFileServerUrl } from '@/utils/api'
import request from '@/utils/request'
import { useWebSocket } from '@/composables/useWebSocket'

const ws = useWebSocket()

interface Session {
  id: string
  userId: string
  userName?: string
  status: string
  createdAt: string
  updatedAt: string
}

interface Message {
  id: string
  senderId: string
  content: string
  contentType: string
  createdAt: number
  isService?: boolean
  senderName?: string
  senderAvatar?: string
  status?: string
  fileName?: string
}

const currentStatus = ref('offline')
const sessions = ref<Session[]>([])
const activeSessionId = ref<string | null>(null)
const messages = ref<Message[]>([])
const messageIds = new Set<string>()
const newMessage = ref('')
const loading = ref(false)
const sending = ref(false)
const quickReplies = ref<any[]>([])
const showQuickReplyPanel = ref(false)
const availableServices = ref<any[]>([])
const showTransferDialog = ref(false)
const selectedTargetService = ref('')
const sessionNote = ref('')
const sessionTags = ref<string[]>([])
const showNotePanel = ref(false)
const queueList = ref<{ total: number; users: any[] }>({ total: 0, users: [] })
const typingUsers = ref<Map<string, number>>(new Map())
const messageContainerRef = ref<HTMLElement | null>(null)
const previewImageVisible = ref(false)
const previewImageUrl = ref('')

let typingTimeout: number | null = null

function scrollToBottom() {
  nextTick(() => {
    const container = messageContainerRef.value
    if (!container) return
    const scrollWrap = container.querySelector('.el-scrollbar__wrap') as HTMLElement
    if (scrollWrap) {
      scrollWrap.scrollTo({ top: scrollWrap.scrollHeight, behavior: 'smooth' })
    } else {
      container.scrollTo({ top: container.scrollHeight, behavior: 'smooth' })
    }
  })
}

const activeSession = computed(() => 
  sessions.value.find(s => s.id === activeSessionId.value)
)

function addMessage(msg: Message) {
  if (!msg.id || messageIds.has(msg.id)) return false
  messageIds.add(msg.id)
  messages.value.push(msg)
  return true
}

onMounted(async () => {
  const adminInfo = JSON.parse(sessionStorage.getItem('admin_info') || '{}')
  const adminToken = sessionStorage.getItem('admin_token')

  // 连接 WebSocket
  if (adminToken) {
    ws.connect(adminToken)
    ws.on('service_message_sent', handleServiceMessageSent)
    ws.on('service_new_message', handleServiceNewMessage)
    ws.on('service_typing', handleServiceTyping)
    ws.on('service_error', handleServiceError)
    ws.on('service_queue_update', handleQueueUpdate)
    ws.on('service_offline_messages_done', handleOfflineMessagesDone)
    ws.on('service_session_ended', handleServiceSessionEnded)
  }

  await loadSessions()
  await loadQuickReplies()
  await loadServiceList()
  await loadQueueList()

  // 加载配置文件服务器地址
  try {
    const config: any = await configApi.getConfig()
    if (config?.data?.fileServerUrl) {
      setFileServerUrl(config.data.fileServerUrl)
    }
  } catch (e) {
    console.error('Failed to load config:', e)
  }

  if (adminInfo.adminId) {
    const resp: any = await adminApi.getServiceList()
    const service = resp.services?.find((s: any) => s.id === adminInfo.adminId)
    if (service) {
      currentStatus.value = service.status || 'offline'
    }
  }
})

onUnmounted(() => {
  ws.off('service_message_sent', handleServiceMessageSent)
  ws.off('service_new_message', handleServiceNewMessage)
  ws.off('service_typing', handleServiceTyping)
  ws.off('service_error', handleServiceError)
  ws.off('service_queue_update', handleQueueUpdate)
  ws.off('service_offline_messages_done', handleOfflineMessagesDone)
  ws.off('service_session_ended', handleServiceSessionEnded)
  ws.disconnect()
})

async function loadSessions() {
  const adminInfo = JSON.parse(sessionStorage.getItem('admin_info') || '{}')
  try {
    const data: any = await adminApi.getServiceSessions({ serviceId: adminInfo.adminId })
    sessions.value = data?.sessions || []
  } catch (e) {
    console.error('Failed to load sessions:', e)
  }
}

async function loadQueueList() {
  try {
    const data: any = await adminApi.getQueueList()
    queueList.value = { total: data?.total || 0, users: data?.users || [] }
  } catch (e) {
    console.error('Failed to load queue list:', e)
  }
}

function handleServiceMessageSent({ payload }: any) {
  const { message } = payload.payload || {}
  if (message && activeSessionId.value) {
    const msgConversationId = String(message.conversationId || '')
    const msgUserId = msgConversationId.replace('service_', '')
    const activeId = String(activeSessionId.value)
    if (activeId.startsWith('service_' + msgUserId)) {
      if (addMessage(message)) scrollToBottom()
    }
  }
}

function handleServiceNewMessage({ payload }: any) {
  const { sessionId, message } = payload.payload || {}
  if (activeSessionId.value && String(sessionId) === String(activeSessionId.value)) {
    if (addMessage(message)) scrollToBottom()
  }
  loadSessions()
}

function handleServiceTyping({ payload }: any) {
  const { userId } = payload.payload || {}
  if (userId) {
    typingUsers.value.set(userId, Date.now())
    if (typingTimeout) clearTimeout(typingTimeout)
    typingTimeout = window.setTimeout(() => {
      typingUsers.value.delete(userId)
    }, 3000)
  }
}

function handleServiceError({ payload }: any) {
  console.error('Service error:', payload.payload?.message)
  ElMessage.error(payload.payload?.message || '服务错误')
}

function handleQueueUpdate({ payload }: any) {
  const { status, position } = payload.payload || {}
  if (status === 'waiting') {
    queueList.value.total = position || queueList.value.total
  } else if (status === 'chatting' || status === 'ended') {
    loadSessions()
    loadQueueList()
  }
}

function handleOfflineMessagesDone({ payload }: any) {
  const { count } = payload.payload || {}
  console.log(`接收到 ${count} 条离线消息`)
  ElMessage.success(`已接收 ${count} 条离线消息`)
  loadSessions()
}

function handleServiceSessionEnded({ payload }: any) {
  const { sessionId } = payload.payload || {}
  console.log(`会话已结束 sessionId: ${sessionId}`)
  ElMessage.warning('会话已结束')
  loadSessions()
  // 清除当前选中的会话
  if (activeSessionId.value && String(activeSessionId.value) === String(sessionId)) {
    activeSessionId.value = null
    messages.value = []
  }
}

function isUserTyping(userId: string): boolean {
  const lastTime = typingUsers.value.get(userId)
  return lastTime ? Date.now() - lastTime < 3000 : false
}

async function loadQuickReplies() {
  try {
    const data: any = await adminApi.getQuickReplies()
    quickReplies.value = data?.quickReplies || []
  } catch (e) {
    console.error('Failed to load quick replies:', e)
  }
}

function useQuickReply(content: string) {
  newMessage.value = content
  showQuickReplyPanel.value = false
}

async function loadServiceList() {
  try {
    const data: any = await adminApi.getServiceList()
    availableServices.value = (data?.services || []).filter((s: any) => s.status === 'online')
  } catch (e) {
    console.error('Failed to load service list:', e)
  }
}

async function handleTransfer() {
  if (!activeSessionId.value || !selectedTargetService.value) return
  
  try {
    const data: any = await adminApi.transferSession({
      sessionId: activeSessionId.value,
      toServiceId: selectedTargetService.value
    })
    if (data?.success) {
      ElMessage.success('会话已转移到 ' + data.serviceName)
      activeSessionId.value = null
      messages.value = []
      messageIds.clear()
      showTransferDialog.value = false
      selectedTargetService.value = ''
      await loadSessions()
    } else {
      ElMessage.error(data?.message || '转移失败')
    }
  } catch (e) {
    ElMessage.error('发送失败')
  } finally {
    sending.value = false
  }
}

async function handleImageSelect(file: any) {
  if (!activeSessionId.value) {
    ElMessage.warning('请先选择会话')
    return
  }

  const rawFile = file.raw
  if (!rawFile) return

  if (rawFile.size > 10 * 1024 * 1024) {
    ElMessage.warning('图片大小不能超过 10MB')
    return
  }

  sending.value = true
  try {
    const formData = new FormData()
    formData.append('file', rawFile)

    const result: any = await request.post('/chat/upload/image', formData, {
      headers: { 'Content-Type': 'multipart/form-data' }
    })

    if (result?.url) {
      ws.send({
        type: 'service',
        payload: {
          action: 'admin_send',
          sessionId: activeSessionId.value,
          content: result.url,
          contentType: 'image'
        }
      })
    }
  } catch (e) {
    console.error('Failed to upload image:', e)
    ElMessage.error('图片上传失败')
  } finally {
    sending.value = false
  }
}

function handleTyping() {
  if (typingTimeout) clearTimeout(typingTimeout)
  typingTimeout = window.setTimeout(() => {
    if (activeSessionId.value) {
      adminApi.sendTyping().catch(() => {})
    }
  }, 1000)
}

async function handleEndSession() {
  if (!activeSessionId.value) return
  
  try {
    await adminApi.endServiceSession(activeSessionId.value)
    activeSessionId.value = null
    messages.value = []
    messageIds.clear()
    sessionNote.value = ''
    sessionTags.value = []
    await loadSessions()
  } catch (e) {
    console.error('Failed to end session:', e)
  }
}

async function saveSessionNote() {
  if (!activeSessionId.value) return
  try {
    await adminApi.updateSessionNote(activeSessionId.value, sessionNote.value)
    ElMessage.success('备注已保存')
  } catch (e) {
    console.error('Failed to save note:', e)
    ElMessage.error('保存失败')
  }
}

async function saveSessionTags() {
  if (!activeSessionId.value) return
  try {
    await adminApi.updateSessionTags(activeSessionId.value, sessionTags.value)
    ElMessage.success('标签已保存')
  } catch (e) {
    console.error('Failed to save tags:', e)
    ElMessage.error('保存失败')
  }
}

function formatTime(dateStr: string | number) {
  if (!dateStr) return ''
  const date = new Date(dateStr)
  return date.toLocaleString('zh-CN')
}

function handlePreviewImage(url: string) {
  previewImageUrl.value = url
  previewImageVisible.value = true
}

async function selectSession(session: Session) {
  activeSessionId.value = session.id
  sessionNote.value = (session as any).note || ''
  sessionTags.value = (session as any).tags || []
  await loadMessages(session.id)
}

async function loadMessages(sessionId: string) {
  loading.value = true
  try {
    const data: any = await adminApi.getSessionMessages(sessionId, { limit: 50 })
    const loadedMessages = data?.messages || []
    messageIds.clear()
    loadedMessages.forEach((msg: Message) => {
      if (msg.id) messageIds.add(msg.id)
    })
    messages.value = loadedMessages
    scrollToBottom()
  } catch (e) {
    console.error('Failed to load messages:', e)
  } finally {
    loading.value = false
  }
}

async function handleStatusChange(status: string) {
  try {
    await adminApi.updateServiceStatus({ status })
    currentStatus.value = status
  } catch (e) {
    console.error('Failed to update status:', e)
  }
}

async function handleGetNextUser() {
  try {
    const data: any = await adminApi.getNextUser()
    if (data?.success && data?.sessionId) {
      await loadSessions()
      activeSessionId.value = data.sessionId
      await loadMessages(data.sessionId)
    } else if (data?.message) {
      ElMessage.warning(data.message)
    }
  } catch (e) {
    console.error('Failed to get next user:', e)
  }
}

async function handleSendMessage() {
  if (!newMessage.value.trim() || !activeSessionId.value) return

  if (ws.status.value !== 'connected') {
    ElMessage.warning('网络未连接，请检查网络')
    return
  }

  sending.value = true
  try {
    ws.send({
      type: 'service',
      payload: {
        action: 'admin_send',
        sessionId: activeSessionId.value,
        content: newMessage.value,
        contentType: 'text'
      }
    })
    newMessage.value = ''
  } catch (e) {
    ElMessage.error('发送失败')
  } finally {
    sending.value = false
  }
}
</script>

<template>
  <div class="service-workbench">
    <div class="workbench-header">
      <h2>客服工作台</h2>
      <div class="status-controls">
        <div class="status-indicator">
          <span class="status-dot" :class="currentStatus"></span>
          <span class="status-text">{{ currentStatus === 'online' ? '在线' : currentStatus === 'busy' ? '忙碌' : '离线' }}</span>
        </div>
        <el-select v-model="currentStatus" @change="handleStatusChange" placeholder="选择状态" style="width: 100px">
          <el-option label="在线" value="online" />
          <el-option label="忙碌" value="busy" />
          <el-option label="离线" value="offline" />
        </el-select>
        <el-button type="primary" @click="handleGetNextUser" size="small">
          接待下一个
        </el-button>
        <el-button @click="loadQueueList" :loading="loading" size="small">
          刷新队列
        </el-button>
        <span class="queue-info">等待: {{ queueList.total }}人</span>
      </div>
    </div>

    <!-- 等待队列预览 -->
    <div v-if="queueList.users.length > 0" class="queue-preview">
      <el-collapse>
        <el-collapse-item title="等待队列预览 (最近10人)" name="queue">
          <div class="queue-list">
            <div v-for="(user, index) in queueList.users" :key="user.userId" class="queue-item">
              <span class="queue-index">{{ index + 1 }}</span>
              <span class="queue-name">{{ user.userName || user.userId }}</span>
            </div>
          </div>
        </el-collapse-item>
      </el-collapse>
    </div>

    <div class="workbench-content">
      <div class="session-list">
        <div class="session-header">
          <h3>会话列表 ({{ sessions.length }})</h3>
          <el-button size="small" @click="loadSessions" :loading="loading">
            刷新
          </el-button>
        </div>
        <el-scrollbar>
          <div 
            v-for="session in sessions" 
            :key="session.id"
            class="session-item"
            :class="{ active: session.id === activeSessionId }"
            @click="selectSession(session)"
          >
            <div class="session-info">
              <span class="user-name">
                {{ session.userName || session.userId }}
                <span v-if="isUserTyping(session.userId)" class="typing-indicator">正在输入...</span>
              </span>
              <el-tag size="small" :type="session.status === 'chatting' ? 'success' : 'info'">
                {{ session.status === 'chatting' ? '进行中' : '已结束' }}
              </el-tag>
            </div>
            <div class="session-time">{{ formatTime(session.updatedAt) }}</div>
          </div>
          <div v-if="sessions.length === 0" class="empty-tip">
            暂无会话
          </div>
        </el-scrollbar>
      </div>

      <div class="chat-area">
        <template v-if="activeSession">
          <div class="chat-header">
            <span>与 {{ activeSession.userName || activeSession.userId }} 的会话</span>
            <div class="chat-actions">
              <el-button type="info" size="small" @click="showNotePanel = !showNotePanel">
                备注
              </el-button>
              <el-button type="warning" size="small" @click="showTransferDialog = true">
                转移会话
              </el-button>
              <el-button type="danger" size="small" @click="handleEndSession">
                结束会话
              </el-button>
            </div>
          </div>
          <div class="messages-container" ref="messageContainerRef">
            <el-scrollbar>
              <div v-for="msg in messages" :key="msg.id" class="message-item" :class="{ 'is-service': msg.isService, 'is-user': !msg.isService }">
                <div class="message-header">
                  <el-avatar v-if="msg.senderAvatar" :size="28" :src="msg.senderAvatar" class="message-avatar"/>
                  <el-avatar v-else :size="28" class="message-avatar">{{ msg.senderName?.charAt(0) || (msg.isService ? '客' : '用') }}</el-avatar>
                  <span class="message-sender">{{ msg.senderName }}</span>
                  <span class="message-time">{{ formatTime(msg.createdAt) }}</span>
                  <span v-if="msg.status" class="message-status" :class="msg.status">{{ msg.status === 'sent' ? '已发送' : msg.status === 'delivered' ? '已送达' : msg.status === 'read' ? '已读' : '' }}</span>
                </div>
                <!-- 文本消息 -->
                <div v-if="msg.contentType === 'text' || !msg.contentType" class="message-content">{{ msg.content }}</div>
                <!-- 图片消息 -->
                <div v-else-if="msg.contentType === 'image'" class="message-image">
                  <img
                    :src="getFullFileUrl(msg.content)"
                    class="chat-image-img"
                    @click="handlePreviewImage(getFullFileUrl(msg.content))"
                  />
                </div>
                <!-- 文件消息 -->
                <div v-else-if="msg.contentType === 'file'" class="message-file">
                  <el-button type="primary" link :href="msg.content" target="_blank">
                    <el-icon><Document /></el-icon>
                    {{ msg.fileName || '文件' }}
                  </el-button>
                </div>
              </div>
              <div v-if="messages.length === 0" class="empty-tip">
                暂无消息
              </div>
            </el-scrollbar>
          </div>
          <div class="message-input">
            <el-upload
              class="image-uploader"
              action=""
              :show-file-list="false"
              :auto-upload="false"
              :on-change="handleImageSelect"
              accept="image/*"
            >
              <el-button size="small">
                <el-icon><Picture /></el-icon>
              </el-button>
            </el-upload>
            <el-popover
              placement="top"
              :width="300"
              trigger="click"
              v-model:visible="showQuickReplyPanel"
            >
              <template #reference>
                <el-button size="small">快捷回复</el-button>
              </template>
              <div class="quick-reply-list">
                <div 
                  v-for="reply in quickReplies" 
                  :key="reply.id"
                  class="quick-reply-item"
                  @click="useQuickReply(reply.content)"
                >
                  <div class="quick-reply-title">{{ reply.title }}</div>
                  <div class="quick-reply-content">{{ reply.content }}</div>
                </div>
              </div>
            </el-popover>
            <el-input 
              v-model="newMessage" 
              placeholder="输入回复消息..." 
              @keyup.enter="handleSendMessage"
              @input="handleTyping"
              :disabled="sending"
            />
            <el-button type="primary" @click="handleSendMessage" :loading="sending" :disabled="sending">
              {{ sending ? '发送中' : '发送' }}
            </el-button>
          </div>
        </template>
        <div v-else class="no-session">
          <p>请选择一个会话或点击"接待下一个"开始工作</p>
        </div>
      </div>

      <!-- 备注面板 -->
      <div v-if="showNotePanel && activeSession" class="note-panel">
        <div class="note-header">
          <span>会话备注</span>
          <el-button text size="small" @click="showNotePanel = false">关闭</el-button>
        </div>
        <div class="note-content">
          <el-input
            v-model="sessionNote"
            type="textarea"
            :rows="4"
            placeholder="输入备注信息..."
          />
          <el-button type="primary" size="small" class="mt-2" @click="saveSessionNote">
            保存备注
          </el-button>
          <div class="mt-4">
            <div class="tags-label">标签</div>
            <el-select v-model="sessionTags" multiple placeholder="选择标签" @change="saveSessionTags" style="width: 100%">
              <el-option label="投诉" value="complaint" />
              <el-option label="咨询" value="inquiry" />
              <el-option label="售后" value="after-sales" />
              <el-option label="VIP" value="vip" />
              <el-option label="紧急" value="urgent" />
            </el-select>
          </div>
        </div>
      </div>
    </div>

    <el-dialog v-model="showTransferDialog" title="转移会话" width="400px">
      <el-form label-width="80px">
        <el-form-item label="选择客服">
          <el-select v-model="selectedTargetService" placeholder="请选择目标客服" style="width: 100%">
            <el-option
              v-for="service in availableServices"
              :key="service.id"
              :label="service.nickname"
              :value="service.id"
            />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showTransferDialog = false">取消</el-button>
        <el-button type="primary" @click="handleTransfer">确认转移</el-button>
      </template>
    </el-dialog>

    <!-- 图片预览弹窗 -->
    <el-dialog
      v-model="previewImageVisible"
      title="图片预览"
      width="80%"
      :append-to-body="true"
      destroy-on-close
    >
      <div class="preview-image-container">
        <img :src="previewImageUrl" class="preview-image" />
      </div>
    </el-dialog>
  </div>
</template>

<style scoped>
.service-workbench {
  height: 100%;
  display: flex;
  flex-direction: column;
}

.workbench-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 16px 24px;
  border-bottom: 1px solid #eee;
  background: #fff;
}

.workbench-header h2 {
  margin: 0;
  font-size: 18px;
}

.status-controls {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: nowrap;
  flex-shrink: 0;
}

.status-indicator {
  display: flex;
  align-items: center;
  gap: 6px;
}

.status-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
}

.status-dot.online {
  background: #67c23a;
}

.status-dot.busy {
  background: #e6a23c;
}

.status-dot.offline {
  background: #909399;
}

.queue-info {
  font-size: 14px;
  color: #606266;
}

.queue-preview {
  padding: 0 24px;
  background: #fff;
  border-bottom: 1px solid #eee;
}

.queue-list {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  padding: 8px 0;
}

.queue-item {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 4px 10px;
  background: #f5f7fa;
  border-radius: 4px;
  font-size: 13px;
}

.queue-index {
  color: #909399;
  min-width: 20px;
}

.queue-name {
  color: #303133;
}

.workbench-content {
  flex: 1;
  display: flex;
  overflow: hidden;
}

.session-list {
  width: 280px;
  border-right: 1px solid #eee;
  display: flex;
  flex-direction: column;
}

.session-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 12px 16px;
  border-bottom: 1px solid #eee;
}

.session-header h3 {
  margin: 0;
  font-size: 14px;
}

.session-item {
  padding: 12px 16px;
  cursor: pointer;
  border-bottom: 1px solid #f5f5f5;
}

.session-item:hover {
  background-color: #f9f9f9;
}

.session-item.active {
  background-color: #e6f7ff;
}

.session-info {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 4px;
}

.typing-indicator {
  color: #909399;
  font-size: 12px;
  margin-left: 8px;
  font-style: italic;
}

.user-name {
  font-weight: 500;
}

.session-time {
  font-size: 12px;
  color: #999;
}

.chat-area {
  flex: 1;
  display: flex;
  flex-direction: column;
}

.chat-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 12px 16px;
  border-bottom: 1px solid #eee;
}

.chat-actions {
  display: flex;
  gap: 8px;
}

.messages-container {
  flex: 1;
  padding: 16px;
  overflow: hidden;
}

.message-item {
  margin-bottom: 16px;
  display: flex;
  flex-direction: column;
}

.message-item.is-user {
  align-items: flex-start;
}

.message-item.is-service {
  align-items: flex-end;
}

.message-item.is-service .message-content {
  background-color: #e6f7ff;
  border: 1px solid #91d5ff;
}

.message-header {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 6px;
}

.message-avatar {
  flex-shrink: 0;
}

.message-sender {
  font-size: 13px;
  font-weight: 500;
  color: #303133;
}

.message-item.is-service .message-sender {
  color: #1890ff;
}

.message-time {
  font-size: 12px;
  color: #909399;
  margin-left: auto;
}

.message-status {
  font-size: 11px;
  margin-left: 8px;
}

.message-status.sent {
  color: #909399;
}

.message-status.delivered {
  color: #67c23a;
}

.message-status.read {
  color: #1890ff;
}

.message-image {
  margin-top: 4px;
}

.message-image .chat-image-img {
  max-width: 200px;
  max-height: 180px;
  border-radius: 8px;
  cursor: pointer;
  object-fit: cover;
  border: 1px solid #eee;
}

.message-image .chat-image-img:hover {
  opacity: 0.9;
}

.preview-image-container {
  display: flex;
  justify-content: center;
  align-items: center;
  min-height: 400px;
}

.preview-image {
  max-width: 100%;
  max-height: 70vh;
  object-fit: contain;
}

.message-file {
  margin-top: 4px;
}

.message-content {
  padding: 8px 12px;
  background-color: #f5f5f5;
  border-radius: 4px;
  display: inline-block;
  max-width: 80%;
  word-break: break-word;
}

.message-time {
  font-size: 11px;
  color: #999;
  margin-top: 4px;
}

.message-input {
  display: flex;
  gap: 12px;
  padding: 12px 16px;
  border-top: 1px solid #eee;
}

.message-input .el-input {
  flex: 1;
}

.no-session {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #999;
}

.empty-tip {
  padding: 20px;
  text-align: center;
  color: #999;
}

.quick-reply-list {
  max-height: 300px;
  overflow-y: auto;
}

.quick-reply-item {
  padding: 8px 12px;
  cursor: pointer;
  border-bottom: 1px solid #f0f0f0;
}

.quick-reply-item:hover {
  background-color: #f5f7fa;
}

.quick-reply-item:last-child {
  border-bottom: none;
}

.quick-reply-title {
  font-weight: 500;
  color: #333;
  margin-bottom: 4px;
}

.quick-reply-content {
  font-size: 12px;
  color: #666;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.note-panel {
  width: 280px;
  border-left: 1px solid #eee;
  background: #fafafa;
  padding: 16px;
  display: flex;
  flex-direction: column;
}

.note-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
  font-weight: 500;
}

.note-content {
  flex: 1;
}

.tags-label {
  font-size: 12px;
  color: #666;
  margin-bottom: 8px;
}

.mt-2 {
  margin-top: 8px;
}

.mt-4 {
  margin-top: 16px;
}
</style>