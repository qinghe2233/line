<template>
  <div>
    <el-row :gutter="12">
      <el-col :span="6" v-for="card in cards" :key="card.label">
        <el-card shadow="never" class="stat-card">
          <div class="stat-value">{{ card.value }}</div>
          <div class="stat-label">{{ card.label }}</div>
        </el-card>
      </el-col>
    </el-row>

    <el-card shadow="never" style="margin-top: 12px">
      <el-form inline>
        <el-form-item label="门店">
          <el-select v-model="filter.storeId" placeholder="全部门店" clearable style="width: 170px" @change="reloadAll">
            <el-option v-for="s in stores" :key="s.storeId" :label="s.name" :value="s.storeId" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="filter.status" placeholder="全部状态" clearable style="width: 140px" @change="load">
            <el-option v-for="o in TICKET_STATUS_OPTIONS" :key="o.value" :label="o.label" :value="o.value" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button @click="reloadAll">刷新</el-button>
          <span class="muted">每 10 秒自动刷新</span>
        </el-form-item>
      </el-form>

      <el-table :data="rows" v-loading="loading" border stripe>
        <el-table-column prop="ticketNo" label="号码" width="110">
          <template #default="{ row }">
            <strong>{{ row.ticketNo }}</strong>
          </template>
        </el-table-column>
        <el-table-column label="服务项" min-width="130">
          <template #default="{ row }">{{ serviceName(row.serviceId) }}</template>
        </el-table-column>
        <el-table-column prop="seqNo" label="序号" width="80" />
        <el-table-column label="状态" width="110">
          <template #default="{ row }">
            <el-tag :type="TICKET_STATUS_TYPE[row.status] || 'info'">{{ row.statusDesc }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="calledAt" label="叫号时间" min-width="160">
          <template #default="{ row }">{{ row.calledAt || '-' }}</template>
        </el-table-column>
        <el-table-column prop="createdAt" label="取号时间" min-width="160" />
        <el-table-column label="操作" width="130" fixed="right">
          <template #default="{ row }">
            <el-button v-if="row.status === 0" type="primary" size="small" @click="call(row)">叫号</el-button>
            <el-button v-else-if="row.status === 1" type="success" size="small" @click="finish(row)">完成</el-button>
            <span v-else class="muted">-</span>
          </template>
        </el-table-column>
      </el-table>
    </el-card>
  </div>
</template>

<script setup>
import { computed, onMounted, onUnmounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { adminServices, adminTickets, callTicket, finishTicket, listStores, todayOverview } from '../../api'
import { TICKET_STATUS_OPTIONS, TICKET_STATUS_TYPE } from '../../utils/dict'

const stores = ref([])
const services = ref([])
const rows = ref([])
const overview = ref({})
const loading = ref(false)
let timer = null

const filter = reactive({ storeId: null, status: null })

const cards = computed(() => [
  { label: '今日取号', value: Number(overview.value.ticketTotal || 0) },
  { label: '等待中', value: Number(overview.value.waiting || 0) },
  { label: '叫号中', value: Number(overview.value.calling || 0) },
  { label: '已完成', value: Number(overview.value.ticketFinished || 0) }
])

const serviceMap = computed(() => {
  const map = {}
  services.value.forEach((s) => { map[s.id] = s.name })
  return map
})

function serviceName(serviceId) {
  return serviceMap.value[serviceId] || `服务 ${serviceId}`
}

async function load() {
  loading.value = true
  try {
    rows.value = (await adminTickets({
      storeId: filter.storeId || undefined,
      status: filter.status ?? undefined
    })) || []
  } finally {
    loading.value = false
  }
}

async function loadOverview() {
  overview.value = (await todayOverview(filter.storeId || undefined)) || {}
}

async function loadServices() {
  services.value = (await adminServices(filter.storeId || undefined)) || []
}

function reloadAll() {
  load()
  loadOverview()
  loadServices()
}

async function call(row) {
  const ok = await ElMessageBox.confirm(`叫号 ${row.ticketNo}？3 分钟内未到店将自动过号`, '叫号确认', { type: 'warning' })
    .catch(() => false)
  if (!ok) return
  await callTicket(row.id)
  ElMessage.success(`已叫号 ${row.ticketNo}`)
  reloadAll()
}

async function finish(row) {
  await finishTicket(row.id)
  ElMessage.success(`${row.ticketNo} 已完成`)
  reloadAll()
}

onMounted(async () => {
  stores.value = await listStores()
  reloadAll()
  timer = setInterval(() => {
    load()
    loadOverview()
  }, 10000)
})

onUnmounted(() => {
  if (timer) clearInterval(timer)
})
</script>

<style scoped>
.stat-card {
  text-align: center;
}
.stat-value {
  font-size: 26px;
  font-weight: 600;
  color: #303133;
}
.stat-label {
  margin-top: 4px;
  color: #909399;
  font-size: 13px;
}
.muted {
  color: #909399;
  font-size: 12px;
}
</style>
