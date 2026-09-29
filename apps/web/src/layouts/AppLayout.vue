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
        <div v-if="healthHint" class="run-banner is-failed">
          <div>
            <strong>服务异常</strong>
            <p>{{ healthHint }}</p>
          </div>
        </div>
        <router-view />
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import http from '@/api/http'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const healthHint = ref('')
const activeMenu = computed(() => {
  if (route.path.startsWith('/app/reports')) return '/app/reports'
  if (route.path.startsWith('/app/projects')) return '/app/projects'
  return route.path
})

function onLogout() {
  auth.logout()
  router.push('/login')
}

async function checkHealth() {
  try {
    const { data } = await http.get('/health', { silent: true } as any)
    const h = data.data || {}
    if (h.mysql === 'down') healthHint.value = '数据服务暂时连不上，请稍后重试。'
    else if (h.worker === 'down') healthHint.value = '分析服务暂时连不上。请先启动分析进程，否则清洗并分析会失败。'
    else healthHint.value = ''
  } catch {
    healthHint.value = '后台暂时连不上，请确认服务已启动后再试。'
  }
}

onMounted(checkHealth)
</script>
