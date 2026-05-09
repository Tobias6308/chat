import request from './request'

export const adminApi = {
  // Login
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
    request.put('/admin/password', data)
}