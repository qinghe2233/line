<template>
  <div class="login-page">
    <el-card class="login-card">
      <h2>多门店预约排队系统</h2>
      <p class="subtitle">手机号 + 密码登录</p>

      <el-form ref="formRef" :model="form" :rules="rules" label-width="0" @keyup.enter="submit">
        <el-form-item prop="phone">
          <el-input v-model="form.phone" placeholder="手机号" size="large" maxlength="11" />
        </el-form-item>
        <el-form-item prop="password">
          <el-input v-model="form.password" type="password" placeholder="密码" size="large" show-password />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" size="large" style="width: 100%" :loading="loading" @click="submit">
            登录
          </el-button>
        </el-form-item>
      </el-form>

      <el-alert type="info" :closable="false" show-icon title="演示账号（密码均为 123456）">
        <div class="demo-row">
          <span>店长：13900000000</span>
          <el-button link type="primary" size="small" @click="fill('13900000000')">填入</el-button>
        </div>
        <div class="demo-row">
          <span>顾客：13800001000</span>
          <el-button link type="primary" size="small" @click="fill('13800001000')">填入</el-button>
        </div>
      </el-alert>
    </el-card>
  </div>
</template>

<script setup>
import { reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { login } from '../api'

const router = useRouter()
const route = useRoute()
const formRef = ref()
const loading = ref(false)

const form = reactive({ phone: '13900000000', password: '123456' })

const rules = {
  phone: [
    { required: true, message: '请输入手机号', trigger: 'blur' },
    { pattern: /^1[3-9]\d{9}$/, message: '手机号格式不正确', trigger: 'blur' }
  ],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }]
}

function fill(phone) {
  form.phone = phone
  form.password = '123456'
}

/**
 * 真实登录：token 与用户信息都由后端返回。
 * 角色 >= 1 默认进管理端，顾客进 H5；带 redirect 时优先回原页面，
 * 但顾客不能借 redirect 绕过角色限制。
 */
async function submit() {
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return

  loading.value = true
  try {
    const data = await login({ phone: form.phone.trim(), password: form.password })
    localStorage.setItem('token', data.token)
    localStorage.setItem('user', JSON.stringify(data))
    ElMessage.success(`欢迎回来，${data.nickname || data.phone}`)

    let target = route.query.redirect || (data.role >= 1 ? '/admin/appointments' : '/h5')
    if (typeof target === 'string' && target.startsWith('/admin') && data.role < 1) {
      target = '/h5'
    }
    router.replace(target)
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.login-page {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #e8f0fe, #f5f7fa);
}
.login-card {
  width: 380px;
  padding: 12px 8px;
}
.login-card h2 {
  margin: 8px 0 4px;
  text-align: center;
}
.subtitle {
  margin: 0 0 24px;
  text-align: center;
  color: #909399;
  font-size: 13px;
}
.demo-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-size: 13px;
  line-height: 1.9;
}
</style>
