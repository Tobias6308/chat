import { encryptMD5 as md5 } from './md5';

const API_BASE = import.meta.env.VITE_API_BASE || 'http://localhost:8082/api';

function getToken(): string {
  return sessionStorage.getItem('chat_token') || '';
}

function encryptPassword(password: string): string {
  return md5(password);
}

async function request<T>(
  endpoint: string,
  options: RequestInit = {}
): Promise<T> {
  const url = `${API_BASE}${endpoint}`;
  
  const response = await fetch(url, {
    ...options,
    headers: {
      'Content-Type': 'application/json',
      'Authorization': `Bearer ${getToken()}`,
      ...options.headers,
    },
  });
  
  if (!response.ok) {
    const error = await response.json().catch(() => ({ message: '请求失败' }));
    // 401 Unauthorized - 跳转到登录页面
    if (response.status === 401) {
      sessionStorage.removeItem('chat_token');
      window.location.href = '/login';
      throw new Error('Unauthorized');
    }
    throw new Error(error.message || `HTTP ${response.status}`);
  }
  
  return response.json();
}

export const authApi = {
  register: (data: { username: string; password: string; nickname?: string }) =>
    request<{ token: string; userId: string; nickname: string }>('/auth/register', {
      method: 'POST',
      body: JSON.stringify({
        ...data,
        password: encryptPassword(data.password),
      }),
    }),
  
  login: (data: { username: string; password: string }) =>
    request<{ token: string; userId: string; nickname: string; avatar?: string }>('/auth/login', {
      method: 'POST',
      body: JSON.stringify({
        ...data,
        password: encryptPassword(data.password),
      }),
    }),
};

export const chatApi = {
  getConversations: () =>
    request<any[]>('/chat/conversations'),
  
  getConversation: (id: string) =>
    request<any>(`/chat/conversation/${id}`),
  
  getMessages: (conversationId: string, cursor?: number, limit = 50) =>
    request<any>(`/chat/conversation/${conversationId}/messages?limit=${limit}${cursor ? `&cursor=${cursor}` : ''}`),
  
  createConversation: (data: { type: string; participantIds: string[]; name?: string }) =>
    request<any>('/chat/conversation', {
      method: 'POST',
      body: JSON.stringify(data),
    }),
  
  getOrCreatePrivate: (friendId: string) =>
    request<any>('/chat/private', {
      method: 'POST',
      body: JSON.stringify({ friendId }),
    }),
  
  markAsRead: (conversationId: string) =>
    request<any>(`/chat/conversation/${conversationId}/read`, {
      method: 'POST',
    }),
  
  togglePin: (conversationId: string) =>
    request<{ success: boolean; pinned: boolean }>(`/chat/conversation/${conversationId}/pin`, {
      method: 'POST',
    }),
  
  toggleMute: (conversationId: string) =>
    request<{ success: boolean; muted: boolean }>(`/chat/conversation/${conversationId}/mute`, {
      method: 'POST',
    }),
};

export const groupApi = {
  create: (data: { name: string; description?: string; memberIds: string[] }) =>
    request<any>('/group', {
      method: 'POST',
      body: JSON.stringify(data),
    }),
  
  getList: () =>
    request<any[]>('/group/list'),
  
  getMembers: (groupId: string) =>
    request<any[]>(`/group/${groupId}/members`),
  
  addMembers: (groupId: string, memberIds: string[]) =>
    request<any>(`/group/${groupId}/members`, {
      method: 'POST',
      body: JSON.stringify({ memberIds }),
    }),
  
removeMember: (groupId: string, memberId: string) =>
    request<any>(`/group/${groupId}/members/${memberId}`, {
      method: 'DELETE',
    }),
  
  setMemberRole: (groupId: string, memberId: string, role: string) =>
    request<any>(`/group/${groupId}/members/${memberId}/role`, {
      method: 'PUT',
      body: JSON.stringify({ role }),
    }),
  
  setMemberMute: (groupId: string, memberId: string, muted: boolean) =>
    request<any>(`/group/${groupId}/members/${memberId}/mute`, {
      method: 'PUT',
      body: JSON.stringify({ muted }),
    }),
  
  delete: (groupId: string) =>
    request<any>(`/group/${groupId}`, {
      method: 'DELETE',
    }),
  
  update: (groupId: string, data: { name?: string; description?: string; avatar?: string }) =>
    request<any>(`/group/${groupId}`, {
      method: 'PUT',
      body: JSON.stringify(data),
    }),
  
  togglePin: (groupId: string) =>
    request<{ success: boolean; pinned: boolean }>(`/group/${groupId}/pin`, {
      method: 'POST',
    }),
  
  toggleMute: (groupId: string) =>
    request<{ success: boolean; muted: boolean }>(`/group/${groupId}/mute`, {
      method: 'POST',
    }),
};

export const friendApi = {
  search: (keyword: string) =>
    request<{ users: any[] }>(`/friend/search?keyword=${encodeURIComponent(keyword)}`),
  
  sendRequest: (userId: string) =>
    request<any>('/friend/request', {
      method: 'POST',
      body: JSON.stringify({ userId }),
    }),
  
  acceptRequest: (requestId: string) =>
    request<any>(`/friend/request/${requestId}/accept`, {
      method: 'POST',
    }),
  
  rejectRequest: (requestId: string) =>
    request<any>(`/friend/request/${requestId}/reject`, {
      method: 'POST',
    }),
  
  removeFriend: (userId: string) =>
    request<any>(`/friend/${userId}`, {
      method: 'DELETE',
    }),
  
  getList: () =>
    request<{ friends: any[] }>('/friend/list'),
  
  getRequests: () =>
    request<any>('/friend/requests'),
};

export const userApi = {
  getInfo: () =>
    request<{
      userId: string;
      username: string;
      nickname: string;
      avatar: string;
      createdAt: number;
      lastLoginAt?: number;
      online: boolean;
      extra?: any;
    }>('/user/info'),
  
  getById: (userId: string) =>
    request<{
      userId: string;
      username: string;
      nickname: string;
      avatar: string;
      online: boolean;
    }>(`/user/${userId}`),
  
  updateProfile: (data: { nickname?: string; avatar?: string }) =>
    request<{ userId: string; nickname: string; avatar: string }>('/user/profile', {
      method: 'PUT',
      body: JSON.stringify(data),
    }),
  
  updatePassword: (data: { oldPassword: string; newPassword: string }) =>
    request<{ success: boolean }>('/user/password', {
      method: 'PUT',
      body: JSON.stringify({
        oldPassword: encryptPassword(data.oldPassword),
        newPassword: encryptPassword(data.newPassword),
      }),
    }),

  getBatch: (userIds: string[]) =>
    request<{ users: Record<string, { userId: string; username: string; nickname: string; avatar?: string }> }>(
      `/user/batch?userIds=${userIds.join(',')}`
    ),
};

export const adminApi = {
  login: (username: string, password: string) =>
    request<{ token: string; userId: string; nickname: string }>('/admin/login', {
      method: 'POST',
      body: JSON.stringify({ username, password }),
    }),
  
  getStats: () =>
    request<any>('/admin/stats'),
};

export const configApi = {
  getConfig: () =>
    request<{ success: boolean; data: { fileServerUrl: string } }>('/chat/config'),
};

// 文件服务器地址缓存
let fileServerUrl = '';

export function getFileServerUrl(): string {
  return fileServerUrl;
}

export function setFileServerUrl(url: string): void {
  fileServerUrl = url;
}

// 拼接完整的文件URL
export function getFullFileUrl(relativePath: string): string {
  if (!relativePath) return '';
  if (relativePath.startsWith('http')) return relativePath;
  return fileServerUrl + relativePath;
}

export const uploadApi = {
  upload: async (file: File): Promise<{
    success: boolean;
    url: string;
    filename: string;
    originalName: string;
    extension: string;
    size: number;
    type: string;
  }> => {
    const formData = new FormData();
    formData.append('file', file);
    
    const url = `${API_BASE}/chat/upload`;
    const response = await fetch(url, {
      method: 'POST',
      headers: {
        'Authorization': `Bearer ${getToken()}`,
      },
      body: formData,
    });
    
    if (!response.ok) {
      const error = await response.json().catch(() => ({ message: '上传失败' }));
      throw new Error(error.message || `HTTP ${response.status}`);
    }
    
    return response.json();
  },
  
  uploadImage: (file: File) => uploadApi.upload(file),
  uploadAudio: (file: File) => uploadApi.upload(file),
  uploadVideo: (file: File) => uploadApi.upload(file),
  uploadFile: (file: File) => uploadApi.upload(file),
};