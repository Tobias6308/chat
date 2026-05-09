import { createRouter, createWebHistory } from 'vue-router'
import Login from '@/views/Login.vue'
import Dashboard from '@/views/Dashboard.vue'
import Admins from '@/views/Admins.vue'
import Users from '@/views/Users.vue'
import Groups from '@/views/Groups.vue'
import Conversations from '@/views/Conversations.vue'
import Friends from '@/views/Friends.vue'
import Messages from '@/views/Messages.vue'
import Profile from '@/views/Profile.vue'

const routes = [
  { path: '/login', name: 'Login', component: Login },
  { path: '/', name: 'Dashboard', component: Dashboard },
  { path: '/admins', name: 'Admins', component: Admins },
  { path: '/users', name: 'Users', component: Users },
  { path: '/groups', name: 'Groups', component: Groups },
  { path: '/conversations', name: 'Conversations', component: Conversations },
  { path: '/friends', name: 'Friends', component: Friends },
  { path: '/messages', name: 'Messages', component: Messages },
  { path: '/profile', name: 'Profile', component: Profile, meta: { title: '账号设置' } }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

router.beforeEach((to, _from, next) => {
  if (to.path !== '/login' && !sessionStorage.getItem('admin_token')) {
    next('/login')
  } else {
    next()
  }
})

export default router