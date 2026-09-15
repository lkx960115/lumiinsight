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
        <el-menu-item v-if="auth.has('admin:job:view')" index="/admin/jobs">任务日志</el-menu-item>
        <el-menu-item v-if="auth.has('admin:dict:view')" index="/admin/dicts">方面词典</el-menu-item>
        <el-menu-item v-if="auth.has('admin:audit:view')" index="/admin/audit">操作日志</el-menu-item>
      </el-menu>
    </el-aside>
    <el-container>
      <el-header class="app-header">
        <span class="muted">账号、模型与任务日志</span>
        <div>
          <el-button text @click="router.push('/app/projects')">返回工作台</el-button>
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
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()

function onLogout() {
  auth.logout()
  router.push('/login')
}
</script>
