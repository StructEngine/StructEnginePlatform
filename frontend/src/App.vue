<script setup>
import { computed, onBeforeUnmount, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuth } from './stores/auth'

const route = useRoute()
const router = useRouter()
const auth = useAuth()

const isAuthPage = computed(() => Boolean(route.meta.public))
const showNav = computed(() => Boolean(auth.state.user) && !isAuthPage.value)
const wide = computed(() => route.name === 'server-detail' || route.path.startsWith('/admin/'))
const title = computed(() => route.meta.title || 'StructEngine')
const subtitle = computed(() => route.meta.subtitle || '')

const navItems = computed(() => {
  if (!auth.state.user) {
    return []
  }
  if (auth.isAdmin.value) {
    return [
      { path: '/admin/users', label: '用户管理' },
      { path: '/servers', label: '服务器管理' },
      { path: '/admin/models', label: '全部模型' },
      { path: '/admin/audit', label: '审计日志' },
    ]
  }
  return [{ path: '/servers', label: '我的服务器' }]
})

function isActive(path) {
  return route.path === path || (path === '/servers' && route.path.startsWith('/servers/'))
}

function logout() {
  auth.logout()
  router.replace('/login')
}

function handleUnauthorized() {
  auth.logout()
  router.replace('/login')
}

onMounted(() => {
  window.addEventListener('structengine:unauthorized', handleUnauthorized)
})

onBeforeUnmount(() => {
  window.removeEventListener('structengine:unauthorized', handleUnauthorized)
})
</script>

<template>
  <div class="app-shell">
    <header class="app-header">
      <div class="app-header-inner">
        <router-link :to="auth.defaultRoute.value" class="app-brand">
          <span class="app-brand-main">StructEngine</span>
          <span class="app-brand-badge">Platform</span>
        </router-link>

        <nav v-if="showNav" class="app-nav" aria-label="功能导航">
          <router-link
            v-for="item in navItems"
            :key="item.path"
            :to="item.path"
            class="nav-item"
            :class="{ 'is-active': isActive(item.path) }"
          >
            {{ item.label }}
          </router-link>
        </nav>

        <div v-if="auth.state.user && !isAuthPage" class="app-user">
          <span class="app-user-name">{{ auth.state.user.username }}</span>
          <span class="app-user-role">
            {{ auth.state.user.role === 'ADMIN' ? '管理员' : '服主' }}
          </span>
          <router-link to="/change-password" class="app-user-link">改密</router-link>
          <button type="button" class="app-logout" @click="logout">退出</button>
        </div>
      </div>
    </header>

    <section v-if="!isAuthPage" class="page-title">
      <h1>{{ title }}</h1>
      <p v-if="subtitle">{{ subtitle }}</p>
    </section>

    <main class="app-main" :class="{ 'app-main-wide': wide }">
      <router-view />
    </main>
  </div>
</template>

<style scoped>
.app-shell {
  min-height: 100vh;
  display: flex;
  flex-direction: column;
}

.app-header {
  border-bottom: 1px solid var(--rc-panel-border);
  background: rgba(10, 15, 11, 0.72);
  backdrop-filter: blur(8px);
}

.app-header-inner {
  max-width: 1280px;
  margin: 0 auto;
  min-height: 62px;
  padding: 0 20px;
  display: flex;
  align-items: center;
  gap: 24px;
}

.app-brand {
  display: flex;
  align-items: center;
  gap: 8px;
  color: var(--rc-text);
  text-decoration: none;
  white-space: nowrap;
}

.app-brand-main {
  color: var(--el-color-primary);
  font-size: 17px;
  font-weight: 800;
  letter-spacing: 2px;
}

.app-brand-badge {
  padding: 2px 8px;
  border: 1px solid var(--rc-panel-border);
  border-radius: 999px;
  color: var(--rc-text-dim);
  font-size: 11px;
  letter-spacing: 1px;
}

.app-nav {
  display: flex;
  gap: 4px;
  padding: 4px;
  border: 1px solid var(--rc-panel-border);
  border-radius: 999px;
  background: var(--rc-panel);
}

.nav-item {
  padding: 7px 14px;
  border-radius: 999px;
  color: var(--rc-text-dim);
  font-size: 13px;
  text-decoration: none;
  white-space: nowrap;
  transition: color 0.18s ease, background 0.18s ease;
}

.nav-item:hover {
  color: var(--rc-text);
}

.nav-item.is-active {
  background: rgba(124, 189, 75, 0.16);
  color: var(--el-color-primary);
}

.app-user {
  margin-left: auto;
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 12px;
  color: var(--rc-text-dim);
}

.app-user-name {
  color: var(--rc-text);
  font-weight: 600;
}

.app-user-role {
  padding: 1px 7px;
  border: 1px solid var(--rc-panel-border);
  border-radius: 999px;
  font-size: 10px;
}

.app-user-link {
  color: var(--rc-text-dim);
}

.app-logout {
  padding: 4px 8px;
  border: 1px solid var(--rc-panel-border);
  border-radius: 6px;
  background: transparent;
  color: var(--rc-text-dim);
  font-size: 12px;
  cursor: pointer;
}

.app-logout:hover {
  color: var(--rc-text);
  border-color: rgba(255, 255, 255, 0.2);
}

.page-title {
  max-width: 1280px;
  width: 100%;
  margin: 0 auto;
  padding: 36px 20px 0;
}

.page-title h1 {
  margin: 0;
  font-size: 28px;
  font-weight: 700;
  letter-spacing: 1px;
}

.page-title p {
  margin: 8px 0 0;
  color: var(--rc-text-dim);
  font-size: 13px;
}

.app-main {
  flex: 1;
  width: 100%;
  max-width: 980px;
  margin: 0 auto;
  padding: 24px 20px 64px;
}

.app-main-wide {
  max-width: 1280px;
}

@media (max-width: 760px) {
  .app-header-inner {
    flex-wrap: wrap;
    padding: 10px 14px;
  }

  .app-user {
    width: 100%;
    justify-content: flex-end;
  }

  .app-nav {
    order: 3;
    width: 100%;
    justify-content: center;
  }
}
</style>
