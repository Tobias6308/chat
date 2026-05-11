import request from './request'
import { encryptMD5 } from './md5'

// 配置文件服务器地址
let fileServerUrl = '';

export function setFileServerUrl(url: string): void {
  fileServerUrl = url;
}

export function getFileServerUrl(): string {
  return fileServerUrl;
}

export function getFullFileUrl(relativePath: string): string {
  if (!relativePath) return '';
  if (relativePath.startsWith('http')) return relativePath;
  return fileServerUrl + relativePath;
}

export const configApi = {
  getConfig: () =>
    request<{ success: boolean; data: { fileServerUrl: string } }>('/chat/config'),
};

export const adminApi = {
  // Login (Login.vue already hashes the password)
  login: (username: string, password: string) =>
    request.post('/admin/login', { username, password }),

  // Stats
  getStats: () =>
    request.get('/admin/stats'),

  // Admins
  getAdmins: () =>
    request.get('/admin/admins'),
  createAdmin: (data: { username: string; password: string; nickname?: string }) =>
    request.post('/admin/admin', data),
  deleteAdmin: (id: string) =>
    request.delete(`/admin/admin/${id}`),

  // Users
  getUsers: (params?: { limit?: number; skip?: number }) =>
    request.get('/admin/users', { params }),
  updateUserEnable: (id: string, enable: boolean) =>
    request.put(`/admin/user/${id}/enable`, { enabled: enable }),

  // Groups
  getGroups: (params?: { limit?: number; skip?: number }) =>
    request.get('/admin/groups', { params }),
  deleteGroup: (id: string) =>
    request.delete(`/admin/group/${id}`),

  // Conversations
  getConversations: (params?: { type?: string; limit?: number; skip?: number }) =>
    request.get('/admin/conversations', { params }),
  deleteConversation: (id: string) =>
    request.delete(`/admin/conversation/${id}`),

  // Friends
  getFriends: (params?: { userId?: string; limit?: number; skip?: number }) =>
    request.get('/admin/friends', { params }),
  deleteFriend: (id: string) =>
    request.delete(`/admin/friend/${id}`),

  // Messages
  getMessages: (params?: { conversationId?: string; senderId?: string; limit?: number; skip?: number }) =>
    request.get('/admin/messages', { params }),
  deleteMessage: (id: string) =>
    request.delete(`/admin/message/${id}`),

  // Profile
  getProfile: () =>
    request.get('/admin/profile'),
  updateProfile: (data: { nickname: string }) =>
    request.put('/admin/profile', data),
  updatePassword: (data: { oldPassword: string; newPassword: string }) =>
    request.put('/admin/password', data),

  // Service (Customer Service Management)
  getServiceList: () =>
    request.get('/admin/service/list'),
  createService: (data: { username: string; password: string; nickname?: string }) =>
    request.post('/admin/service/create', { 
      ...data, 
      password: encryptMD5(data.password) 
    }),
  updateServiceStatus: (data: { status: string }) =>
    request.put('/admin/service/status', data),
  getNextUser: () =>
    request.post('/admin/service/next'),
  getServiceSessions: (params?: { page?: number; limit?: number; status?: string; serviceId?: string }) =>
    request.get('/admin/service/sessions', { params }),
  getQueueList: () =>
    request.get('/admin/service/queue'),
  getSessionMessages: (sessionId: string, params?: { limit?: number; skip?: number }) =>
    request.get(`/admin/service/session/${sessionId}/messages`, { params }),
  sendServiceMessage: (data: { sessionId: string; content: string; contentType?: string }) =>
    request.post('/admin/service/message', data),
  sendTyping: () =>
    request.post('/admin/service/typing'),
  endServiceSession: (sessionId: string) =>
    request.post('/admin/service/end', { sessionId }),
  getQuickReplies: () =>
    request.get('/admin/service/quick-replies'),
  addQuickReply: (data: { title: string; content: string }) =>
    request.post('/admin/service/quick-replies', data),
  deleteQuickReply: (replyId: string) =>
    request.delete(`/admin/service/quick-replies/${replyId}`),
  transferSession: (data: { sessionId: string; toServiceId: string }) =>
    request.post('/admin/service/transfer', data),
  getServiceStats: () =>
    request.get('/admin/service/stats'),
  getServicePerformance: () =>
    request.get('/admin/service/performance'),
  updateSessionNote: (sessionId: string, note: string) =>
    request.put(`/admin/service/session/${sessionId}/note`, { note }),
  updateSessionTags: (sessionId: string, tags: string[]) =>
    request.put(`/admin/service/session/${sessionId}/tags`, { tags }),
  resetServicePassword: (serviceId: string) =>
    request.post('/admin/service/reset-password', { serviceId })
}