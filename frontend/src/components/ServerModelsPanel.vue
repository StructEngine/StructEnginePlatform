<script setup>
import { onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { deleteServerModel, listServerModels } from '../api/platform'
import { copyText } from '../utils/clipboard'

const props = defineProps({
  serverId: { type: String, required: true },
})

const models = ref([])
const total = ref(0)
const page = ref(1)
const size = ref(20)
const source = ref('')
const loading = ref(false)

const sourceLabel = {
  MAP_AI: '地图 AI',
  REFERENCE_IMAGE: '参考图',
}

function formatTime(value) {
  return value ? new Date(value).toLocaleString() : '-'
}

async function load() {
  loading.value = true
  try {
    const data = await listServerModels(props.serverId, {
      page: page.value,
      size: size.value,
      source: source.value,
    })
    models.value = data.items || []
    total.value = data.total || 0
  } catch (e) {
    ElMessage.error(e.message || '加载模型失败')
  } finally {
    loading.value = false
  }
}

async function copyCommand(model) {
  const res = await copyText(`/buildmodel ${model.modelUrl}`)
  if (res.ok) {
    ElMessage.success('构建指令已复制')
  } else {
    ElMessage.warning('复制失败，请手动复制模型链接')
  }
}

async function remove(model) {
  try {
    await ElMessageBox.confirm(
      '删除后模型会从列表隐藏，但公开 JSON 链接仍永久可访问。确定删除吗？',
      '删除模型',
      { type: 'warning' },
    )
  } catch (e) {
    return
  }
  try {
    await deleteServerModel(props.serverId, model.modelId)
    ElMessage.success('模型已删除')
    await load()
  } catch (e) {
    ElMessage.error(e.message || '删除失败')
  }
}

function onPageChange(value) {
  page.value = value
  load()
}

onMounted(load)
</script>

<template>
  <section class="models-panel">
    <div class="toolbar">
      <el-select v-model="source" placeholder="全部来源" clearable class="source-select" @change="page = 1; load()">
        <el-option label="地图 AI" value="MAP_AI" />
        <el-option label="参考图" value="REFERENCE_IMAGE" />
      </el-select>
      <el-button @click="load">刷新</el-button>
      <span class="total">共 {{ total }} 个模型</span>
    </div>

    <el-table v-loading="loading" :data="models" border>
      <el-table-column prop="modelId" label="模型 ID" width="190" />
      <el-table-column label="来源" width="100">
        <template #default="{ row }">
          <el-tag size="small" :type="row.source === 'MAP_AI' ? 'success' : 'warning'">
            {{ sourceLabel[row.source] || row.source }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="blockCount" label="方块" width="90" />
      <el-table-column label="尺寸" width="150">
        <template #default="{ row }">
          {{ row.maxX - row.minX + 1 }} × {{ row.maxY - row.minY + 1 }} × {{ row.maxZ - row.minZ + 1 }}
        </template>
      </el-table-column>
      <el-table-column prop="prompt" label="描述" min-width="180" show-overflow-tooltip />
      <el-table-column label="创建时间" width="180">
        <template #default="{ row }">{{ formatTime(row.createdAt) }}</template>
      </el-table-column>
      <el-table-column label="操作" width="260" fixed="right">
        <template #default="{ row }">
          <a :href="row.modelUrl" target="_blank" rel="noopener" class="link">下载 JSON</a>
          <el-button size="small" @click="copyCommand(row)">复制指令</el-button>
          <el-button size="small" type="danger" plain @click="remove(row)">删除</el-button>
        </template>
      </el-table-column>
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
.models-panel {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.toolbar {
  display: flex;
  align-items: center;
  gap: 8px;
}

.source-select {
  width: 160px;
}

.total {
  margin-left: auto;
  color: var(--rc-text-dim);
  font-size: 13px;
}

.link {
  margin-right: 8px;
  color: var(--el-color-primary);
  font-size: 12px;
}

.pager {
  display: flex;
  justify-content: flex-end;
}
</style>
