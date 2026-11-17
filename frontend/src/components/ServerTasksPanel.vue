<script setup>
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { listServerTasks } from '../api/platform'

const props = defineProps({
  serverId: { type: String, required: true },
})

const tasks = ref([])
const loading = ref(false)

const statusType = {
  QUEUED: 'info',
  DOWNLOADING: 'warning',
  BUILDING: 'warning',
  COMPLETED: 'success',
  FAILED: 'danger',
  ABORTED: 'danger',
}

function formatTime(value) {
  return value ? new Date(value).toLocaleString() : '-'
}

async function load() {
  loading.value = true
  try {
    tasks.value = await listServerTasks(props.serverId)
  } catch (e) {
    ElMessage.error(e.message || '加载任务失败')
  } finally {
    loading.value = false
  }
}

onMounted(load)
</script>

<template>
  <section class="tasks-panel">
    <div class="toolbar">
      <span class="hint">最近 100 条任务；进度由插件 BUILD_STATUS 实时回报。</span>
      <el-button @click="load">刷新</el-button>
    </div>

    <el-table v-loading="loading" :data="tasks" border>
      <el-table-column prop="id" label="任务 ID" width="190" />
      <el-table-column prop="playerName" label="玩家" width="130" />
      <el-table-column label="状态" width="120">
        <template #default="{ row }">
          <el-tag size="small" :type="statusType[row.status] || 'info'">{{ row.status }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="进度" width="110">
        <template #default="{ row }">{{ row.placed }} / {{ row.total }}</template>
      </el-table-column>
      <el-table-column label="中心" width="150">
        <template #default="{ row }">X {{ row.centerX }} Z {{ row.centerZ }}</template>
      </el-table-column>
      <el-table-column label="原点" width="170">
        <template #default="{ row }">{{ row.originX }}, {{ row.originY }}, {{ row.originZ }}</template>
      </el-table-column>
      <el-table-column prop="error" label="错误" min-width="180" show-overflow-tooltip />
      <el-table-column label="创建时间" width="180">
        <template #default="{ row }">{{ formatTime(row.createdAt) }}</template>
      </el-table-column>
    </el-table>

    <div v-if="!loading && tasks.length === 0" class="empty">暂无任务</div>
  </section>
</template>

<style scoped>
.tasks-panel {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
}

.hint {
  color: var(--rc-text-dim);
  font-size: 12px;
}

.empty {
  padding: 32px;
  text-align: center;
  color: var(--rc-text-dim);
  font-size: 13px;
}
</style>
