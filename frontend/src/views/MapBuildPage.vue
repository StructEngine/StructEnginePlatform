<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, ref, shallowRef } from 'vue'
import L from 'leaflet'
import 'leaflet/dist/leaflet.css'
import { ElMessage } from 'element-plus'
import { createBuild, fetchGameServers, MapBuildError } from '../api/mapBuild'
import { copyText } from '../utils/clipboard'
import {
  createProjection,
  DEFAULT_EXTRA_ZOOM,
  DEFAULT_MAX_ZOOM,
  formatBlockCoordinates,
  normalizeBaseUrl,
  parseSquaremapSettings,
  settingsUrl,
  tileUrlTemplate,
} from '../utils/squaremap'

/**
 * 环境准备（详见 README「地图选点」章节）：
 * 1. 游戏服安装 Squaremap，并开放 Web Server 端口；
 * 2. 游戏服安装 StructEngine 插件，并配置 STRUCTENGINE_WS_URL 指向本 Platform；
 * 3. 前端 .env 配置 VITE_SQUAREMAP_URL（可配合 VITE_SQUAREMAP_PROXY_TARGET 走 Vite 代理）
 *    与 VITE_SQUAREMAP_WORLD（默认 world）。
 */
const mapBaseUrl = normalizeBaseUrl(import.meta.env.VITE_SQUAREMAP_URL)
const worldName = (import.meta.env.VITE_SQUAREMAP_WORLD || 'world').trim() || 'world'
const isConfigured = Boolean(mapBaseUrl)

const mapEl = ref(null)
const mapInstance = shallowRef(null)
const markerInstance = shallowRef(null)
const projection = shallowRef(createProjection(DEFAULT_MAX_ZOOM))

const mapLoading = ref(isConfigured)
const mapError = ref('')
const settingsWarning = ref('')
const selectedPoint = ref(null)
const selectedLabel = computed(() =>
  selectedPoint.value ? `X: ${selectedPoint.value.x}, Z: ${selectedPoint.value.z}` : '尚未选点',
)

const playerName = ref(localStorage.getItem('structengine.playerName') || '')
const prompt = ref('')
const servers = ref([])
const serverId = ref('')
const serversLoading = ref(false)
const serverError = ref('')

const submitting = ref(false)
const result = ref(null)
const buildError = ref('')
const copied = ref(false)

const canSubmit = computed(
  () => isConfigured && !submitting.value && selectedPoint.value && playerName.value.trim() && prompt.value.trim(),
)

async function loadWorldSettings() {
  try {
    const response = await fetch(settingsUrl(mapBaseUrl, worldName), { cache: 'no-store' })
    if (!response.ok) {
      throw new Error(`HTTP ${response.status}`)
    }
    return parseSquaremapSettings(await response.json())
  } catch (e) {
    settingsWarning.value = '无法读取 Squaremap 世界配置，已回退到默认缩放与出生点。'
    return parseSquaremapSettings(null, {
      maxZoom: DEFAULT_MAX_ZOOM,
      extraZoom: DEFAULT_EXTRA_ZOOM,
      defZoom: 0,
    })
  }
}

async function initMap() {
  if (!isConfigured || !mapEl.value) {
    return
  }
  mapLoading.value = true
  mapError.value = ''
  try {
    const settings = await loadWorldSettings()
    if (!mapEl.value) {
      return
    }

    projection.value = createProjection(settings.maxZoom)
    const center = projection.value.toLatLng(settings.spawn.x, settings.spawn.z)
    const maxMapZoom = settings.maxZoom + settings.extraZoom

    const map = L.map(mapEl.value, {
      crs: L.CRS.Simple,
      center,
      zoom: settings.defZoom,
      minZoom: 0,
      maxZoom: maxMapZoom,
      zoomControl: true,
      attributionControl: false,
      preferCanvas: true,
      noWrap: true,
    })

    L.tileLayer(tileUrlTemplate(mapBaseUrl, worldName), {
      tileSize: 512,
      minNativeZoom: 0,
      maxNativeZoom: settings.maxZoom,
      noWrap: true,
      keepBuffer: 4,
      className: 'squaremap-tiles',
      attribution: 'Squaremap',
    }).addTo(map)

    map.on('click', handleMapClick)
    mapInstance.value = map

    await nextTick()
    map.invalidateSize()
  } catch (e) {
    mapError.value = e instanceof Error ? e.message : '地图初始化失败'
  } finally {
    mapLoading.value = false
  }
}

function handleMapClick(event) {
  const point = projection.value.toBlock(event.latlng)
  selectedPoint.value = point
  buildError.value = ''
  result.value = null
  placeMarker(point)
}

function placeMarker(point) {
  const map = mapInstance.value
  if (!map) {
    return
  }
  // 取方块中心，避免 Marker 压在四个方块的公共角上。
  const latlng = projection.value.toLatLng(point.x + 0.5, point.z + 0.5)
  if (markerInstance.value) {
    markerInstance.value.setLatLng(latlng)
  } else {
    markerInstance.value = L.marker(latlng, {
      keyboard: false,
      icon: L.divIcon({
        className: 'se-pick-marker',
        html: '<span class="se-pick-marker-dot"></span>',
        iconSize: [28, 40],
        iconAnchor: [14, 38],
      }),
    }).addTo(map)
  }
  markerInstance.value.bindPopup(
    `<strong>构建原点</strong><br/>X: ${point.x}<br/>Z: ${point.z}`,
  )
}

async function loadServers() {
  serversLoading.value = true
  serverError.value = ''
  try {
    const list = await fetchGameServers()
    servers.value = Array.isArray(list) ? list : []
    if (!serverId.value && servers.value.length > 0) {
      serverId.value = servers.value[0].serverId
    }
    if (serverId.value && !servers.value.some((item) => item.serverId === serverId.value)) {
      serverId.value = servers.value.length > 0 ? servers.value[0].serverId : ''
    }
  } catch (e) {
    serverError.value = e instanceof MapBuildError ? e.message : '游戏服列表加载失败'
    servers.value = []
  } finally {
    serversLoading.value = false
  }
}

async function handleSubmit() {
  if (submitting.value) {
    return
  }
  if (!selectedPoint.value) {
    ElMessage.warning('请先在地图上点击选择构建原点')
    return
  }
  const name = playerName.value.trim()
  if (!/^[A-Za-z0-9_]{3,16}$/.test(name)) {
    ElMessage.warning('请输入 3-16 位 Minecraft 玩家名（字母、数字、下划线）')
    return
  }
  const text = prompt.value.trim()
  if (!text) {
    ElMessage.warning('请输入建筑提示词')
    return
  }

  submitting.value = true
  buildError.value = ''
  result.value = null
  try {
    const data = await createBuild({
      serverId: serverId.value,
      x: selectedPoint.value.x,
      z: selectedPoint.value.z,
      playerName: name,
      prompt: text,
    })
    result.value = data
    localStorage.setItem('structengine.playerName', name)
    if (data && Number.isFinite(data.x) && Number.isFinite(data.z)) {
      selectedPoint.value = { x: data.x, z: data.z }
      placeMarker(selectedPoint.value)
      markerInstance.value.setPopupContent(
        `<strong>构建原点</strong><br/>${formatBlockCoordinates(data.x, data.y, data.z)}`,
      )
    }
    ElMessage.success('模型已生成并下发给游戏服务器')
  } catch (e) {
    buildError.value = e instanceof MapBuildError ? e.message : '构建请求失败'
    ElMessage.error(buildError.value)
  } finally {
    submitting.value = false
  }
}

async function handleCopyUrl() {
  if (!result.value || !result.value.modelUrl) {
    return
  }
  const res = await copyText(result.value.modelUrl)
  if (res.ok) {
    copied.value = true
    ElMessage.success('模型地址已复制')
    setTimeout(() => {
      copied.value = false
    }, 2000)
  }
}

onMounted(() => {
  initMap()
  loadServers()
})

onBeforeUnmount(() => {
  if (markerInstance.value) {
    markerInstance.value.remove()
    markerInstance.value = null
  }
  if (mapInstance.value) {
    mapInstance.value.remove()
    mapInstance.value = null
  }
})
</script>

<template>
  <section class="map-page">
    <header class="map-head">
      <div>
        <h2 class="map-title">地图选点构建</h2>
        <p class="map-subtitle">
          Squaremap 地图点击拾取二维坐标，Platform 通过 WebSocket 向游戏服查询地表高度后生成模型并下发构建。
        </p>
      </div>
      <div class="map-badges">
        <span class="badge">世界：{{ worldName }}</span>
        <span class="badge" :class="{ 'badge-ok': isConfigured, 'badge-warn': !isConfigured }">
          {{ isConfigured ? 'Squaremap 已配置' : 'Squaremap 未配置' }}
        </span>
      </div>
    </header>

    <el-alert
      v-if="!isConfigured"
      class="map-alert"
      type="warning"
      show-icon
      :closable="false"
      title="缺少 VITE_SQUAREMAP_URL"
      description="请在前端 .env 中配置 VITE_SQUAREMAP_URL（例如 /squaremap 或 http://map.example.com:8123），然后重新构建前端。"
    />

    <el-alert
      v-if="!serversLoading && servers.length === 0"
      class="map-alert"
      type="info"
      show-icon
      :closable="false"
      title="当前没有已连接的游戏服务器"
      description="请确认游戏服已安装 StructEngine 插件，并通过 STRUCTENGINE_WS_URL 成功连接到本 Platform。"
    />

    <div
      v-loading="submitting"
      element-loading-text="正在探测地表高度并生成模型..."
      element-loading-background="rgba(8, 12, 10, 0.78)"
      class="map-shell"
    >
      <div class="map-stage">
        <div ref="mapEl" class="map-canvas" :class="{ 'is-hidden': !isConfigured }"></div>

        <div v-if="!isConfigured" class="map-placeholder">
          <span class="placeholder-grid"></span>
          <p>配置 VITE_SQUAREMAP_URL 后在此加载 Squaremap 瓦片</p>
        </div>

        <div v-else-if="mapLoading" class="map-loading">正在加载 Squaremap 世界配置...</div>
        <div v-else-if="mapError" class="map-loading map-loading-error">{{ mapError }}</div>

        <div v-if="isConfigured" class="map-hint">
          点击地图选择构建原点 · 坐标向下取整 · Y 轴由游戏服实时回传
        </div>
      </div>

      <aside class="map-sidebar">
        <div class="panel point-panel">
          <p class="panel-label">构建原点（X / Z）</p>
          <p class="point-value">{{ selectedLabel }}</p>
          <p class="point-tip">瓦片放大时已强制最近邻采样，方块边缘保持锋利。</p>
        </div>

        <div class="panel form-panel">
          <label class="field">
            <span class="field-label">游戏服务器</span>
            <el-select
              v-model="serverId"
              class="field-control"
              :loading="serversLoading"
              placeholder="选择已连接的游戏服"
              no-data-text="暂无已连接的游戏服"
            >
              <el-option
                v-for="server in servers"
                :key="server.serverId"
                :label="server.serverId"
                :value="server.serverId"
              />
            </el-select>
          </label>

          <label class="field">
            <span class="field-label">玩家名 playerName</span>
            <el-input
              v-model="playerName"
              class="field-control"
              maxlength="16"
              placeholder="例如 Steve"
              clearable
            />
          </label>

          <label class="field">
            <span class="field-label">建筑提示词 prompt</span>
            <el-input
              v-model="prompt"
              class="field-control"
              type="textarea"
              :rows="4"
              maxlength="1000"
              show-word-limit
              placeholder="例如：一座带红色屋顶的中世纪小木屋"
            />
          </label>

          <el-button
            class="submit-button"
            type="primary"
            size="large"
            :loading="submitting"
            :disabled="!canSubmit"
            @click="handleSubmit"
          >
            {{ submitting ? '正在构建...' : '确定构建' }}
          </el-button>

          <el-alert
            v-if="serverError"
            :title="serverError"
            type="error"
            show-icon
            :closable="false"
          />
        </div>

        <div v-if="result" class="panel result-panel">
          <p class="panel-label">构建任务已下发</p>
          <p class="result-coords">
            X: {{ result.x }}, Y: {{ result.y }}, Z: {{ result.z }}
          </p>
          <p class="result-meta">玩家：{{ result.playerName }} · 任务：{{ result.taskId }}</p>
          <div class="result-url-row">
            <code class="result-url">{{ result.modelUrl }}</code>
            <el-button size="small" @click="handleCopyUrl">
              {{ copied ? '已复制' : '复制' }}
            </el-button>
          </div>
        </div>

        <el-alert
          v-if="buildError"
          :title="buildError"
          type="error"
          show-icon
          :closable="false"
        />

        <p v-if="settingsWarning" class="settings-warning">{{ settingsWarning }}</p>
      </aside>
    </div>
  </section>
</template>

<style scoped>
.map-page {
  display: flex;
  flex-direction: column;
  gap: 18px;
}

.map-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  flex-wrap: wrap;
}

.map-title {
  margin: 0;
  font-size: 22px;
  letter-spacing: 1px;
  color: var(--rc-text);
}

.map-subtitle {
  margin: 8px 0 0;
  max-width: 680px;
  font-size: 13px;
  line-height: 1.7;
  color: var(--rc-text-dim);
}

.map-badges {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}

.badge {
  padding: 5px 10px;
  border-radius: 999px;
  border: 1px solid var(--rc-panel-border);
  background: var(--rc-panel);
  font-size: 12px;
  color: var(--rc-text-dim);
}

.badge-ok {
  border-color: rgba(124, 189, 75, 0.55);
  color: var(--el-color-primary);
}

.badge-warn {
  border-color: rgba(230, 162, 60, 0.55);
  color: #e6a23c;
}

.map-alert {
  border-radius: 10px;
}

.map-shell {
  position: relative;
  display: grid;
  grid-template-columns: minmax(0, 1fr) 340px;
  gap: 16px;
  align-items: stretch;
}

.map-stage {
  position: relative;
  min-height: 600px;
  border: 1px solid var(--rc-panel-border);
  border-radius: 14px;
  overflow: hidden;
  background: #0a0f0b;
}

.map-canvas {
  position: absolute;
  inset: 0;
  z-index: 0;
  background: #0a0f0b;
}

.map-canvas.is-hidden {
  visibility: hidden;
}

/* 核心像素化规则：关闭浏览器对放大瓦片的双线性/三重平滑。 */
.map-canvas :deep(.leaflet-tile-pane img),
.map-canvas :deep(img.squaremap-tiles) {
  image-rendering: pixelated;
  image-rendering: crisp-edges;
  image-rendering: -moz-crisp-edges;
  -ms-interpolation-mode: nearest-neighbor;
  filter: none !important;
}

.map-canvas :deep(.leaflet-container) {
  background: #0a0f0b;
  font-family: inherit;
}

.map-canvas :deep(.leaflet-control-attribution) {
  background: rgba(10, 15, 11, 0.72);
  color: var(--rc-text-dim);
}

.map-canvas :deep(.leaflet-control-zoom a) {
  background: var(--rc-panel);
  border-color: var(--rc-panel-border);
  color: var(--rc-text);
}

.map-placeholder,
.map-loading {
  position: absolute;
  inset: 0;
  z-index: 2;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 12px;
  padding: 24px;
  text-align: center;
  color: var(--rc-text-dim);
  background: rgba(9, 14, 10, 0.92);
}

.map-loading-error {
  color: #f56c6c;
}

.placeholder-grid {
  width: 120px;
  height: 120px;
  border: 1px solid var(--rc-panel-border);
  background-image:
    linear-gradient(rgba(124, 189, 75, 0.22) 1px, transparent 1px),
    linear-gradient(90deg, rgba(124, 189, 75, 0.22) 1px, transparent 1px);
  background-size: 15px 15px;
}

.map-hint {
  position: absolute;
  left: 50%;
  bottom: 14px;
  z-index: 3;
  transform: translateX(-50%);
  padding: 7px 14px;
  border: 1px solid var(--rc-panel-border);
  border-radius: 999px;
  background: rgba(10, 15, 11, 0.82);
  font-size: 12px;
  color: var(--rc-text-dim);
  white-space: nowrap;
  pointer-events: none;
}

.map-sidebar {
  display: flex;
  flex-direction: column;
  gap: 12px;
  min-width: 0;
}

.panel {
  padding: 16px;
  border: 1px solid var(--rc-panel-border);
  border-radius: 14px;
  background: var(--rc-panel);
}

.panel-label {
  margin: 0 0 8px;
  font-size: 12px;
  letter-spacing: 1px;
  color: var(--rc-text-dim);
  text-transform: uppercase;
}

.point-value {
  margin: 0;
  font-family: 'Consolas', 'Menlo', monospace;
  font-size: 20px;
  color: var(--rc-accent);
  word-break: break-all;
}

.point-tip {
  margin: 8px 0 0;
  font-size: 12px;
  line-height: 1.6;
  color: var(--rc-text-dim);
}

.form-panel {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.field {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.field-label {
  font-size: 12px;
  color: var(--rc-text-dim);
}

.field-control {
  width: 100%;
}

.submit-button {
  width: 100%;
  margin-top: 2px;
}

.result-panel {
  border-color: rgba(127, 227, 208, 0.45);
}

.result-coords {
  margin: 0;
  font-family: 'Consolas', 'Menlo', monospace;
  font-size: 18px;
  color: var(--el-color-primary);
}

.result-meta {
  margin: 8px 0 12px;
  font-size: 12px;
  color: var(--rc-text-dim);
  word-break: break-all;
}

.result-url-row {
  display: flex;
  align-items: center;
  gap: 8px;
}

.result-url {
  flex: 1;
  min-width: 0;
  padding: 8px 10px;
  border: 1px solid var(--rc-panel-border);
  border-radius: 8px;
  background: #101610;
  font-size: 11px;
  color: var(--rc-accent);
  word-break: break-all;
}

.settings-warning {
  margin: 0;
  font-size: 12px;
  line-height: 1.6;
  color: #e6a23c;
}

:deep(.se-pick-marker) {
  background: transparent;
  border: 0;
}

:deep(.se-pick-marker-dot) {
  display: block;
  width: 18px;
  height: 18px;
  margin: 2px auto 0;
  border: 3px solid #0f1410;
  border-radius: 50%;
  background: var(--el-color-primary);
  box-shadow:
    0 0 0 2px var(--el-color-primary),
    0 0 18px rgba(124, 189, 75, 0.85);
}

:deep(.se-pick-marker-dot)::after {
  content: '';
  display: block;
  width: 2px;
  height: 14px;
  margin: 15px auto 0;
  background: linear-gradient(var(--el-color-primary), transparent);
}

@media (max-width: 920px) {
  .map-shell {
    grid-template-columns: minmax(0, 1fr);
  }

  .map-stage {
    min-height: 460px;
  }
}
</style>
