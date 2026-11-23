<template>
  <div>
    <el-tabs v-model="status" @tab-change="search">
      <el-tab-pane label="全部" name="all" />
      <el-tab-pane label="待支付" name="0" />
      <el-tab-pane label="已支付" name="10" />
      <el-tab-pane label="已核销" name="20" />
    </el-tabs>

    <el-card v-for="row in rows" :key="row.orderId" shadow="never" class="order-card">
      <div class="row">
        <span class="service">{{ row.serviceName }}</span>
        <el-tag :type="ORDER_STATUS_TYPE[row.status] || 'info'">{{ row.statusDesc }}</el-tag>
      </div>
      <div class="muted">{{ row.slotDate }} {{ row.startTime }}-{{ row.endTime }}</div>
      <div class="muted">订单号 {{ row.orderNo }}</div>
      <div class="row" style="margin-top: 8px">
        <span class="amount">¥{{ row.amount }}</span>
        <div class="actions">
          <el-button v-if="row.status === 0" size="small" @click="onCancel(row)">取消</el-button>
          <el-button v-if="row.status === 0" type="primary" size="small" @click="goPay(row)">去支付</el-button>
        </div>
      </div>
    </el-card>

    <el-empty v-if="!loading && !rows.length" description="暂无预约记录" />

    <el-pagination v-if="total > query.size" small background layout="prev, pager, next"
                   :total="total" :current-page="query.page" :page-size="query.size"
                   style="margin-top: 12px; justify-content: center"
                   @current-change="onPageChange" />
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { cancelAppointment, myAppointments } from '../../api'
import { ORDER_STATUS_TYPE } from '../../utils/dict'

const router = useRouter()
const status = ref('all')
const rows = ref([])
const total = ref(0)
const loading = ref(false)

const query = reactive({ page: 1, size: 10 })

async function load() {
  loading.value = true
  try {
    const data = await myAppointments({
      page: query.page,
      size: query.size,
      status: status.value === 'all' ? undefined : Number(status.value)
    })
    rows.value = data.records || []
    total.value = Number(data.total || 0)
  } finally {
    loading.value = false
  }
}

function search() {
  query.page = 1
  load()
}

function onPageChange(page) {
  query.page = page
  load()
}

/**
 * 取消待支付订单。
 * 二次确认避免误触；成功后刷新列表，订单状态由后端状态机决定，
 * 前端不做乐观更新，防止与「支付/超时关单」并发时展示错误状态。
 */
async function onCancel(row) {
  try {
    await ElMessageBox.confirm(
      `确定取消「${row.serviceName}」${row.slotDate} ${row.startTime} 的预约吗？`,
      '取消预约',
      { type: 'warning', confirmButtonText: '确定取消', cancelButtonText: '再想想' }
    )
  } catch {
    return
  }

  try {
    await cancelAppointment(row.orderId)
  } catch {
    // 失败提示已由 axios 拦截器统一弹出（如订单已被支付/超时关闭），这里只需不刷新
    return
  }
  ElMessage.success('预约已取消')
  // 当前在「待支付」页签时取消后该条会消失，回到第一页重新拉取
  if (status.value !== 'all') {
    query.page = 1
  }
  load()
}

function goPay(row) {
  router.push({
    path: '/h5/pay',
    query: {
      orderNo: row.orderNo,
      amount: row.amount,
      serviceName: row.serviceName,
      slotDate: row.slotDate,
      startTime: row.startTime,
      endTime: row.endTime
    }
  })
}

onMounted(load)
</script>

<style scoped>
.order-card {
  margin-bottom: 10px;
}
.row {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.service {
  font-weight: 600;
}
.muted {
  color: #909399;
  font-size: 12px;
  line-height: 1.8;
}
.amount {
  color: #f56c6c;
  font-weight: 600;
}
.actions {
  display: flex;
  align-items: center;
}
</style>
