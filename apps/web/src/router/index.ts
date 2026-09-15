import { createRouter, createWebHistory } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/login', component: () => import('@/views/login/LoginView.vue') },
    {
      path: '/app',
      component: () => import('@/layouts/AppLayout.vue'),
      children: [
        { path: '', redirect: '/app/projects' },
        { path: 'projects', component: () => import('@/views/app/ProjectListView.vue') },
        { path: 'projects/:id', component: () => import('@/views/app/ProjectDetailView.vue') },
      ],
    },
    {
      path: '/admin',
      component: () => import('@/layouts/AdminLayout.vue'),
      children: [
        { path: '', redirect: '/admin/users' },
        { path: 'users', component: () => import('@/views/admin/UsersView.vue') },
        { path: 'llm', component: () => import('@/views/admin/LlmView.vue') },
        { path: 'jobs', component: () => import('@/views/admin/JobsView.vue') },
        { path: 'dicts', component: () => import('@/views/admin/DictsView.vue') },
        { path: 'audit', component: () => import('@/views/admin/AuditView.vue') },
      ],
    },
    { path: '/', redirect: '/app/projects' },
  ],
})

router.beforeEach(async (to) => {
  const auth = useAuthStore()
  if (to.path === '/login') {
    return true
  }
  if (!auth.loggedIn) {
    return { path: '/login', query: { redirect: to.fullPath } }
  }
  if (!auth.user) {
    try {
      await auth.fetchMe()
    } catch {
      auth.logout()
      return { path: '/login' }
    }
  }
  if (to.path.startsWith('/admin') && !auth.isAdmin) {
    return '/app/projects'
  }
  return true
})

export default router
