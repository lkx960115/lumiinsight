<template>
  <el-container class="app-shell">
    <el-aside width="220px" class="app-aside">
      <div class="app-brand">
        <strong>灯鉴</strong>
        <span>工作台</span>
      </div>
      <el-menu :default-active="route.path" router>
        <el-menu-item index="/app/projects">项目与导入</el-menu-item>
      </el-menu>
    </el-aside>
    <el-container>
      <el-header class="app-header">
        <span class="muted">评论导入与洞察</span>
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
