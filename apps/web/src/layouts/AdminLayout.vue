<template>
  <el-container class="shell">
    <el-aside width="220px" class="aside">
      <div class="brand">灯鉴 · 管理端</div>
      <el-menu :default-active="route.path" router>
        <el-menu-item v-if="auth.has('admin:user:view')" index="/admin/users">用户角色</el-menu-item>
        <el-menu-item v-if="auth.has('admin:llm:view')" index="/admin/llm">模型配置</el-menu-item>
        <el-menu-item v-if="auth.has('admin:job:view')" index="/admin/jobs">导入任务</el-menu-item>
        <el-menu-item v-if="auth.has('admin:dict:view')" index="/admin/dicts">方面词典</el-menu-item>
        <el-menu-item v-if="auth.has('admin:audit:view')" index="/admin/audit">操作日志</el-menu-item>
      </el-menu>
    </el-aside>
    <el-container>
      <el-header class="header">
        <span class="muted">权限码与后端注解同名</span>
        <div>
          <el-button text @click="router.push('/app/projects')">返回工作台</el-button>
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
.aside { background: #1b2430; color: #f4efe6; }
.brand { padding: 20px 16px; font-weight: 700; }
.aside :deep(.el-menu) { background: transparent; border: none; }
.aside :deep(.el-menu-item) { color: #dbe4ee; }
.aside :deep(.el-menu-item.is-active) { background: #2a5a8c; color: #fff; }
.header { display: flex; align-items: center; justify-content: space-between; background: #fff; border-bottom: 1px solid #eadfce; }
.user { margin-right: 8px; }
</style>
