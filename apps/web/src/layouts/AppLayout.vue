<template>
  <el-container class="shell">
    <el-aside width="220px" class="aside">
      <div class="brand">灯鉴 · 工作台</div>
      <el-menu :default-active="route.path" router>
        <el-menu-item index="/app/projects">项目与导入</el-menu-item>
      </el-menu>
    </el-aside>
    <el-container>
      <el-header class="header">
        <span class="muted">同一登录态 · 菜单按角色</span>
        <div>
          <el-button v-if="auth.isAdmin" text @click="router.push('/admin/users')">进入管理端</el-button>
          <span class="user">{{ auth.user?.displayName }}</span>
          <el-button text @click="onLogout">退出</el-button>
        </div>
      </el-header>
      <el-main>
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

<style scoped>
.shell { height: 100%; }
.aside { background: #16302b; color: #f4efe6; }
.brand { padding: 20px 16px; font-weight: 700; letter-spacing: 0.04em; }
.aside :deep(.el-menu) { background: transparent; border: none; }
.aside :deep(.el-menu-item) { color: #e7efe9; }
.aside :deep(.el-menu-item.is-active) { background: #1f6f5b; color: #fff; }
.header { display: flex; align-items: center; justify-content: space-between; background: #fff; border-bottom: 1px solid #eadfce; }
.user { margin-right: 8px; }
</style>
