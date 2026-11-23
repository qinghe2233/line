<template>
  <el-container class="admin-layout">
    <el-aside width="210px" class="aside">
      <div class="logo">预约排队后台</div>
      <el-menu :default-active="activeMenu" router background-color="#304156" text-color="#bfcbd9"
               active-text-color="#409eff">
        <el-menu-item index="/admin/appointments">预约列表</el-menu-item>
        <el-menu-item index="/admin/schedule">排班管理</el-menu-item>
        <el-menu-item index="/admin/services">服务项管理</el-menu-item>
        <el-menu-item index="/admin/queue">叫号面板</el-menu-item>
      </el-menu>
    </el-aside>

    <el-container>
      <el-header class="header">
        <span class="title">{{ route.meta.title || '管理后台' }}</span>
        <div class="right">
          <el-link href="/h5" target="_blank" type="primary">顾客端 H5</el-link>
          <span class="user">{{ displayName }}</span>
          <el-tag v-if="roleName" size="small" type="success">{{ roleName }}</el-tag>
          <el-button link type="danger" @click="logout">退出</el-button>
        </div>
      </el-header>
      <el-main>
        <router-view />
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup>
import { computed, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'

const route = useRoute()
const router = useRouter()
const activeMenu = computed(() => route.path)

// 用户信息来自登录接口返回的 LoginVO，不再信任任何请求头
const user = ref(readUser())
const displayName = computed(() => user.value?.nickname || user.value?.phone || '未登录')
const roleName = computed(() => user.value?.roleName || '')

function readUser() {
  try {
    return JSON.parse(localStorage.getItem('user') || 'null')
  } catch {
    return null
  }
}

function logout() {
  localStorage.removeItem('token')
  localStorage.removeItem('user')
  router.replace('/login')
}
</script>

<style scoped>
.admin-layout {
  min-height: 100vh;
}
.aside {
  background: #304156;
}
.logo {
  height: 56px;
  line-height: 56px;
  text-align: center;
  color: #fff;
  font-weight: 600;
  letter-spacing: 1px;
}
.aside :deep(.el-menu) {
  border-right: none;
}
.header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  background: #fff;
  border-bottom: 1px solid #e4e7ed;
}
.title {
  font-size: 16px;
  font-weight: 600;
}
.right {
  display: flex;
  align-items: center;
  gap: 16px;
}
.user {
  color: #606266;
  font-size: 14px;
}
</style>
