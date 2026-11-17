<script setup>
import { computed, defineAsyncComponent, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
const BuildStudioPage = defineAsyncComponent(() => import('./BuildStudioPage.vue'))
const GeneratePage = defineAsyncComponent(() => import('./GeneratePage.vue'))
const ServerModelsPanel = defineAsyncComponent(() => import('../components/ServerModelsPanel.vue'))
const ServerTasksPanel = defineAsyncComponent(() => import('../components/ServerTasksPanel.vue'))
import {
  disableServer,
  enableServer,
  getServer,
  rotateServerSecret,
  updateServer,
} from '../api/platform'
import { copyText } from '../utils/clipboard'

const route = useRoute()
const router = useRouter()
const serverId = computed(() => String(route.params.serverId || ''))
const server = ref(null)
const loading = ref(false)
const activeTab = ref(typeof route.query.tab === 'string' ? route.query.tab : 'overview')

const secretVisible = ref(false)
const secretInfo = reactive({ serverName: '', secret: '', wsUrl: '' })

const statusMeta = {
  PENDING: { label: '待注册', type: 'warning' },
  ONLINE: { label: '在线', type: 'success' },
  OFFLINE: { label: '离线', type: 'info' },
  DISABLED: { label: '已禁用', type: 'danger' },
}

const status = computed(() => statusMeta[server.value?.status] || { label: '-', type: 'info' })

function formatTime(value) {
  return value ? new Date(value).toLocaleString() : '-'
}

async function load() {
  loading.value = true
  try {
    server.value = await getServer(serverId.value)
  } catch (e) {
    ElMessage.error(e.message || '加载服务器失败')
    await router.replace('/servers')
  } finally {
    loading.value = false
  }
}

async function rename() {
  if (!server.value) return
  let value
  try {
    const result = await ElMessageBox.prompt('请输入新的服务器名称', '重命名服务器', {
      inputValue: server.value.serverName,
      inputValidator: (input) => Boolean(input && input.trim() && input.trim().length <= 64)
        || '名称需为 1~64 个字符',
    })
    value = result.value
  } catch (e) {
    return
  }
  try {
    await updateServer(serverId.value, { name: value.trim() })
    ElMessage.success('已重命名')
    await load()
  } catch (e) {
    ElMessage.error(e.message || '重命名失败')
  }
}

async function rotate() {
  if (!server.value) return
  try {
    await ElMessageBox.confirm(
      '轮换后旧 secret 立即失效，插件需要更换新 secret 才能重连。确定轮换吗？',
      '轮换 secret',
      { type: 'warning' },
    )
  } catch (e) {
    return
  }
  try {
    const data = await rotateServerSecret(serverId.value)
    secretInfo.serverName = server.value.serverName
    secretInfo.secret = data.secret
    secretInfo.wsUrl = data.wsUrl
    secretVisible.value = true
    await load()
  } catch (e) {
    ElMessage.error(e.message || '轮换失败')
  }
}

async function toggleStatus() {
  if (!server.value) return
  const disabling = server.value.status !== 'DISABLED'
  try {
    await ElMessageBox.confirm(
      disabling ? '禁用后插件会被断开，确定禁用吗？' : '确定启用该服务器吗？',
      disabling ? '禁用服务器' : '启用服务器',
      { type: 'warning' },
    )
  } catch (e) {
    return
  }
  try {
    if (disabling) {
      await disableServer(serverId.value)
    } else {
      await enableServer(serverId.value)
    }
    ElMessage.success(disabling ? '已禁用' : '已启用')
    await load()
  } catch (e) {
    ElMessage.error(e.message || '操作失败')
  }
}

async function copySecret() {
  const res = await copyText(secretInfo.secret)
  if (res.ok) {
    ElMessage.success('secret 已复制')
  } else {
    ElMessage.warning('复制失败，请手动选择')
  }
}

async function copyConfig() {
  const text = `STRUCTENGINE_PLATFORM_URL=${secretInfo.wsUrl}\nSTRUCTENGINE_SERVER_SECRET=${secretInfo.secret}`
  const res = await copyText(text)
  if (res.ok) {
    ElMessage.success('插件配置已复制')
  } else {
    ElMessage.warning('复制失败，请手动选择')
  }
}

watch(() => route.params.serverId, (value, previous) => {
  if (value && value !== previous) {
    load()
  }
})

onMounted(load)
</script>

<template>
  <section v-loading="loading" class="server-detail">
    <div v-if="server" class="detail-head">
      <div>
        <button type="button" class="back-link" @click="router.push('/servers')">← 返回服务器列表</button>
        <h2>{{ server.serverName }}</h2>
        <p class="server-id">{{ server.serverId }}</p>
      </div>
      <div class="head-actions">
        <el-tag :type="status.type">{{ status.label }}</el-tag>
        <el-button size="small" @click="rename">重命名</el-button>
        <el-button size="small" @click="rotate">轮换 secret</el-button>
        <el-button size="small" @click="toggleStatus">
          {{ server.status === 'DISABLED' ? '启用' : '禁用' }}
        </el-button>
      </div>
    </div>

    <el-alert
      v-if="server && server.status === 'PENDING'"
      type="warning"
      show-icon
      :closable="false"
      title="服务器待注册"
      description="请把服务器 secret 配置到该服的 StructEngine 插件；插件连接并上报 BlueMap 后状态会变为在线。"
    />

    <el-tabs v-if="server" v-model="activeTab" class="detail-tabs">
      <el-tab-pane label="概览" name="overview">
        <div class="overview-grid">
          <section class="info-card">
            <h3>连接信息</h3>
            <dl>
              <dt>状态</dt>
              <dd>{{ status.label }}{{ server.connected ? ' · WS 已连接' : '' }}</dd>
              <dt>BlueMap</dt>
              <dd>
                <a v-if="server.blueMapUrl" :href="server.blueMapUrl" target="_blank" rel="noopener">
                  {{ server.blueMapUrl }}
                </a>
                <span v-else class="muted">尚未上报</span>
              </dd>
              <dt>mapId</dt>
              <dd>{{ server.mapId || 'world' }}</dd>
              <dt>最后在线</dt>
              <dd>{{ formatTime(server.lastSeenAt) }}</dd>
              <dt>注册时间</dt>
              <dd>{{ formatTime(server.registeredAt) }}</dd>
            </dl>
          </section>

          <section class="info-card">
            <h3>在线玩家（{{ server.playerCount || 0 }}）</h3>
            <el-table v-if="server.players && server.players.length" :data="server.players" size="small">
              <el-table-column prop="name" label="玩家" />
              <el-table-column prop="world" label="世界" />
              <el-table-column label="坐标">
                <template #default="{ row }">
                  {{ row.x }}, {{ row.y }}, {{ row.z }}
                </template>
              </el-table-column>
            </el-table>
            <p v-else class="muted">当前没有在线玩家。</p>
          </section>
        </div>
      </el-tab-pane>

      <el-tab-pane label="AI 地图建造" name="build" lazy>
        <BuildStudioPage :server-id="serverId" />
      </el-tab-pane>

      <el-tab-pane label="参考图生成" name="studio" lazy>
        <GeneratePage :server-id="serverId" />
      </el-tab-pane>

      <el-tab-pane label="模型产物" name="models" lazy>
        <ServerModelsPanel :server-id="serverId" />
      </el-tab-pane>

      <el-tab-pane label="构建任务" name="tasks" lazy>
        <ServerTasksPanel :server-id="serverId" />
      </el-tab-pane>
    </el-tabs>

    <el-dialog v-model="secretVisible" title="服务器 secret" width="620px" @closed="secretInfo.secret = ''">
      <div class="secret-body">
        <el-alert
          type="warning"
          show-icon
          :closable="false"
          title="secret 只显示这一次"
          description="关闭弹窗后无法再次查看；如果丢失只能轮换 secret。"
        />
        <p class="secret-server">{{ secretInfo.serverName }}</p>
        <el-input :model-value="secretInfo.secret" readonly>
          <template #append>
            <el-button @click="copySecret">复制</el-button>
          </template>
        </el-input>
        <div class="config-box">
          <div class="config-title">插件配置</div>
          <pre>STRUCTENGINE_PLATFORM_URL={{ secretInfo.wsUrl }}
STRUCTENGINE_SERVER_SECRET={{ secretInfo.secret }}</pre>
          <el-button size="small" @click="copyConfig">复制配置</el-button>
        </div>
      </div>
      <template #footer>
        <el-button type="primary" @click="secretVisible = false">我已保存</el-button>
      </template>
    </el-dialog>
  </section>
</template>

<style scoped>
.server-detail {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.detail-head {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 16px;
  flex-wrap: wrap;
}

.back-link {
  padding: 0;
  border: 0;
  background: transparent;
  color: var(--rc-text-dim);
  font-size: 12px;
  cursor: pointer;
}

.detail-head h2 {
  margin: 8px 0 0;
  font-size: 26px;
}

.server-id {
  margin: 6px 0 0;
  color: var(--rc-text-dim);
  font-family: Consolas, monospace;
  font-size: 12px;
}

.head-actions {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.detail-tabs :deep(.el-tabs__content) {
  padding-top: 8px;
}

.overview-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(320px, 1fr));
  gap: 14px;
}

.info-card {
  padding: 18px;
  border: 1px solid var(--rc-panel-border);
  border-radius: 12px;
  background: var(--rc-panel);
}

.info-card h3 {
  margin: 0 0 12px;
  font-size: 15px;
}

.info-card dl {
  display: grid;
  grid-template-columns: 90px 1fr;
  gap: 8px 12px;
  margin: 0;
  font-size: 13px;
}

.info-card dt {
  color: var(--rc-text-dim);
}

.info-card dd {
  margin: 0;
  word-break: break-all;
}

.info-card a {
  color: var(--el-color-primary);
}

.muted {
  color: var(--rc-text-dim);
}

.secret-body {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.secret-server {
  margin: 0;
  font-weight: 600;
}

.config-box {
  padding: 12px;
  border: 1px solid var(--rc-panel-border);
  border-radius: 8px;
  background: #101610;
}

.config-title {
  margin-bottom: 8px;
  color: var(--rc-text-dim);
  font-size: 12px;
}

.config-box pre {
  margin: 0 0 10px;
  color: var(--rc-accent);
  font-family: Consolas, monospace;
  font-size: 12px;
  white-space: pre-wrap;
  word-break: break-all;
}
</style>
