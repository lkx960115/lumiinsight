<template>
  <div class="login">
    <el-card class="card">
      <h1>灯鉴 LumiInsight</h1>
      <p class="muted">本地账号登录。初始管理员密码只在后端首次启动日志中打印。</p>
      <el-form @submit.prevent="onSubmit">
        <el-form-item>
          <el-input v-model="username" placeholder="用户名" />
        </el-form-item>
        <el-form-item>
          <el-input v-model="password" type="password" placeholder="密码" show-password />
        </el-form-item>
        <el-button type="primary" style="width: 100%" :loading="loading" native-type="submit">登录</el-button>
      </el-form>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

const username = ref('admin')
const password = ref('')
const loading = ref(false)
const auth = useAuthStore()
const router = useRouter()
const route = useRoute()

async function onSubmit() {
  loading.value = true
  try {
    await auth.login(username.value, password.value)
    const redirect = (route.query.redirect as string) || (auth.isAdmin ? '/admin/users' : '/app/projects')
    await router.push(redirect)
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.login {
  min-height: 100%;
  display: grid;
  place-items: center;
  background: linear-gradient(160deg, #16302b, #1f6f5b 55%, #f4efe6);
}
.card { width: 380px; }
h1 { margin: 0 0 8px; font-size: 22px; }
</style>
