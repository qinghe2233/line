/** 订单状态 -> 标签颜色；文案统一用后端返回的 statusDesc，避免两边各维护一份 */
export const ORDER_STATUS_TYPE = {
  0: 'warning',
  10: 'success',
  20: 'primary',
  30: 'success',
  35: 'danger',
  40: 'info',
  50: 'info',
  60: 'warning',
  70: 'info'
}

export const TICKET_STATUS_TYPE = {
  0: 'warning',
  1: 'primary',
  3: 'success',
  4: 'danger'
}

export const ORDER_STATUS_OPTIONS = [
  { value: 0, label: '待支付' },
  { value: 10, label: '已支付' },
  { value: 20, label: '已核销' },
  { value: 30, label: '已完成' },
  { value: 35, label: '爽约' },
  { value: 40, label: '用户取消' },
  { value: 50, label: '超时取消' }
]

export const TICKET_STATUS_OPTIONS = [
  { value: 0, label: '等待中' },
  { value: 1, label: '叫号中' },
  { value: 3, label: '已完成' },
  { value: 4, label: '过号' }
]

/** yyyy-MM-dd，offset 为天数偏移 */
export function dateStr(offset = 0) {
  const d = new Date()
  d.setDate(d.getDate() + offset)
  const m = `${d.getMonth() + 1}`.padStart(2, '0')
  const day = `${d.getDate()}`.padStart(2, '0')
  return `${d.getFullYear()}-${m}-${day}`
}

/** 浏览器原生 UUID，极老环境退回时间戳 + 随机数 */
export function uuid() {
  if (window.crypto?.randomUUID) {
    return window.crypto.randomUUID()
  }
  return `${Date.now()}-${Math.random().toString(16).slice(2)}`
}
