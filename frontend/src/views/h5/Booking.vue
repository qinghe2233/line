<template>
  <div>
    <el-steps :active="step" finish-status="success" simple style="margin-bottom: 12px">
      <el-step title="选门店" />
      <el-step title="选服务" />
      <el-step title="选时段" />
      <el-step title="确认下单" />
    </el-steps>

    <!-- 第一步：门店 -->
    <div v-if="step === 0">
      <el-card v-for="s in stores" :key="s.storeId" shadow="hover" class="click-card" @click="chooseStore(s)">
        <div class="card-title">{{ s.name }}</div>
        <div class="muted">{{ s.city }} · {{ s.address }}</div>
        <div class="muted">营业时间 {{ s.businessStart }}-{{ s.businessEnd }}</div>
      </el-card>
      <el-empty v-if="!stores.length" description="暂无营业中的门店" />
    </div>

    <!-- 第二步：服务项 -->
    <div v-if="step === 1">
      <el-page-header content="选择服务" @back="step = 0" style="margin-bottom: 12px" />
      <el-card v-for="s in services" :key="s.id" shadow="hover" class="click-card" @click="chooseService(s)">
        <div class="card-title">
          {{ s.name }}
          <span class="price">¥{{ s.price }}</span>
        </div>
        <div class="muted">{{ s.durationMinutes }} 分钟<span v-if="s.description"> · {{ s.description }}</span></div>
      </el-card>
      <el-empty v-if="!services.length" description="该门店暂无可预约服务" />
    </div>

    <!-- 第三步：日期 + 时段 -->
    <div v-if="step === 2">
      <el-page-header content="选择时段" @back="step = 1" style="margin-bottom: 12px" />
      <el-card shadow="never" style="margin-bottom: 12px">
        <div class="muted">{{ store?.name }} · {{ service?.name }} · ¥{{ service?.price }}</div>
        <el-date-picker v-model="date" type="date" value-format="YYYY-MM-DD" placeholder="选择日期"
                        style="width: 100%; margin-top: 10px" @change="loadSlots" />
      </el-card>

      <el-card v-for="slot in slots" :key="slot.slotId" shadow="hover" class="click-card"
               :class="{ selected: selectedSlot?.slotId === slot.slotId }" @click="chooseSlot(slot)">
        <div class="card-title">
          {{ slot.startTime }}-{{ slot.endTime }}
          <el-tag size="small" type="success">剩 {{ slot.remainStock }}</el-tag>
        </div>
        <div class="muted">资源：{{ slot.resourceName }}</div>
      </el-card>
      <el-empty v-if="!loading && !slots.length" description="当天无可约时段，请换个日期" />
    </div>

    <!-- 第四步：确认下单 -->
    <div v-if="step === 3">
      <el-page-header content="确认订单" @back="step = 2" style="margin-bottom: 12px" />
      <el-card shadow="never">
        <el-descriptions :column="1" border>
          <el-descriptions-item label="门店">{{ store?.name }}</el-descriptions-item>
          <el-descriptions-item label="服务">{{ service?.name }}</el-descriptions-item>
          <el-descriptions-item label="时间">{{ date }} {{ selectedSlot?.startTime }}-{{ selectedSlot?.endTime }}</el-descriptions-item>
          <el-descriptions-item label="资源">{{ selectedSlot?.resourceName }}</el-descriptions-item>
          <el-descriptions-item label="金额">¥{{ service?.price }}</el-descriptions-item>
        </el-descriptions>

        <el-input v-model="remark" type="textarea" :rows="2" maxlength="255" show-word-limit
                  placeholder="备注（选填，如希望安排店长）" style="margin-top: 12px" />

        <el-button type="primary" size="large" style="width: 100%; margin-top: 16px"
                   :loading="submitting" @click="submit">
          提交订单
        </el-button>
        <p class="muted center">提交后请在 15 分钟内完成支付，超时自动取消</p>
      </el-card>
    </div>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { createAppointment, listSlots, listStoreServices, listStores } from '../../api'
import { dateStr, uuid } from '../../utils/dict'

const router = useRouter()

const step = ref(0)
const stores = ref([])
const services = ref([])
const slots = ref([])
const loading = ref(false)
const submitting = ref(false)

const store = ref(null)
const service = ref(null)
const date = ref(dateStr(1))
const selectedSlot = ref(null)
const remark = ref('')

onMounted(async () => {
  stores.value = (await listStores()) || []
})

async function chooseStore(s) {
  store.value = s
  services.value = (await listStoreServices(s.storeId)) || []
  step.value = 1
}

async function chooseService(s) {
  service.value = s
  selectedSlot.value = null
  step.value = 2
  await loadSlots()
}

async function loadSlots() {
  if (!store.value || !service.value) return
  loading.value = true
  try {
    slots.value = (await listSlots(store.value.storeId, service.value.id, date.value)) || []
  } finally {
    loading.value = false
  }
}

function chooseSlot(slot) {
  selectedSlot.value = slot
  step.value = 3
}

async function submit() {
  if (!selectedSlot.value) {
    ElMessage.warning('请先选择时段')
    return
  }
  submitting.value = true
  try {
    // 幂等键：一次点击生成一次；网络重试时复用，不会重复占名额
    const order = await createAppointment({ slotId: selectedSlot.value.slotId, remark: remark.value }, uuid())
    router.replace({
      path: '/h5/pay',
      query: {
        orderNo: order.orderNo,
        amount: order.amount,
        serviceName: order.serviceName,
        slotDate: order.slotDate,
        startTime: order.startTime,
        endTime: order.endTime
      }
    })
  } finally {
    submitting.value = false
  }
}
</script>

<style scoped>
.click-card {
  margin-bottom: 10px;
  cursor: pointer;
  border: 1px solid transparent;
}
.click-card.selected {
  border-color: #409eff;
}
.card-title {
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-weight: 600;
  margin-bottom: 6px;
}
.price {
  color: #f56c6c;
}
.muted {
  color: #909399;
  font-size: 12px;
  line-height: 1.8;
}
.center {
  text-align: center;
}
</style>
