import axios from 'axios'
import { ElMessage } from 'element-plus'
import router from '../router'

/**
 * 统一 axios 实例。
 *
 * 后端约定：业务成败看响应体 code，HTTP 状态码只表达请求是否被正确处理。
 * 所以这里把 code !== 0 也当成失败，统一弹错误提示，页面里只写正常逻辑。
 */
const request = axios.create({
  baseURL: '/api',
  timeout: 15000
})

request.interceptors.request.use((config) => {
  const token = localStorage.getItem('token')
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  // 用户身份只由后端从 JWT 解析，前端不再传任何 user id
  return config
})

function toLogin() {
  localStorage.removeItem('token')
  localStorage.removeItem('user')
  const current = router.currentRoute.value
  if (current.path !== '/login') {
    router.replace({ path: '/login', query: { redirect: current.fullPath } })
  }
}

request.interceptors.response.use(
  (response) => {
    const body = response.data
    if (body && body.code === 0) {
      return body.data
    }
    const message = body?.message || '请求失败'
    ElMessage.error(message)
    // 10002 = 未登录/令牌失效，回登录页；10003 = 已登录但无权限，只提示不踢人
    if (body && body.code === 10002) {
      toLogin()
    }
    return Promise.reject(new Error(message))
  },
  (error) => {
    const status = error.response?.status
    const message = error.response?.data?.message || error.message || '网络异常'
    ElMessage.error(message)
    if (status === 401) {
      toLogin()
    }
    return Promise.reject(error)
  }
)

export default request
