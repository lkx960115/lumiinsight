import axios from 'axios'
import { ElMessage } from 'element-plus'
import router from '@/router'

export interface ApiResult<T> {
  code: string
  message: string
  data: T
}

const http = axios.create({
  baseURL: '/api/v1',
  timeout: 60000,
})

http.interceptors.request.use((config) => {
  const token = localStorage.getItem('lumi_token')
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

http.interceptors.response.use(
  (resp) => {
    if (resp.config.responseType === 'blob') {
      return resp
    }
    const payload = resp.data as ApiResult<unknown>
    if (payload && typeof payload === 'object' && 'code' in payload && payload.code !== '0') {
      ElMessage.error(payload.message || '请求失败')
      return Promise.reject(payload)
    }
    return resp
  },
  (err) => {
    const status = err.response?.status
    const payload = err.response?.data as ApiResult<unknown> | undefined
    if (status === 401) {
      localStorage.removeItem('lumi_token')
      if (router.currentRoute.value.path !== '/login') {
        router.push({ path: '/login', query: { redirect: router.currentRoute.value.fullPath } })
      }
    }
    ElMessage.error(payload?.message || err.message || '网络异常')
    return Promise.reject(err)
  },
)

export default http
