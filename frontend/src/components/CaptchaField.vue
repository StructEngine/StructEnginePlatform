<script setup>
import { computed, onMounted, ref } from 'vue'
import { request } from '../api/http'

const props = defineProps({
  code: { type: String, default: '' },
})

const emit = defineEmits(['update:code', 'update:captchaId'])

const captchaId = ref('')
const image = ref('')
const loading = ref(false)
const error = ref('')

const codeText = computed({
  get: () => props.code,
  set: (value) => emit('update:code', value),
})

async function refresh() {
  loading.value = true
  error.value = ''
  try {
    const data = await request('/api/auth/captcha')
    captchaId.value = data.captchaId
    image.value = data.image
    emit('update:captchaId', data.captchaId)
    codeText.value = ''
  } catch (e) {
    error.value = e.message || '验证码加载失败'
  } finally {
    loading.value = false
  }
}

onMounted(refresh)

defineExpose({ refresh })
</script>

<template>
  <div class="captcha-field">
    <el-input
      v-model="codeText"
      maxlength="4"
      placeholder="图形验证码"
      class="captcha-input"
      autocomplete="off"
    />
    <button type="button" class="captcha-image-button" :disabled="loading" @click="refresh">
      <img v-if="image" :src="image" alt="图形验证码" class="captcha-image" />
      <span v-else class="captcha-placeholder">{{ loading ? '加载中...' : '点击刷新' }}</span>
    </button>
  </div>
  <p v-if="error" class="captcha-error">{{ error }}</p>
</template>

<style scoped>
.captcha-field {
  display: flex;
  gap: 10px;
  align-items: center;
}

.captcha-input {
  flex: 1;
}

.captcha-image-button {
  width: 120px;
  height: 40px;
  padding: 0;
  border: 1px solid var(--rc-panel-border);
  border-radius: 6px;
  background: #0d120d;
  cursor: pointer;
  overflow: hidden;
}

.captcha-image {
  width: 100%;
  height: 100%;
  display: block;
}

.captcha-placeholder {
  color: var(--rc-text-dim);
  font-size: 12px;
}

.captcha-error {
  margin: 6px 0 0;
  color: #ff8b8b;
  font-size: 12px;
}
</style>
