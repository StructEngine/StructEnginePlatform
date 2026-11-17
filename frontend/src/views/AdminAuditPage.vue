<script setup>
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { listAdminAudit } from '../api/platform'

const logs = ref([])
const total = ref(0)
const page = ref(1)
const size = ref(20)
const serverId = ref('')
const action = ref('')
const loading = ref(false)

const actions = [
  'SERVER_CREATE', 'SERVER_RENAME', 'SERVER_SECRET_ROTATE', 'SERVER_DISABLE',
  'SERVER_ENABLE', 'SERVER_DELETE', 'MODEL_GENERATE', 'MODEL_DELETE',
  'BUILD_DEPLOY', 'BUILD_ROLLBACK', 'BUILD_REDO',
]

function formatTime(value) {
  return value ? new Date(value).toLocaleString() : '-'
}

async function load() {
  loading.value = true
  try {
    const data = await listAdminAudit({
      page: page.value,
      size: size.value,
      serverId: serverId.value.trim(),
      action: action.value,
    })
    logs.value = data.items || []
    total.value = data.total || 0
  } catch (e) {
    ElMessage.error(e.message || '加载审计日志失败')
  } finally {
    loading.value = false
  }
}

function search() {
  page.value = 1
  load()
}

function onPageChange(value) {
  page.value = value
  load()
}

onMounted(load)
</script>

<template>
  <section class="audit-page">
    <div class="page-head">
      <h2>审计日志</h2>
      <p>记录服务器管理、模型生成、部署与回滚等高风险操作。</p>
    </div>

    <div class="toolbar">
      <el-input v-model="serverId" placeholder="服务器 ID" clearable class="server-input" @keyup.enter="search" />
      <el-select v-model="action" placeholder="全部操作" clearable class="action-select" @change="search">
        <el-option v-for="item in actions" :key="item" :label="item" :value="item" />
      </el-select>
      <el-button @click="search">查询</el-button>
      <el-button @click="load">刷新</el-button>
      <span class="total">共 {{ total }} 条</span>
    </div>

    <el-table v-loading="loading" :data="logs" border>
      <el-table-column label="时间" width="180">
        <template #default="{ row }">{{ formatTime(row.createdAt) }}</template>
      </el-table-column>
      <el-table-column label="操作者" width="150">
        <template #default="{ row }">{{ row.actorName || '-' }}<span class="muted"> ({{ row.actorType }})</span></template>
      </el-table-column>
      <el-table-column prop="action" label="操作" width="180" />
      <el-table-column prop="serverId" label="服务器" width="180" />
      <el-table-column prop="modelId" label="模型" width="180" />
      <el-table-column prop="taskId" label="任务" width="180" />
      <el-table-column label="坐标" width="150">
        <template #default="{ row }">
          <span v-if="row.x != null">{{ row.x }}, {{ row.y }}, {{ row.z }}</span>
          <span v-else>-</span>
        </template>
      </el-table-column>
      <el-table-column prop="result" label="结果" width="100" />
      <el-table-column prop="ip" label="IP" width="140" />
      <el-table-column prop="detail" label="详情" min-width="160" show-overflow-tooltip />
    </el-table>

    <div class="pager">
      <el-pagination
        layout="prev, pager, next"
        :total="total"
        :page-size="size"
        :current-page="page"
        @current-change="onPageChange"
      />
    </div>
  </section>
</template>

<style scoped>
.audit-page {
  display: flex;
  flex-direction: column;
  gap: 16px;
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

.toolbar {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.server-input {
  width: 220px;
}

.action-select {
  width: 190px;
}

.total {
  margin-left: auto;
  color: var(--rc-text-dim);
  font-size: 13px;
}

.muted {
  color: var(--rc-text-dim);
  font-size: 11px;
}

.pager {
  display: flex;
  justify-content: flex-end;
}
</style>
