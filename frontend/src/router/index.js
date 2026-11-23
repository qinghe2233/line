import { createRouter, createWebHistory } from 'vue-router'

const routes = [
  { path: '/', redirect: '/h5' },
  { path: '/login', component: () => import('../views/Login.vue') },
  {
    path: '/admin',
    component: () => import('../layout/AdminLayout.vue'),
    meta: { requiresAuth: true, requiresStaff: true },
    children: [
      { path: '', redirect: '/admin/appointments' },
      { path: 'appointments', component: () => import('../views/admin/AppointmentList.vue'), meta: { title: '预约列表' } },
      { path: 'schedule', component: () => import('../views/admin/ScheduleManage.vue'), meta: { title: '排班管理' } },
      { path: 'services', component: () => import('../views/admin/ServiceManage.vue'), meta: { title: '服务项管理' } },
      { path: 'queue', component: () => import('../views/admin/QueuePanel.vue'), meta: { title: '叫号面板' } }
    ]
  },
  {
    path: '/h5',
    component: () => import('../layout/H5Layout.vue'),
    meta: { requiresAuth: true },
    children: [
      { path: '', component: () => import('../views/h5/Booking.vue') },
      { path: 'pay', component: () => import('../views/h5/Pay.vue') },
      { path: 'my', component: () => import('../views/h5/MyAppointments.vue') }
    ]
  },
  { path: '/:pathMatch(.*)*', redirect: '/h5' }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

function currentUser() {
  try {
    return JSON.parse(localStorage.getItem('user') || 'null')
  } catch {
    return null
  }
}

/**
 * 路由守卫：
 * - 管理端：必须登录且角色为店员/店长；
 * - H5：必须登录（下单、支付、我的预约都需要用户身份）；
 * - 登录页已登录时按角色送回各自首页。
 */
router.beforeEach((to) => {
  const token = localStorage.getItem('token')
  const user = currentUser()

  // 旧版本残留的假 token 或 user 缺失，直接当作未登录清掉
  if (token && !user) {
    localStorage.removeItem('token')
  }

  if (to.meta.requiresAuth && !token) {
    return { path: '/login', query: { redirect: to.fullPath } }
  }
  if (to.meta.requiresStaff && (!user || user.role < 1)) {
    return { path: '/login', query: { redirect: to.fullPath } }
  }
  if (to.path === '/login' && token && user) {
    return user.role >= 1 ? '/admin/appointments' : '/h5'
  }
  return true
})

export default router
