import { ref } from 'vue'
import { generateFromImages, fetchModel, GenerateError, NETWORK_ERROR } from '../api/generate.js'
import { parseVoxelData } from '../three/parseVoxelData'

const MAX_IMAGES = 3

export function useGenerate(serverId = '') {
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
      if (!serverId) {
        throw new GenerateError('请从服务器详情进入参考图生成')
      }
      const data = await generateFromImages(serverId, fileList.value, description.value)
      result.value = data.modelUrl
      voxels.value = data.blocks && data.blocks.length > 0
        ? parseVoxelData(data.blocks).blocks
        : parseVoxelData(await fetchModel(data.modelUrl)).blocks
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
