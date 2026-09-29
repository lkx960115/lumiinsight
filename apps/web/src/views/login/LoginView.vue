<template>
  <div class="login-page">
    <div class="login-stage">
      <p class="login-mark">灯鉴</p>
      <h1>登录工作台</h1>
      <p class="muted">用首次启动日志里的管理员账号进入。密码只打印一次。</p>
      <el-form class="login-form" label-position="top" @submit.prevent="onSubmit">
        <el-form-item label="用户名">
          <el-input v-model="username" size="large" autocomplete="username" />
        </el-form-item>
        <el-form-item label="密码">
          <el-input v-model="password" type="password" size="large" show-password autocomplete="current-password" />
        </el-form-item>
        <el-button class="login-submit" type="primary" size="large" :loading="loading" native-type="submit">
          进入灯鉴
        </el-button>
      </el-form>
    </div>
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
