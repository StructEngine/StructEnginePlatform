<script setup>
import { computed, ref } from 'vue'
import { ElMessage } from 'element-plus'

const props = defineProps({
  loading: { type: Boolean, default: false },
  description: { type: String, default: '' },
})

const emit = defineEmits(['change-files', 'submit', 'update:description'])

const fileCount = ref(0)

const descriptionText = computed({
  get: () => props.description,
  set: (value) => emit('update:description', value),
})

const submitLabel = computed(() => {
  if (fileCount.value === 0) {
    return '请先选择参考图'
  }
  return fileCount.value < 3 ? '生成方块模型（AI 补全）' : '生成方块模型'
})

function syncFiles(uploadFiles) {
  const files = uploadFiles.map((f) => f.raw).filter(Boolean)
  fileCount.value = files.length
  emit('change-files', files)
}

function handleChange(uploadFile, uploadFiles) {
  syncFiles(uploadFiles)
}

function handleRemove(uploadFile, uploadFiles) {
  syncFiles(uploadFiles)
}

function handleExceed() {
  ElMessage.warning('最多上传 3 张参考图（三视图仅作建议）')
}

function handleSubmit() {
  if (fileCount.value === 0) {
    ElMessage.warning('请先选择至少 1 张参考图')
    return
  }
  emit('submit')
}
</script>

<template>
  <section class="upload">
    <div class="upload-card">
      <div class="upload-head">
        <span class="upload-title">参考图（1~3 张）</span>
        <span class="upload-badge">三视图仅为建议</span>
      </div>

      <el-upload
        list-type="picture-card"
        :auto-upload="false"
        :limit="3"
        accept="image/*"
        :on-change="handleChange"
        :on-remove="handleRemove"
        :on-exceed="handleExceed"
      >
        <div class="upload-trigger">
          <span class="upload-plus">+</span>
          <span class="upload-hint">选择图片</span>
        </div>
      </el-upload>

      <p class="upload-tip">
        已选择 {{ fileCount }} / 3 张。正视图、侧视图、俯视图只是推荐；
        不足三视图也可以直接提交，后端会调用多模态模型推断补全建筑结构。
      </p>

      <el-input
        v-model="descriptionText"
        type="textarea"
        :rows="3"
        maxlength="1000"
        show-word-limit
        placeholder="可选补充说明：建筑类型、层数、屋顶、材质、颜色、门窗位置等。参考图不足三视图时，AI 会结合说明补全。"
      />

      <div class="upload-actions">
        <el-button
          type="primary"
          size="large"
          :disabled="loading || fileCount === 0"
          :loading="loading"
          @click="handleSubmit"
        >
          {{ submitLabel }}
        </el-button>
      </div>
    </div>
  </section>
</template>

<style scoped>
.upload {
  margin-bottom: 24px;
}

.upload-card {
  background: var(--rc-panel);
  border: 1px solid var(--rc-panel-border);
  border-radius: 12px;
  padding: 24px;
}

.upload-head {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 14px;
}

.upload-title {
  font-size: 15px;
  font-weight: 700;
  color: var(--rc-text);
}

.upload-badge {
  padding: 2px 8px;
  border: 1px solid rgba(124, 189, 75, 0.45);
  border-radius: 999px;
  color: var(--el-color-primary);
  font-size: 11px;
  letter-spacing: 1px;
}

.upload-trigger {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 6px;
}

.upload-plus {
  font-size: 28px;
  line-height: 1;
  color: var(--el-color-primary);
}

.upload-hint {
  font-size: 13px;
  color: var(--rc-text-dim);
}

.upload-tip {
  margin: 14px 0;
  font-size: 12px;
  line-height: 1.7;
  color: var(--rc-text-dim);
}

.upload-actions {
  margin-top: 20px;
  display: flex;
  justify-content: center;
}
</style>
