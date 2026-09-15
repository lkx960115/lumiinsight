import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import http from '@/api/http'

export interface MenuItem {
  code: string
  name: string
  path: string
}

export interface UserProfile {
  id: number
  username: string
  displayName: string
  roles: string[]
  permissions: string[]
  menus: MenuItem[]
  mustChangePwd: boolean
}

export const useAuthStore = defineStore('auth', () => {
  const token = ref(localStorage.getItem('lumi_token') || '')
  const user = ref<UserProfile | null>(null)

  const isAdmin = computed(() => user.value?.roles.includes('admin') ?? false)
  const loggedIn = computed(() => Boolean(token.value))

  function has(code: string) {
    return user.value?.permissions.includes(code) ?? false
  }

  async function login(username: string, password: string) {
    const { data } = await http.post('/auth/login', { username, password })
    token.value = data.data.token
    user.value = data.data.user
    localStorage.setItem('lumi_token', token.value)
  }

  async function fetchMe() {
    if (!token.value) return
    const { data } = await http.get('/auth/me')
    token.value = data.data.token
    user.value = data.data.user
    localStorage.setItem('lumi_token', token.value)
  }

  function logout() {
    token.value = ''
    user.value = null
    localStorage.removeItem('lumi_token')
  }

  return { token, user, isAdmin, loggedIn, has, login, fetchMe, logout }
})
