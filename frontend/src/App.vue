<script setup>
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import GeneratePage from './views/GeneratePage.vue'
import BuildStudioPage from './views/BuildStudioPage.vue'

const route = ref('build')

const titles = {
  build: {
    title: 'StructEngine AI 地图建造',
    subtitle: 'BlueMap 选点 · 玩家选择 · AI 模型生成 · 部署与回滚',
  },
  studio: {
    title: 'StructEngine 像素三视图转换器',
    subtitle: '上传像素化三视图，生成 Minecraft 体素模型',
  },
}

const current = computed(() => titles[route.value] || titles.studio)

function syncRoute() {
  route.value = window.location.hash.startsWith('#/studio') ? 'studio' : 'build'
}

function navigate(name) {
  window.location.hash = name === 'studio' ? '#/studio' : '#/build'
}

onMounted(() => {
  syncRoute()
  window.addEventListener('hashchange', syncRoute)
})

onBeforeUnmount(() => {
  window.removeEventListener('hashchange', syncRoute)
})
</script>

<template>
  <div class="app-shell">
    <header class="app-header">
      <h1 class="app-title">{{ current.title }}</h1>
      <p class="app-subtitle">{{ current.subtitle }}</p>
      <nav class="app-nav" aria-label="功能导航">
        <button
          type="button"
          class="nav-item"
          :class="{ 'is-active': route === 'build' }"
          @click="navigate('build')"
        >
          AI 地图建造
        </button>
        <button
          type="button"
          class="nav-item"
          :class="{ 'is-active': route === 'studio' }"
          @click="navigate('studio')"
        >
          三视图建模
        </button>
      </nav>
    </header>
    <main class="app-main" :class="{ 'app-main-wide': route === 'build' }">
      <BuildStudioPage v-if="route === 'build'" />
      <GeneratePage v-else />
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
  text-align: center;
  padding: 44px 20px 4px;
}

.app-title {
  margin: 0;
  font-size: 32px;
  font-weight: 700;
  letter-spacing: 3px;
  color: var(--rc-text);
}

.app-subtitle {
  margin: 10px 0 0;
  color: var(--rc-text-dim);
  font-size: 14px;
  letter-spacing: 1px;
}

.app-nav {
  display: inline-flex;
  gap: 4px;
  margin-top: 22px;
  padding: 4px;
  border: 1px solid var(--rc-panel-border);
  border-radius: 999px;
  background: var(--rc-panel);
}

.nav-item {
  padding: 9px 18px;
  border: 0;
  border-radius: 999px;
  background: transparent;
  color: var(--rc-text-dim);
  font-size: 13px;
  letter-spacing: 1px;
  cursor: pointer;
  transition: color 0.18s ease, background 0.18s ease;
}

.nav-item:hover {
  color: var(--rc-text);
}

.nav-item.is-active {
  background: rgba(124, 189, 75, 0.16);
  color: var(--el-color-primary);
}

.app-main {
  flex: 1;
  width: 100%;
  max-width: 980px;
  margin: 0 auto;
  padding: 28px 20px 64px;
}

.app-main-wide {
  max-width: 1280px;
}
</style>
