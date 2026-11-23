<template>
  <div>
    <el-card shadow="never">
      <el-form :model="query" inline>
        <el-form-item label="门店">
          <el-select v-model="query.storeId" placeholder="全部门店" clearable style="width: 160px">
            <el-option v-for="s in stores" :key="s.storeId" :label="s.name" :value="s.storeId" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="query.status" placeholder="全部状态" clearable style="width: 140px">
            <el-option v-for="o in ORDER_STATUS_OPTIONS" :key="o.value" :label="o.label" :value="o.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="预约日期">
          <el-date-picker v-model="query.slotDate" type="date" value-format="YYYY-MM-DD"
                          placeholder="选择日期" clearable style="width: 150px" />
        </el-form-item>
        <el-form-item label="订单号">
          <el-input v-model="query.orderNo" placeholder="支持模糊" clearable style="width: 180px" />
        </el-form-item>
        <el-form-item label="手机号">
          <el-input v-model="query.phone" placeholder="支持前缀" clearable style="width: 140px" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="search">查询</el-button>
          <el-button @click="reset">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card shadow="never" style="margin-top: 12px">
      <el-table :data="rows" v-loading="loading" border stripe>
        <el-table-column prop="orderNo" label="订单号" min-width="200" />
        <el-table-column label="顾客" min-width="130">
          <template #default="{ row }">
            {{ row.userNickname || '-' }}<br />
            <span class="muted">{{ row.userPhone || '-' }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="serviceName" label="服务项" min-width="120" />
        <el-table-column label="预约时间" min-width="180">
          <template #default="{ row }">
            {{ row.slotDate }} {{ row.startTime }}-{{ row.endTime }}
          </template>
        </el-table-column>
        <el-table-column prop="amount" label="金额" width="90" />
        <el-table-column label="状态" width="110">
          <template #default="{ row }">
            <el-tag :type="ORDER_STATUS_TYPE[row.status] || 'info'">{{ row.statusDesc }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createdAt" label="下单时间" min-width="160" />
        <el-table-column label="操作" width="170" fixed="right">
          <template #default="{ row }">
            <el-button v-if="row.status === 10" type="primary" size="small" link @click="verify(row)">
              核销
            </el-button>
            <el-button v-if="row.status === 10" type="danger" size="small" link @click="noShow(row)">
              爽约
            </el-button>
            <span v-if="row.status !== 10" class="muted">-</span>
          </template>
        </el-table-column>
      </el-table>

      <el-pagination style="margin-top: 12px; justify-content: flex-end"
                     layout="total, sizes, prev, pager, next"
                     :total="total" :current-page="query.page" :page-size="query.size"
                     :page-sizes="[10, 20, 50]"
                     @current-change="onPageChange" @size-change="onSizeChange" />
    </el-card>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { adminAppointments, listStores, noShowAppointment, verifyAppointment } from '../../api'
import { ORDER_STATUS_OPTIONS, ORDER_STATUS_TYPE } from '../../utils/dict'

const stores = ref([])
const rows = ref([])
const total = ref(0)
const loading = ref(false)

const query = reactive({
  page: 1,
  size: 10,
  storeId: null,
  status: null,
  slotDate: null,
  orderNo: '',
  phone: ''
})

async function load() {
  loading.value = true
  try {
    const params = {
      page: query.page,
      size: query.size,
      storeId: query.storeId || undefined,
      status: query.status ?? undefined,
      slotDate: query.slotDate || undefined,
      orderNo: query.orderNo || undefined,
      phone: query.phone || undefined
    }
    const data = await adminAppointments(params)
    rows.value = data.records || []
    // 后端把 Long 序列化成字符串，分页数字要转回 number 才能绑定组件
    total.value = Number(data.total || 0)
  } finally {
    loading.value = false
  }
}

function search() {
  query.page = 1
  load()
}

function reset() {
  Object.assign(query, { page: 1, size: 10, storeId: null, status: null, slotDate: null, orderNo: '', phone: '' })
  load()
}

function onPageChange(page) {
  query.page = page
  load()
}

function onSizeChange(size) {
  query.size = size
  query.page = 1
  load()
}

async function verify(row) {
  const ok = await ElMessageBox.confirm(`确认核销订单 ${row.orderNo}？`, '核对到店', { type: 'warning' })
    .catch(() => false)
  if (!ok) return
  await verifyAppointment(row.id)
  ElMessage.success('核销成功')
  load()
}

async function noShow(row) {
  const ok = await ElMessageBox.confirm(`确认将订单 ${row.orderNo} 标记为爽约？`, '提示', { type: 'warning' })
    .catch(() => false)
  if (!ok) return
  await noShowAppointment(row.id)
  ElMessage.success('已标记爽约')
  load()
}

onMounted(async () => {
  stores.value = await listStores()
  load()
})
</script>

<style scoped>
.muted {
  color: #909399;
  font-size: 12px;
}
</style>
