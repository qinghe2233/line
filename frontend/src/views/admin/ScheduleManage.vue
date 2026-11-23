<template>
  <div>
    <el-card shadow="never">
      <el-form inline>
        <el-form-item label="门店">
          <el-select v-model="filter.storeId" placeholder="全部门店" clearable style="width: 170px" @change="load">
            <el-option v-for="s in stores" :key="s.storeId" :label="s.name" :value="s.storeId" />
          </el-select>
        </el-form-item>
        <el-form-item label="日期">
          <el-date-picker v-model="filter.date" type="date" value-format="YYYY-MM-DD"
                          placeholder="全部日期" clearable style="width: 150px" @change="load" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="openCreate">新增时段</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card shadow="never" style="margin-top: 12px">
      <el-table :data="rows" v-loading="loading" border stripe>
        <el-table-column prop="slotDate" label="日期" width="120" />
        <el-table-column label="时段" width="150">
          <template #default="{ row }">{{ row.startTime }}-{{ row.endTime }}</template>
        </el-table-column>
        <el-table-column prop="serviceName" label="服务项" min-width="120" />
        <el-table-column prop="resourceName" label="资源" min-width="110" />
        <el-table-column label="名额（剩余/总数）" width="160">
          <template #default="{ row }">
            <el-tag :type="row.remainStock > 0 ? 'success' : 'danger'">
              {{ row.remainStock }} / {{ row.totalStock }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'info'">
              {{ row.status === 1 ? '可预约' : '已关闭' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="100">
          <template #default="{ row }">
            <el-button type="primary" size="small" link @click="openEdit(row)">编辑</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog v-model="dialogVisible" :title="form.id ? '编辑排班' : '新增排班'" width="520px">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="90px">
        <el-form-item label="门店" prop="storeId">
          <el-select v-model="form.storeId" style="width: 100%" @change="onStoreChange">
            <el-option v-for="s in stores" :key="s.storeId" :label="s.name" :value="s.storeId" />
          </el-select>
        </el-form-item>
        <el-form-item label="服务项" prop="serviceId">
          <el-select v-model="form.serviceId" placeholder="选择服务" style="width: 100%">
            <el-option v-for="s in services" :key="s.id" :label="`${s.name}（${s.durationMinutes}分钟 / ¥${s.price}）`" :value="s.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="资源" prop="resourceId">
          <el-select v-model="form.resourceId" placeholder="选择技师/工位" style="width: 100%">
            <el-option v-for="r in resources" :key="r.id" :label="r.name" :value="r.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="日期" prop="slotDate">
          <el-date-picker v-model="form.slotDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
        </el-form-item>
        <el-form-item label="开始时间" prop="startTime">
          <el-time-picker v-model="form.startTime" format="HH:mm" value-format="HH:mm:ss" style="width: 100%" />
        </el-form-item>
        <el-form-item label="结束时间" prop="endTime">
          <el-time-picker v-model="form.endTime" format="HH:mm" value-format="HH:mm:ss" style="width: 100%" />
        </el-form-item>
        <el-form-item label="名额" prop="totalStock">
          <el-input-number v-model="form.totalStock" :min="1" :max="999" />
        </el-form-item>
        <el-form-item label="状态">
          <el-switch v-model="form.status" :active-value="1" :inactive-value="0" active-text="可预约" inactive-text="关闭" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="save">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import {
  adminResources, adminServices, adminSlots, createSlot, listStores, updateSlot
} from '../../api'
import { dateStr } from '../../utils/dict'

const stores = ref([])
const services = ref([])
const resources = ref([])
const rows = ref([])
const loading = ref(false)
const saving = ref(false)
const dialogVisible = ref(false)
const formRef = ref()

const filter = reactive({ storeId: null, date: null })

const form = reactive({
  id: null,
  storeId: null,
  serviceId: null,
  resourceId: null,
  slotDate: dateStr(1),
  startTime: '10:00:00',
  endTime: '11:00:00',
  totalStock: 5,
  status: 1
})

const rules = {
  storeId: [{ required: true, message: '请选择门店', trigger: 'change' }],
  serviceId: [{ required: true, message: '请选择服务项', trigger: 'change' }],
  resourceId: [{ required: true, message: '请选择资源', trigger: 'change' }],
  slotDate: [{ required: true, message: '请选择日期', trigger: 'change' }],
  startTime: [{ required: true, message: '请选择开始时间', trigger: 'change' }],
  endTime: [{ required: true, message: '请选择结束时间', trigger: 'change' }],
  totalStock: [{ required: true, message: '请输入名额', trigger: 'blur' }]
}

async function load() {
  loading.value = true
  try {
    const data = await adminSlots({
      storeId: filter.storeId || undefined,
      date: filter.date || undefined
    })
    rows.value = data || []
  } finally {
    loading.value = false
  }
}

async function loadOptions(storeId) {
  if (!storeId) {
    services.value = []
    resources.value = []
    return
  }
  const [svc, res] = await Promise.all([adminServices(storeId), adminResources(storeId)])
  services.value = svc || []
  resources.value = res || []
}

function onStoreChange(storeId) {
  form.serviceId = null
  form.resourceId = null
  loadOptions(storeId)
}

function resetForm() {
  Object.assign(form, {
    id: null,
    storeId: filter.storeId || stores.value[0]?.storeId || null,
    serviceId: null,
    resourceId: null,
    slotDate: filter.date || dateStr(1),
    startTime: '10:00:00',
    endTime: '11:00:00',
    totalStock: 5,
    status: 1
  })
}

function openCreate() {
  resetForm()
  loadOptions(form.storeId)
  dialogVisible.value = true
}

function openEdit(row) {
  Object.assign(form, {
    id: row.id,
    storeId: row.storeId,
    serviceId: row.serviceId,
    resourceId: row.resourceId,
    slotDate: row.slotDate,
    startTime: row.startTime,
    endTime: row.endTime,
    totalStock: row.totalStock,
    status: row.status
  })
  loadOptions(row.storeId)
  dialogVisible.value = true
}

async function save() {
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return
  saving.value = true
  try {
    const payload = {
      storeId: form.storeId,
      serviceId: form.serviceId,
      resourceId: form.resourceId,
      slotDate: form.slotDate,
      startTime: form.startTime,
      endTime: form.endTime,
      totalStock: form.totalStock,
      status: form.status
    }
    if (form.id) {
      await updateSlot(form.id, payload)
    } else {
      await createSlot(payload)
    }
    ElMessage.success('保存成功')
    dialogVisible.value = false
    load()
  } finally {
    saving.value = false
  }
}

onMounted(async () => {
  stores.value = await listStores()
  await load()
})
</script>
