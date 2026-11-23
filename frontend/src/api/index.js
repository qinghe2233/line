import request from './request'

/* ---------------- 认证 ---------------- */

export const login = (data) => request.post('/auth/login', data)

export const me = () => request.get('/auth/me')

/* ---------------- 顾客端 ---------------- */

export const listStores = () => request.get('/stores')

export const listStoreServices = (storeId) => request.get(`/stores/${storeId}/services`)

export const listSlots = (storeId, serviceId, date) =>
  request.get(`/stores/${storeId}/slots`, { params: { serviceId, date } })

// 幂等键由页面生成，同一次下单重试必须复用同一个 key
export const createAppointment = (data, idempotencyKey) =>
  request.post('/appointments', data, { headers: { 'Idempotency-Key': idempotencyKey } })

export const myAppointments = (params) => request.get('/appointments/mine', { params })

// 仅待支付订单可取消，后端会校验订单归属并回滚库存
export const cancelAppointment = (id) => request.post(`/appointments/${id}/cancel`)

export const mockPay = (orderNo) => request.post('/pay/mock', { orderNo })

export const takeTicket = (data) => request.post('/queue/tickets', data)

export const ticketProgress = (id) => request.get(`/queue/tickets/${id}`)

/* ---------------- 管理端 ---------------- */

export const adminAppointments = (params) => request.get('/admin/appointments', { params })

export const verifyAppointment = (id) => request.post(`/admin/appointments/${id}/verify`)

export const noShowAppointment = (id) => request.post(`/admin/appointments/${id}/no-show`)

export const adminTickets = (params) => request.get('/admin/queue/tickets', { params })

export const callTicket = (id) => request.post(`/admin/queue/tickets/${id}/call`)

export const finishTicket = (id) => request.post(`/admin/queue/tickets/${id}/finish`)

export const todayOverview = (storeId) =>
  request.get('/admin/dashboard/today', { params: { storeId } })

export const adminServices = (storeId) => request.get('/admin/services', { params: { storeId } })

export const createService = (data) => request.post('/admin/services', data)

export const updateService = (id, data) => request.put(`/admin/services/${id}`, data)

export const adminResources = (storeId) => request.get('/admin/resources', { params: { storeId } })

export const adminSlots = (params) => request.get('/admin/slots', { params })

export const createSlot = (data) => request.post('/admin/slots', data)

export const updateSlot = (id, data) => request.put(`/admin/slots/${id}`, data)
