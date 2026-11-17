<script setup>
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { useRoute } from 'vue-router'
import VoxelPreview from '../components/VoxelPreview.vue'
import {
  ApiError,
  deployModel,
  generateModel,
  getServer,
  getTask,
  listHistory,
  listServers,
  redo,
  rollback,
  sendChat,
} from '../api/platform'

const props = defineProps({
  serverId: { type: String, default: '' },
})

const route = useRoute()
const preferredServerId = props.serverId || (typeof route.query.serverId === 'string' ? route.query.serverId : '')
const servers = ref([])
const selectedServerId = ref('')
const selectedServer = computed(() =>
  servers.value.find((server) => server.serverId === selectedServerId.value) || null,
)
const players = computed(() => (selectedServer.value?.players || []))

const selectedPoint = ref(null)
const playerMode = ref('online')
const selectedPlayerUuid = ref('')
const offlinePlayerName = ref('')
const offlinePlayerUuid = ref('')
const rotationChoice = ref('0')
const prompt = ref('')
const chatInput = ref('')
const chatMessages = ref([])
const chatLoading = ref(false)
const generating = ref(false)
const deploying = ref(false)
const model = ref(null)
const task = ref(null)
const buildError = ref('')
const history = ref([])
const historyLoading = ref(false)
const sessionSeed = ref(`session-${Date.now()}`)
const iframeRef = ref(null)
const mapLoading = ref(false)

const selectedOnlinePlayer = computed(() =>
  players.value.find((player) => player.uuid === selectedPlayerUuid.value) || null,
)
const effectivePlayer = computed(() => {
  if (playerMode.value === 'online') {
    const player = selectedOnlinePlayer.value
    return player ? { uuid: player.uuid, name: player.name, online: true, x: player.x, z: player.z } : null
  }
  const name = offlinePlayerName.value.trim()
  if (!name) {
    return null
  }
  return { uuid: offlinePlayerUuid.value.trim() || null, name, online: false }
})
const playerKey = computed(() => effectivePlayer.value?.uuid || effectivePlayer.value?.name || 'none')
const sessionId = computed(() => `${selectedServerId.value || 'none'}:${playerKey.value}:${sessionSeed.value}`)
const mapUrl = computed(() => {
  const base = selectedServer.value?.blueMapUrl || ''
  if (!base) {
    return ''
  }
  try {
    const url = new URL(base, window.location.href)
    url.searchParams.set('mapId', selectedServer.value?.mapId || 'world')
    return url.toString()
  } catch (e) {
    return base
  }
})
const mapOrigin = computed(() => {
  try {
    return mapUrl.value ? new URL(mapUrl.value, window.location.href).origin : ''
  } catch (e) {
    return ''
  }
})
const canGenerate = computed(() =>
  Boolean(selectedServer.value && selectedPoint.value && effectivePlayer.value && prompt.value.trim() && !generating.value),
)
const canDeploy = computed(() =>
  Boolean(model.value && selectedPoint.value && effectivePlayer.value && selectedServer.value && !deploying.value),
)
const clientRotation = computed(() => {
  if (rotationChoice.value !== 'player') {
    return Number(rotationChoice.value) || 0
  }
  const player = selectedOnlinePlayer.value
  if (!player || !selectedPoint.value) {
    return 0
  }
  const dx = player.x - selectedPoint.value.x
  const dz = player.z - selectedPoint.value.z
  const radians = Math.atan2(dx, dz)
  const quarter = Math.round(radians / (Math.PI / 2))
  return ((quarter * 90) % 360 + 360) % 360
})
const previewVoxels = computed(() => (model.value?.blocks || []).map((block) => (
  Array.isArray(block)
    ? { id: block[0], x: block[1], y: block[2], z: block[3] }
    : { id: block.id, x: block.x, y: block.y, z: block.z }
)))

function errorMessage(error) {
  return error instanceof ApiError ? error.message : '请求失败'
}

async function loadServer(serverId) {
  try {
    const server = await getServer(serverId)
    servers.value = [server]
    selectedServerId.value = server.serverId
  } catch (e) {
    ElMessage.error(errorMessage(e))
  }
}

async function loadServers() {
  try {
    servers.value = await listServers()
    if (selectedServerId.value && !servers.value.some((s) => s.serverId === selectedServerId.value)) {
      selectedServerId.value = ''
    }
    if (preferredServerId && servers.value.some((server) => server.serverId === preferredServerId)) {
      selectedServerId.value = preferredServerId
    } else if (!selectedServerId.value && servers.value.length > 0) {
      selectedServerId.value = servers.value[0].serverId
    }
  } catch (e) {
    ElMessage.error(errorMessage(e))
  }
}

function selectMapPoint(payload) {
  if (!payload || !Number.isFinite(payload.x) || !Number.isFinite(payload.z)) return
  selectedPoint.value = { x: Math.floor(payload.x), z: Math.floor(payload.z), rawY: payload.rawY }
  buildError.value = ''
  focusMap()
}

function focusMap() {
  const target = iframeRef.value?.contentWindow
  if (!target || !selectedPoint.value || !mapOrigin.value) return
  target.postMessage({
    source: 'structengine-platform',
    type: 'focus',
    payload: { x: selectedPoint.value.x, z: selectedPoint.value.z, y: selectedPoint.value.rawY || 0, marker: true },
  }, mapOrigin.value)
}

function handleWindowMessage(event) {
  if (!event.data || event.data.source !== 'structengine-bluemap') return
  if (mapOrigin.value && event.origin !== mapOrigin.value) return
  if (event.data.type === 'pick') {
    selectMapPoint(event.data.payload)
  }
}

function resetSelection() {
  selectedPoint.value = null
  selectedPlayerUuid.value = ''
  model.value = null
  task.value = null
  buildError.value = ''
  history.value = []
  chatMessages.value = []
  sessionSeed.value = `session-${Date.now()}`
}

watch(selectedServerId, () => {
  resetSelection()
  mapLoading.value = Boolean(mapUrl.value)
  if (effectivePlayer.value) loadHistory()
})

watch(selectedPlayerUuid, () => {
  if (selectedPlayerUuid.value) loadHistory()
})

async function sendChatMessage() {
  const text = chatInput.value.trim()
  if (!text || chatLoading.value) return
  chatMessages.value.push({ role: 'user', content: text })
  chatInput.value = ''
  chatLoading.value = true
  try {
    const reply = await sendChat(selectedServerId.value, { sessionId: sessionId.value, message: text })
    chatMessages.value.push({ role: 'assistant', content: reply.reply })
  } catch (e) {
    chatMessages.value.push({ role: 'assistant', content: `错误：${errorMessage(e)}` })
  } finally {
    chatLoading.value = false
  }
}

async function handleGenerate() {
  if (!canGenerate.value) {
    ElMessage.warning('请先完成地图选点、玩家选择和模型描述')
    return
  }
  generating.value = true
  buildError.value = ''
  model.value = null
  task.value = null
  try {
    const player = effectivePlayer.value
    const draft = await generateModel(selectedServerId.value, {
      prompt: prompt.value.trim(),
    })
    model.value = draft
    chatMessages.value.push({
      role: 'assistant',
      content: `模型已生成：${draft.blockCount} 个方块，包围盒 ${draft.maxX + 1}×${draft.maxY + 1}×${draft.maxZ + 1}。可预览后部署。`,
    })
    ElMessage.success('模型生成成功')
  } catch (e) {
    buildError.value = errorMessage(e)
    ElMessage.error(buildError.value)
  } finally {
    generating.value = false
  }
}

async function handleDeploy() {
  if (!canDeploy.value) {
    ElMessage.warning('请先生成模型并完成选点/玩家')
    return
  }
  deploying.value = true
  buildError.value = ''
  try {
    const player = effectivePlayer.value
    task.value = await deployModel(selectedServerId.value, {
      playerUuid: player.uuid,
      playerName: player.name,
      modelId: model.value.modelId,
      centerX: selectedPoint.value.x,
      centerZ: selectedPoint.value.z,
      rotation: clientRotation.value,
    })
    ElMessage.success('部署指令已下发')
    pollTask(task.value.id)
    await loadHistory()
  } catch (e) {
    buildError.value = errorMessage(e)
    ElMessage.error(buildError.value)
  } finally {
    deploying.value = false
  }
}

let pollTimer = null
function pollTask(taskId) {
  clearTimeout(pollTimer)
  pollTimer = setTimeout(async () => {
    try {
      const current = await getTask(selectedServerId.value, taskId)
      task.value = current
      if (!['COMPLETED', 'FAILED', 'ABORTED'].includes(current.status)) {
        pollTask(taskId)
      } else if (current.status === 'COMPLETED') {
        ElMessage.success('构建完成')
      }
    } catch (e) {
      // 保留当前状态，避免网络抖动打断
    }
  }, 1200)
}

async function loadHistory() {
  const player = effectivePlayer.value
  if (!selectedServerId.value || !player) {
    history.value = []
    return
  }
  historyLoading.value = true
  try {
    history.value = await listHistory(selectedServerId.value, player.uuid || '', player.name)
  } catch (e) {
    history.value = []
    if (e.code !== 401) ElMessage.error(errorMessage(e))
  } finally {
    historyLoading.value = false
  }
}

async function handleRollback(entry) {
  const player = effectivePlayer.value
  if (!player) return
  try {
    await rollback(selectedServerId.value, entry.historyId, {
      playerUuid: player.uuid || '', playerName: player.name,
    })
    ElMessage.success('回滚已进入队列')
    setTimeout(loadHistory, 800)
  } catch (e) {
    ElMessage.error(errorMessage(e))
  }
}

async function handleRedo(entry) {
  const player = effectivePlayer.value
  if (!player) return
  try {
    await redo(selectedServerId.value, entry.historyId, {
      playerUuid: player.uuid || '', playerName: player.name,
    })
    ElMessage.success('恢复已进入队列')
    setTimeout(loadHistory, 800)
  } catch (e) {
    ElMessage.error(errorMessage(e))
  }
}

function formatTime(value) {
  if (!value) return '-'
  return new Date(value).toLocaleString()
}

watch(() => props.serverId, (serverId) => {
  if (serverId) {
    loadServer(serverId)
  }
})

onMounted(() => {
  window.addEventListener('message', handleWindowMessage)
  if (props.serverId) {
    loadServer(props.serverId)
  } else {
    loadServers()
  }
})

onBeforeUnmount(() => {
  window.removeEventListener('message', handleWindowMessage)
  clearTimeout(pollTimer)
})
</script>

<template>
  <section class="studio">
      <header class="studio-header">
        <div>
          <h2>AI 地图建造工作台</h2>
          <p>选择游戏服 → BlueMap 选点 → 选择玩家 → AI 生成 → 预览 → 部署</p>
        </div>
        <div class="header-actions">
          <span v-if="serverId" class="server-badge">{{ selectedServer?.serverName || serverId }}</span>
          <template v-else>
            <el-select v-model="selectedServerId" placeholder="选择游戏服" style="width: 210px">
              <el-option v-for="server in servers" :key="server.serverId" :label="`${server.serverName} (${server.serverId})`" :value="server.serverId" />
            </el-select>
            <el-button @click="loadServers">刷新</el-button>
          </template>
        </div>
      </header>

      <el-alert
        v-if="!selectedServer"
        type="warning"
        show-icon
        :closable="false"
        title="没有已连接的游戏服"
        description="请确认 Minecraft Server 已启动并连接到 Platform。"
      />

      <div v-else class="studio-grid">
        <div class="map-panel">
          <div class="panel-head">
            <span>BlueMap · {{ selectedServer.serverName }}</span>
            <span class="coord">{{ selectedPoint ? `X ${selectedPoint.x}  Z ${selectedPoint.z}` : '点击地图选择建筑中心' }}</span>
          </div>
          <div class="map-frame-wrap">
            <iframe
              v-if="mapUrl"
              ref="iframeRef"
              :src="mapUrl"
              class="map-frame"
              allow="fullscreen"
              @load="mapLoading = false"
            ></iframe>
            <div v-else class="map-empty">该服务器尚未上报 BlueMap 地址</div>
          </div>
          <div class="map-footer">
            <span>BlueMap: {{ mapUrl || '-' }}</span>
            <span>旋转：{{ rotationChoice === 'player' ? `朝向玩家（${clientRotation}°）` : `${clientRotation}°` }}</span>
          </div>
        </div>

        <aside class="side-panel">
          <section class="side-card">
            <h3>玩家</h3>
            <el-radio-group v-model="playerMode" size="small">
              <el-radio-button label="online">在线玩家</el-radio-button>
              <el-radio-button label="offline">离线玩家</el-radio-button>
            </el-radio-group>
            <el-select v-if="playerMode === 'online'" v-model="selectedPlayerUuid" placeholder="选择在线玩家" class="full" filterable>
              <el-option v-for="player in players" :key="player.uuid" :label="`${player.name} · ${player.world}`" :value="player.uuid" />
            </el-select>
            <div v-else class="offline-fields">
              <el-input v-model="offlinePlayerName" placeholder="离线玩家名，例如 HelloKitty" />
              <el-input v-model="offlinePlayerUuid" placeholder="UUID（可选，建议填写）" />
            </div>
            <p class="hint">当前：{{ effectivePlayer ? `${effectivePlayer.name}${effectivePlayer.online ? ' · 在线' : ' · 离线'}` : '未选择' }}</p>
          </section>

          <section class="side-card">
            <h3>模型描述与旋转</h3>
            <el-input v-model="prompt" type="textarea" :rows="3" maxlength="1000" show-word-limit placeholder="例如：一座带红石屋顶的中世纪小木屋，正面朝南..." />
            <div class="rotation-row">
              <span>朝向</span>
              <el-select v-model="rotationChoice" style="width: 130px">
                <el-option label="南 (0°)" value="0" />
                <el-option label="东 (90°)" value="90" />
                <el-option label="北 (180°)" value="180" />
                <el-option label="西 (270°)" value="270" />
                <el-option label="朝向玩家" value="player" />
              </el-select>
            </div>
            <el-button type="primary" :loading="generating" :disabled="!canGenerate" class="full" @click="handleGenerate">
              {{ generating ? 'AI 生成中...' : '生成模型' }}
            </el-button>
          </section>

          <section class="side-card chat-card">
            <h3>AI 对话</h3>
            <div class="chat-list">
              <div v-if="chatMessages.length === 0" class="chat-empty">先描述你想要的建筑，再生成模型。</div>
              <div v-for="(message, index) in chatMessages" :key="index" class="chat-message" :class="message.role">
                <span class="chat-role">{{ message.role === 'user' ? '你' : 'AI' }}</span>
                <span>{{ message.content }}</span>
              </div>
            </div>
            <div class="chat-input">
              <el-input v-model="chatInput" placeholder="和 AI 讨论建筑..." @keyup.enter="sendChatMessage" />
              <el-button :loading="chatLoading" @click="sendChatMessage">发送</el-button>
            </div>
          </section>

          <section v-if="model" class="side-card">
            <h3>模型预览</h3>
            <p class="hint">{{ model.blockCount }} 个方块 · {{ model.maxX + 1 }} × {{ model.maxY + 1 }} × {{ model.maxZ + 1 }}</p>
            <VoxelPreview :voxels="previewVoxels" />
            <el-button type="success" :loading="deploying" :disabled="!canDeploy" class="full" @click="handleDeploy">
              部署到 {{ selectedServer.serverName }}
            </el-button>
            <p v-if="task" class="task-state">任务 {{ task.id }}：{{ task.status }} {{ task.placed }}/{{ task.total }}</p>
          </section>

          <el-alert v-if="buildError" :title="buildError" type="error" show-icon :closable="false" />

          <section class="side-card">
            <div class="history-head">
              <h3>构建历史</h3>
              <el-button size="small" :loading="historyLoading" @click="loadHistory">刷新</el-button>
            </div>
            <div v-if="history.length === 0" class="hint">暂无历史（历史在 Minecraft Server 重启后清空）</div>
            <div v-for="entry in history" :key="entry.historyId" class="history-item">
              <div>
                <strong>{{ entry.modelName || entry.taskId || entry.historyId }}</strong>
                <p>{{ formatTime(entry.createdAt) }} · {{ entry.blockCount }} 方块</p>
              </div>
              <div class="history-actions">
                <el-button v-if="entry.canRollback" size="small" type="warning" @click="handleRollback(entry)">回滚</el-button>
                <el-button v-if="entry.canRedo" size="small" type="success" @click="handleRedo(entry)">恢复</el-button>
              </div>
            </div>
          </section>
        </aside>
      </div>
  </section>
</template>

<style scoped>
.studio { display: flex; flex-direction: column; gap: 16px; }
.server-badge { padding: 6px 12px; border: 1px solid var(--rc-panel-border); border-radius: 999px; color: var(--el-color-primary); font-size: 12px; }
.studio-header { display: flex; align-items: flex-start; justify-content: space-between; gap: 16px; flex-wrap: wrap; }
.studio-header h2 { margin: 0; font-size: 22px; }
.studio-header p { margin: 8px 0 0; color: var(--rc-text-dim); font-size: 13px; }
.header-actions { display: flex; gap: 8px; align-items: center; flex-wrap: wrap; }
.studio-grid { display: grid; grid-template-columns: minmax(0, 1fr) 390px; gap: 16px; align-items: start; }
.map-panel { border: 1px solid var(--rc-panel-border); border-radius: 14px; overflow: hidden; background: var(--rc-panel); }
.panel-head, .map-footer { padding: 10px 14px; display: flex; justify-content: space-between; gap: 12px; font-size: 12px; color: var(--rc-text-dim); background: rgba(0,0,0,.18); }
.panel-head .coord { color: var(--rc-accent); font-family: Consolas, monospace; }
.map-frame-wrap { position: relative; min-height: 650px; background: #080c09; }
.map-frame { position: absolute; inset: 0; width: 100%; height: 100%; border: 0; }
.map-empty { position: absolute; inset: 0; display: flex; align-items: center; justify-content: center; color: var(--rc-text-dim); font-size: 13px; }
.side-panel { display: flex; flex-direction: column; gap: 12px; min-width: 0; }
.side-card { padding: 14px; border: 1px solid var(--rc-panel-border); border-radius: 12px; background: var(--rc-panel); display: flex; flex-direction: column; gap: 10px; }
.side-card h3 { margin: 0; font-size: 14px; }
.full { width: 100%; }
.offline-fields { display: flex; flex-direction: column; gap: 8px; }
.hint { margin: 0; color: var(--rc-text-dim); font-size: 12px; line-height: 1.5; }
.rotation-row { display: flex; align-items: center; justify-content: space-between; gap: 10px; color: var(--rc-text-dim); font-size: 13px; }
.chat-card { min-height: 240px; }
.chat-list { max-height: 240px; overflow-y: auto; display: flex; flex-direction: column; gap: 8px; }
.chat-empty { color: var(--rc-text-dim); font-size: 12px; }
.chat-message { padding: 8px 10px; border-radius: 8px; font-size: 12px; line-height: 1.6; background: #121a13; display: flex; flex-direction: column; gap: 3px; }
.chat-message.user { border-left: 2px solid var(--el-color-primary); }
.chat-message.assistant { border-left: 2px solid var(--rc-accent); }
.chat-role { font-size: 10px; color: var(--rc-text-dim); text-transform: uppercase; }
.chat-input { display: flex; gap: 6px; }
.history-head { display: flex; justify-content: space-between; align-items: center; }
.history-item { display: flex; justify-content: space-between; gap: 8px; border-top: 1px solid var(--rc-panel-border); padding-top: 8px; }
.history-item p { margin: 4px 0 0; font-size: 11px; color: var(--rc-text-dim); }
.history-actions { display: flex; gap: 4px; align-items: center; }
.task-state { margin: 0; color: var(--rc-accent); font-size: 12px; word-break: break-all; }
@media (max-width: 1100px) { .studio-grid { grid-template-columns: minmax(0, 1fr); } .map-frame-wrap { min-height: 500px; } }
</style>
