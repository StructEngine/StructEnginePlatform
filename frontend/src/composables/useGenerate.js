import { ref } from 'vue'
import { generateModel, fetchModel, GenerateError, NETWORK_ERROR } from '../api/generate'
import { parseVoxelData } from '../three/parseVoxelData'

const MAX_IMAGES = 3

export function useGenerate() {
  const fileList = ref([])
  const description = ref('')
  const loading = ref(false)
  const result = ref(null)
  const error = ref('')
  const voxels = ref(null)
  const previewError = ref('')
  const previewLoading = ref(false)

  async function submit() {
    if (loading.value) {
      return
    }
    if (fileList.value.length === 0) {
      error.value = '请至少选择 1 张参考图'
      return
    }
    if (fileList.value.length > MAX_IMAGES) {
      error.value = `最多上传 ${MAX_IMAGES} 张参考图`
      return
    }

    error.value = ''
    result.value = null
    voxels.value = null
    previewError.value = ''
    loading.value = true
    try {
      const jsonUrl = await generateModel(fileList.value, description.value)
      result.value = jsonUrl

      previewLoading.value = true
      try {
        const raw = await fetchModel(jsonUrl)
        const parsed = parseVoxelData(raw)
        voxels.value = parsed.blocks
      } catch (e) {
        voxels.value = null
        previewError.value = '预览数据加载失败'
      } finally {
        previewLoading.value = false
      }
    } catch (e) {
      error.value = e instanceof GenerateError ? e.message : NETWORK_ERROR
    } finally {
      loading.value = false
    }
  }

  function reset() {
    fileList.value = []
    description.value = ''
    result.value = null
    error.value = ''
    voxels.value = null
    previewError.value = ''
    previewLoading.value = false
  }

  return {
    fileList,
    description,
    loading,
    result,
    error,
    voxels,
    previewError,
    previewLoading,
    submit,
    reset,
  }
}
