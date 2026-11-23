<template>
  <div>
    <el-card shadow="never">
      <el-form inline>
        <el-form-item label="门店">
          <el-select v-model="storeId" placeholder="全部门店" clearable style="width: 180px" @change="load">
            <el-option v-for="s in stores" :key="s.storeId" :label="s.name" :value="s.storeId" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="openCreate">新增服务项</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card shadow="never" style="margin-top: 12px">
      <el-table :data="rows" v-loading="loading" border stripe>
        <el-table-column prop="name" label="服务名称" min-width="140" />
        <el-table-column label="时长" width="130">
          <template #default="{ row }">
            {{ row.durationMinutes }} 分钟
            <span v-if="row.bufferMinutes" class="muted">+{{ row.bufferMinutes }} 缓冲</span>
          </template>
        </el-table-column>
        <el-table-column prop="price" label="价格" width="100">
          <template #default="{ row }">¥{{ row.price }}</template>
        </el-table-column>
        <el-table-column prop="description" label="描述" min-width="200" show-overflow-tooltip />
        <el-table-column prop="sortNo" label="排序" width="80" />
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'info'">{{ row.status === 1 ? '已上架' : '已下架' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="150">
          <template #default="{ row }">
            <el-button type="primary" size="small" link @click="openEdit(row)">编辑</el-button>
            <el-button :type="row.status === 1 ? 'danger' : 'success'" size="small" link @click="toggleStatus(row)">
              {{ row.status === 1 ? '下架' : '上架' }}
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog v-model="dialogVisible" :title="form.id ? '编辑服务项' : '新增服务项'" width="520px">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="90px">
        <el-form-item label="门店" prop="storeId">
          <el-select v-model="form.storeId" style="width: 100%">
            <el-option v-for="s in stores" :key="s.storeId" :label="s.name" :value="s.storeId" />
          </el-select>
        </el-form-item>
        <el-form-item label="名称" prop="name">
          <el-input v-model="form.name" placeholder="如：剪发" />
        </el-form-item>
        <el-form-item label="服务时长" prop="durationMinutes">
          <el-input-number v-model="form.durationMinutes" :min="5" :max="600" :step="5" /> 分钟
        </el-form-item>
        <el-form-item label="缓冲时长">
          <el-input-number v-model="form.bufferMinutes" :min="0" :max="120" :step="5" /> 分钟
        </el-form-item>
        <el-form-item label="价格" prop="price">
          <el-input-number v-model="form.price" :min="0" :precision="2" :step="10" /> 元
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="form.description" type="textarea" :rows="2" maxlength="500" show-word-limit />
        </el-form-item>
        <el-form-item label="排序">
          <el-input-number v-model="form.sortNo" :min="0" :max="999" />
        </el-form-item>
        <el-form-item label="状态">
          <el-switch v-model="form.status" :active-value="1" :inactive-value="0" active-text="上架" inactive-text="下架" />
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
import { adminServices, createService, listStores, updateService } from '../../api'

const stores = ref([])
const rows = ref([])
const storeId = ref(null)
const loading = ref(false)
const saving = ref(false)
const dialogVisible = ref(false)
const formRef = ref()

const form = reactive({
  id: null,
  storeId: null,
  name: '',
  durationMinutes: 60,
  bufferMinutes: 0,
  price: 0,
  description: '',
  sortNo: 0,
  status: 1
})

const rules = {
  storeId: [{ required: true, message: '请选择门店', trigger: 'change' }],
  name: [{ required: true, message: '请输入服务名称', trigger: 'blur' }],
  durationMinutes: [{ required: true, message: '请输入服务时长', trigger: 'blur' }],
  price: [{ required: true, message: '请输入价格', trigger: 'blur' }]
}

async function load() {
  loading.value = true
  try {
    rows.value = (await adminServices(storeId.value || undefined)) || []
  } finally {
    loading.value = false
  }
}

function openCreate() {
  Object.assign(form, {
    id: null,
    storeId: storeId.value || stores.value[0]?.storeId || null,
    name: '',
    durationMinutes: 60,
    bufferMinutes: 0,
    price: 0,
    description: '',
    sortNo: 0,
    status: 1
  })
  dialogVisible.value = true
}

function openEdit(row) {
  Object.assign(form, {
    id: row.id,
    storeId: row.storeId,
    name: row.name,
    durationMinutes: row.durationMinutes,
    bufferMinutes: row.bufferMinutes,
    price: Number(row.price),
    description: row.description,
    sortNo: row.sortNo,
    status: row.status
  })
  dialogVisible.value = true
}

async function save() {
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return
  saving.value = true
  try {
    const payload = {
      storeId: form.storeId,
      name: form.name,
      durationMinutes: form.durationMinutes,
      bufferMinutes: form.bufferMinutes,
      price: form.price,
      description: form.description,
      sortNo: form.sortNo,
      status: form.status
    }
    if (form.id) {
      await updateService(form.id, payload)
    } else {
      await createService(payload)
    }
    ElMessage.success('保存成功')
    dialogVisible.value = false
    load()
  } finally {
    saving.value = false
  }
}

async function toggleStatus(row) {
  await updateService(row.id, {
    storeId: row.storeId,
    name: row.name,
    durationMinutes: row.durationMinutes,
    bufferMinutes: row.bufferMinutes,
    price: Number(row.price),
    description: row.description,
    sortNo: row.sortNo,
    status: row.status === 1 ? 0 : 1
  })
  ElMessage.success('状态已更新')
  load()
}

onMounted(async () => {
  stores.value = await listStores()
  await load()
})
</script>

<style scoped>
.muted {
  color: #909399;
  font-size: 12px;
}
</style>
