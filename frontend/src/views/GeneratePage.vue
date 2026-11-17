<script setup>
import { useGenerate } from '../composables/useGenerate'
import GuideSection from '../components/GuideSection.vue'
import UploadPanel from '../components/UploadPanel.vue'
import ResultPanel from '../components/ResultPanel.vue'

const props = defineProps({
  serverId: { type: String, default: '' },
})

const { fileList, description, loading, result, error, voxels, submit } = useGenerate(props.serverId)

function handleFilesChange(files) {
  fileList.value = files
}
</script>

<template>
  <div
    v-loading="loading"
    element-loading-text="正在调用多模态模型生成方块模型..."
    class="generate-page"
  >
    <GuideSection />
    <UploadPanel
      v-model:description="description"
      :loading="loading"
      @change-files="handleFilesChange"
      @submit="submit"
    />
    <ResultPanel :result="result" :error="error" :voxels="voxels" />
  </div>
</template>

<style scoped>
.generate-page {
  display: flex;
  flex-direction: column;
}
</style>
