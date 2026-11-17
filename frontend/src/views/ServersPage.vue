<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  createServer,
  deleteServer,
  disableServer,
  enableServer,
  listServers,
  rotateServerSecret,
  updateServer,
} from '../api/platform'
import { useAuth } from '../stores/auth'
import { copyText } from '../utils/clipboard'

const auth = useAuth()
const servers = ref([])
const loading = ref(false)
const actionLoading = ref('')

const createVisible = ref(false)
const createName = ref('')
const createError = ref('')

const secretVisible = ref(false)
const secretInfo = reactive({ serverName: '', secret: '', wsUrl: '' })

const isAdmin = computed(() => auth.isAdmin.value)

const statusMeta = {
  PENDING: { label: '待注册', type: 'warning' },
  ONLINE: { label: '在线', type: 'success' },
  OFFLINE: { label: '离线', type: 'info' },
  DISABLED: { label: '已禁用', type: 'danger' },
}

function statusOf(server) {
  return statusMeta[server.status] || { label: server.status || '-', type: 'info' }
}

function formatTime(value) {
  return value ? new Date(value).toLocaleString() : '-'
}

async function load() {
  loading.value = true
  try {
    servers.value = await listServers()
  } catch (e) {
    ElMessage.error(e.message || '加载服务器失败')
  } finally {
    loading.value = false
  }
}

function openCreate() {
  createName.value = ''
  createError.value = ''
  createVisible.value = true
}

async function submitCreate() {
  createError.value = ''
  const name = createName.value.trim()
  if (!name) {
    createError.value = '请输入服务器名称'
    return
  }
  if (name.length > 64) {
    createError.value = '服务器名称不能超过 64 个字符'
    return
  }
  try {
    const data = await createServer({ name })
    createVisible.value = false
    showSecret(data.server?.serverName || name, data.secret, data.wsUrl)
    await load()
  } catch (e) {
    createError.value = e.message || '创建失败'
  }
}

function showSecret(serverName, secret, wsUrl) {
  secretInfo.serverName = serverName
  secretInfo.secret = secret || ''
  secretInfo.wsUrl = wsUrl || ''
  secretVisible.value = true
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
  const lines = [
    `STRUCTENGINE_PLATFORM_URL=${secretInfo.wsUrl}`,
    `STRUCTENGINE_SERVER_SECRET=${secretInfo.secret}`,
  ]
  const res = await copyText(lines.join('\n'))
  if (res.ok) {
    ElMessage.success('插件配置已复制')
  } else {
    ElMessage.warning('复制失败，请手动选择')
  }
}

async function rename(server) {
  let value
  try {
    const result = await ElMessageBox.prompt('请输入新的服务器名称', '重命名服务器', {
      inputValue: server.serverName,
      inputValidator: (input) => Boolean(input && input.trim() && input.trim().length <= 64)
        || '名称需为 1~64 个字符',
    })
    value = result.value
  } catch (e) {
    return
  }
  try {
    await updateServer(server.serverId, { name: value.trim() })
    ElMessage.success('服务器已重命名')
    await load()
  } catch (e) {
    ElMessage.error(e.message || '重命名失败')
  }
}

async function rotate(server) {
  try {
    await ElMessageBox.confirm(
      `轮换后旧 secret 立即失效，插件需要更换新 secret 才能重连。确定轮换 ${server.serverName} 吗？`,
      '轮换 secret',
      { type: 'warning' },
    )
  } catch (e) {
    return
  }
  try {
    const data = await rotateServerSecret(server.serverId)
    showSecret(server.serverName, data.secret, data.wsUrl)
    await load()
  } catch (e) {
    ElMessage.error(e.message || '轮换失败')
  }
}

async function toggleStatus(server) {
  const disabling = server.status !== 'DISABLED'
  try {
    await ElMessageBox.confirm(
      disabling
        ? `禁用后 ${server.serverName} 的插件会被断开，确定禁用吗？`
        : `确定启用 ${server.serverName} 吗？`,
      disabling ? '禁用服务器' : '启用服务器',
      { type: 'warning' },
    )
  } catch (e) {
    return
  }
  actionLoading.value = server.serverId
  try {
    if (disabling) {
      await disableServer(server.serverId)
    } else {
      await enableServer(server.serverId)
    }
    ElMessage.success(disabling ? '服务器已禁用' : '服务器已启用')
    await load()
  } catch (e) {
    ElMessage.error(e.message || '操作失败')
  } finally {
    actionLoading.value = ''
  }
}

async function remove(server) {
  try {
    await ElMessageBox.confirm(
      `确定删除服务器 ${server.serverName} 吗？删除后插件无法再连接，模型/任务记录会保留。`,
      '删除服务器',
      { type: 'error' },
    )
  } catch (e) {
    return
  }
  try {
    await deleteServer(server.serverId)
    ElMessage.success('服务器已删除')
    await load()
  } catch (e) {
    ElMessage.error(e.message || '删除失败')
  }
}

onMounted(load)
</script>

<template>
  <section class="servers-page">
    <div class="page-head">
      <div>
        <h2>服务器管理</h2>
        <p v-if="isAdmin">管理员可查看和管理全部服务器；创建服务器请使用普通服主账号。参考图生成将在下一阶段接入服务器上下文。</p>
        <p v-else>创建服务器后生成一次性 secret，配置到 StructEngine 插件即可完成注册。</p>
      </div>
      <div class="head-actions">
        <el-button @click="load">刷新</el-button>
        <el-button v-if="!isAdmin" type="primary" @click="openCreate">新建服务器</el-button>
      </div>
    </div>

    <el-alert
      v-if="isAdmin"
      type="info"
      show-icon
      :closable="false"
      title="管理员账号不拥有服务器"
      description="管理员可以管理全部服务器；如需自建服务器，请先创建普通用户并使用该账号登录。"
    />

    <el-table v-loading="loading" :data="servers" border class="server-table">
      <el-table-column label="服务器" min-width="180">
        <template #default="{ row }">
          <router-link :to="`/servers/${row.serverId}`" class="server-name server-name-link">
            {{ row.serverName }}
          </router-link>
          <div class="server-id">{{ row.serverId }}</div>
        </template>
      </el-table-column>
      <el-table-column v-if="isAdmin" label="归属用户 ID" width="190">
        <template #default="{ row }">
          <span class="server-id">{{ row.ownerUserId }}</span>
        </template>
      </el-table-column>
      <el-table-column label="状态" width="110">
        <template #default="{ row }">
          <el-tag :type="statusOf(row).type" size="small">{{ statusOf(row).label }}</el-tag>
          <div v-if="row.connected" class="online-dot">WS 已连接</div>
        </template>
      </el-table-column>
      <el-table-column label="BlueMap" min-width="220">
        <template #default="{ row }">
          <span v-if="row.blueMapUrl" class="mono">{{ row.blueMapUrl }}</span>
          <span v-else class="muted">尚未上报</span>
          <div v-if="row.mapId" class="muted small">mapId: {{ row.mapId }}</div>
        </template>
      </el-table-column>
      <el-table-column label="玩家" width="80">
        <template #default="{ row }">{{ row.playerCount || 0 }}</template>
      </el-table-column>
      <el-table-column label="最后在线" width="180">
        <template #default="{ row }">{{ formatTime(row.lastSeenAt) }}</template>
      </el-table-column>
      <el-table-column label="操作" width="390" fixed="right">
        <template #default="{ row }">
          <el-button size="small" @click="rename(row)">重命名</el-button>
          <el-button size="small" @click="rotate(row)">轮换 secret</el-button>
          <el-button
            size="small"
            :loading="actionLoading === row.serverId"
            @click="toggleStatus(row)"
          >
            {{ row.status === 'DISABLED' ? '启用' : '禁用' }}
          </el-button>
          <el-button size="small" type="danger" plain @click="remove(row)">删除</el-button>
          <router-link :to="`/servers/${row.serverId}`" class="action-link">详情</router-link>
        </template>
      </el-table-column>
    </el-table>

    <div v-if="!loading && servers.length === 0" class="empty">
      <p>还没有服务器。</p>
      <el-button v-if="!isAdmin" type="primary" @click="openCreate">创建第一台服务器</el-button>
    </div>

    <el-dialog v-model="createVisible" title="新建 Minecraft 服务器" width="440px">
      <div class="dialog-form">
        <el-input
          v-model="createName"
          maxlength="64"
          show-word-limit
          placeholder="例如：生存一服"
          @keyup.enter="submitCreate"
        />
        <p class="dialog-hint">
          创建后会生成一次性 secret，请配置到该服的 StructEngine 插件；平台收到插件注册后状态变为在线。
        </p>
        <p v-if="createError" class="dialog-error">{{ createError }}</p>
      </div>
      <template #footer>
        <el-button @click="createVisible = false">取消</el-button>
        <el-button type="primary" @click="submitCreate">创建</el-button>
      </template>
    </el-dialog>

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
        <el-input :model-value="secretInfo.secret" readonly class="secret-input">
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
.servers-page {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.page-head {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 16px;
  flex-wrap: wrap;
}

.page-head h2 {
  margin: 0;
  font-size: 24px;
}

.page-head p {
  margin: 8px 0 0;
  color: var(--rc-text-dim);
  font-size: 13px;
}

.head-actions {
  display: flex;
  gap: 8px;
}

.server-table {
  width: 100%;
}

.server-name {
  font-weight: 600;
}

.server-name-link {
  color: var(--rc-text);
  text-decoration: none;
}

.server-name-link:hover {
  color: var(--el-color-primary);
}

.server-id,
.mono {
  color: var(--rc-text-dim);
  font-family: Consolas, monospace;
  font-size: 12px;
  word-break: break-all;
}

.small {
  font-size: 11px;
}

.muted {
  color: var(--rc-text-dim);
}

.online-dot {
  margin-top: 4px;
  color: var(--el-color-primary);
  font-size: 11px;
}

.action-link {
  margin-left: 8px;
  color: var(--el-color-primary);
  font-size: 12px;
}

.empty {
  padding: 48px;
  border: 1px dashed var(--rc-panel-border);
  border-radius: 12px;
  text-align: center;
  color: var(--rc-text-dim);
}

.dialog-form {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.dialog-hint {
  margin: 0;
  color: var(--rc-text-dim);
  font-size: 12px;
  line-height: 1.7;
}

.dialog-error {
  margin: 0;
  color: #ff9b9b;
  font-size: 13px;
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

.secret-input :deep(input) {
  font-family: Consolas, monospace;
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
