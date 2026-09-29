<template>
  <el-container class="app-shell">
    <el-aside width="220px" class="app-aside">
      <div class="app-brand">
        <strong>灯鉴</strong>
        <span>工作台</span>
      </div>
      <el-menu :key="activeMenu" :default-active="activeMenu" router>
        <el-menu-item index="/app/projects">项目</el-menu-item>
        <el-menu-item v-if="auth.has('report:view')" index="/app/reports">报告</el-menu-item>
      </el-menu>
    </el-aside>
    <el-container>
      <el-header class="app-header">
        <span class="muted">工作台</span>
        <div>
          <el-button v-if="auth.isAdmin" text @click="router.push('/admin/users')">系统管理</el-button>
          <span class="user">{{ auth.user?.displayName }}</span>
          <el-button text @click="onLogout">退出</el-button>
        </div>
      </el-header>
      <el-main class="app-main">
        <router-view />
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const activeMenu = computed(() => {
  if (route.path.startsWith('/app/reports')) return '/app/reports'
  if (route.path.startsWith('/app/projects')) return '/app/projects'
  return route.path
})

function onLogout() {
  auth.logout()
  router.push('/login')
}
</script>
