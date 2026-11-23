<template>
  <div>
    <el-card shadow="never">
      <el-result icon="warning" title="待支付" :sub-title="`订单号 ${orderNo}`">
        <template #extra>
          <el-descriptions :column="1" border style="margin-bottom: 16px">
            <el-descriptions-item label="服务">{{ serviceName }}</el-descriptions-item>
            <el-descriptions-item label="时间">{{ slotDate }} {{ startTime }}-{{ endTime }}</el-descriptions-item>
            <el-descriptions-item label="应付金额">
              <span class="amount">¥{{ amount }}</span>
            </el-descriptions-item>
          </el-descriptions>

          <el-button type="primary" size="large" style="width: 100%" :loading="paying" @click="pay">
            模拟支付
          </el-button>
          <el-button link style="margin-top: 10px" @click="router.replace('/h5/my')">
            暂不支付，查看我的预约
          </el-button>
        </template>
      </el-result>
    </el-card>
  </div>
</template>

<script setup>
import { computed, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { mockPay } from '../../api'

const route = useRoute()
const router = useRouter()
const paying = ref(false)

const orderNo = computed(() => route.query.orderNo || '')
const amount = computed(() => route.query.amount || '0.00')
const serviceName = computed(() => route.query.serviceName || '-')
const slotDate = computed(() => route.query.slotDate || '-')
const startTime = computed(() => route.query.startTime || '')
const endTime = computed(() => route.query.endTime || '')

/**
 * 模拟支付：服务端自己生成三方流水号与签名，再走和真实回调同一条幂等链路。
 * 所以这里重复点击也不会重复扣款/重复加积分。
 */
async function pay() {
  if (!orderNo.value) {
    ElMessage.error('订单号缺失，请重新下单')
    router.replace('/h5')
    return
  }
  paying.value = true
  try {
    await mockPay(orderNo.value)
    ElMessage.success('支付成功')
    router.replace('/h5/my')
  } finally {
    paying.value = false
  }
}
</script>

<style scoped>
.amount {
  color: #f56c6c;
  font-size: 20px;
  font-weight: 600;
}
</style>
