<template>
  <el-container class="app-shell">
    <el-aside width="220px" class="app-aside">
      <div class="app-brand">
        <strong>灯鉴</strong>
        <span>系统管理</span>
      </div>
      <el-menu :default-active="route.path" router>
        <el-menu-item v-if="auth.has('admin:user:view')" index="/admin/users">用户角色</el-menu-item>
        <el-menu-item v-if="auth.has('admin:llm:view')" index="/admin/llm">模型配置</el-menu-item>
        <el-menu-item v-if="auth.has('admin:llm:view')" index="/admin/usage">用量</el-menu-item>
        <el-menu-item v-if="auth.has('admin:job:view')" index="/admin/jobs">任务日志</el-menu-item>
        <el-menu-item v-if="auth.has('admin:dict:view')" index="/admin/dicts">方面词典</el-menu-item>
        <el-menu-item v-if="auth.has('admin:audit:view')" index="/admin/audit">操作日志</el-menu-item>
      </el-menu>
    </el-aside>
    <el-container>
      <el-header class="app-header">
        <span class="muted">系统设置</span>
        <div>
          <el-button text @click="router.push('/app/projects')">返回工作台</el-button>
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
import { onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import http from '@/api/http'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const healthHint = ref('')

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

function onLogout() {
  auth.logout()
  router.push('/login')
}

onMounted(checkHealth)
</script>
