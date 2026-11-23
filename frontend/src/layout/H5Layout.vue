<template>
  <div class="h5-page">
    <header class="h5-header">
      <span>多门店预约</span>
      <span class="h5-user">
        {{ displayName }}
        <a class="h5-logout" @click="logout">退出</a>
      </span>
    </header>

    <main class="h5-main">
      <router-view />
    </main>

    <nav class="h5-tabbar">
      <router-link to="/h5" :class="{ active: route.path === '/h5' }">预约</router-link>
      <router-link to="/h5/my" :class="{ active: route.path === '/h5/my' }">我的预约</router-link>
    </nav>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'

const route = useRoute()
const router = useRouter()

const user = readUser()
const displayName = computed(() => user?.nickname || user?.phone || '未登录')

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
.h5-page {
  max-width: 480px;
  min-height: 100vh;
  margin: 0 auto;
  background: #f5f7fa;
  padding-bottom: 56px;
  box-sizing: border-box;
}
.h5-header {
  position: sticky;
  top: 0;
  z-index: 10;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 12px;
  height: 48px;
  font-weight: 600;
  background: #409eff;
  color: #fff;
}
.h5-user {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 12px;
  font-weight: 400;
}
.h5-logout {
  color: #fff;
  text-decoration: underline;
  cursor: pointer;
}
.h5-main {
  padding: 12px;
}
.h5-tabbar {
  position: fixed;
  bottom: 0;
  left: 0;
  right: 0;
  max-width: 480px;
  margin: 0 auto;
  display: flex;
  background: #fff;
  border-top: 1px solid #e4e7ed;
}
.h5-tabbar a {
  flex: 1;
  text-align: center;
  padding: 14px 0;
  color: #606266;
  text-decoration: none;
  font-size: 14px;
}
.h5-tabbar a.active {
  color: #409eff;
  font-weight: 600;
}
</style>
