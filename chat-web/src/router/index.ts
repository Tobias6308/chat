import { createRouter, createWebHistory } from 'vue-router';
import type { RouteRecordRaw } from 'vue-router';

const routes: RouteRecordRaw[] = [
  {
    path: '/',
    name: 'ChatRoom',
    component: () => import('@/views/ChatRoom.vue'),
    meta: {
      title: '聊天'
    }
  },
  {
    path: '/login',
    name: 'Login',
    component: () => import('@/views/Login.vue'),
    meta: {
      title: '登录'
    }
  },
  {
    path: '/profile',
    name: 'Profile',
    component: () => import('@/views/Profile.vue'),
    meta: {
      title: '个人设置'
    }
  },
  {
    path: '/friends',
    name: 'Friends',
    component: () => import('@/views/Friends.vue'),
    meta: {
      title: '好友'
    }
  },
  {
    path: '/friend-requests',
    name: 'FriendRequests',
    component: () => import('@/views/FriendRequests.vue'),
    meta: {
      title: '好友请求'
    }
  },
  {
    path: '/groups',
    name: 'Groups',
    component: () => import('@/views/Groups.vue'),
    meta: {
      title: '群聊'
    }
  },
  {
    path: '/group-manage/:id',
    name: 'GroupManage',
    component: () => import('@/views/GroupManage.vue'),
    meta: {
      title: '群管理'
    }
  },
  {
    path: '/service',
    name: 'Service',
    component: () => import('@/views/Service.vue'),
    meta: {
      title: '联系客服',
      requiresAuth: true
    }
  },
  {
    path: '/:pathMatch(.*)*',
    redirect: '/'
  }
];

const router = createRouter({
  history: createWebHistory(),
  routes
});

router.beforeEach((to, _from, next) => {
  const token = sessionStorage.getItem('chat_token');

  if (to.meta.requiresAuth && !token) {
    next({ name: 'Login' });
  } else if (to.name === 'Login' && token) {
    next({ name: 'ChatRoom' });
  } else {
    next();
  }
});

export default router;