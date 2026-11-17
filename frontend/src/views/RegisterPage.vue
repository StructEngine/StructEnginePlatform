<script setup>
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import CaptchaField from '../components/CaptchaField.vue'
import { useAuth } from '../stores/auth'

const router = useRouter()
const auth = useAuth()

const username = ref('')
const password = ref('')
const confirmPassword = ref('')
const captchaCode = ref('')
const captchaId = ref('')
const error = ref('')
const captchaRef = ref(null)

function validate() {
  if (!/^[A-Za-z]{4,16}$/.test(username.value.trim())) {
    return '用户名必须为 4~16 位英文字母'
  }
  if (password.value.length < 6 || password.value.length > 16) {
    return '密码长度必须为 6~16 位'
  }
  if (password.value !== confirmPassword.value) {
    return '两次输入的密码不一致'
  }
  if (!captchaCode.value.trim()) {
    return '请输入图形验证码'
  }
  return ''
}

async function submit() {
  error.value = validate()
  if (error.value) {
    return
  }
  try {
    const data = await auth.register({
      username: username.value.trim(),
      password: password.value,
      captchaId: captchaId.value,
      captchaCode: captchaCode.value.trim(),
    })
    if (data.user.mustChangePassword) {
      await router.replace('/change-password')
      return
    }
    await router.replace(auth.defaultRoute.value)
  } catch (e) {
    error.value = e.message || '注册失败'
    captchaCode.value = ''
    captchaRef.value?.refresh()
  }
}
</script>

<template>
  <div class="auth-page">
    <div class="auth-card">
      <div class="auth-brand">
        <span class="auth-logo">StructEngine</span>
        <span class="auth-badge">Platform</span>
      </div>
      <h2>注册服主账号</h2>
      <p class="auth-subtitle">用户名仅支持英文字母，注册后即可登录</p>

      <el-input
        v-model="username"
        placeholder="用户名（4~16 位英文字母）"
        size="large"
        maxlength="16"
        autocomplete="username"
      />
      <el-input
        v-model="password"
        type="password"
        show-password
        placeholder="密码（6~16 位）"
        size="large"
        maxlength="16"
        autocomplete="new-password"
      />
      <el-input
        v-model="confirmPassword"
        type="password"
        show-password
        placeholder="确认密码"
        size="large"
        maxlength="16"
        autocomplete="new-password"
      />
      <CaptchaField
        ref="captchaRef"
        v-model:code="captchaCode"
        v-model:captchaId="captchaId"
      />

      <p v-if="error" class="auth-error">{{ error }}</p>

      <el-button
        type="primary"
        size="large"
        class="auth-submit"
        :loading="auth.state.loading"
        @click="submit"
      >
        注册并登录
      </el-button>

      <p class="auth-switch">
        已有账号？
        <router-link to="/login">返回登录</router-link>
      </p>
    </div>
  </div>
</template>

<style scoped>
.auth-page {
  min-height: 70vh;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 40px 20px;
}

.auth-card {
  width: 380px;
  padding: 32px;
  border: 1px solid var(--rc-panel-border);
  border-radius: 16px;
  background: var(--rc-panel);
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.auth-brand {
  display: flex;
  align-items: center;
  gap: 8px;
}

.auth-logo {
  color: var(--el-color-primary);
  font-size: 18px;
  font-weight: 800;
  letter-spacing: 2px;
}

.auth-badge {
  padding: 2px 8px;
  border: 1px solid var(--rc-panel-border);
  border-radius: 999px;
  color: var(--rc-text-dim);
  font-size: 11px;
}

.auth-card h2 {
  margin: 4px 0 0;
  font-size: 24px;
}

.auth-subtitle {
  margin: 0 0 6px;
  color: var(--rc-text-dim);
  font-size: 13px;
}

.auth-submit {
  width: 100%;
  margin-top: 4px;
}

.auth-error {
  margin: 0;
  padding: 8px 10px;
  border: 1px solid rgba(255, 100, 100, 0.35);
  border-radius: 8px;
  background: rgba(255, 70, 70, 0.08);
  color: #ff9b9b;
  font-size: 13px;
}

.auth-switch {
  margin: 4px 0 0;
  color: var(--rc-text-dim);
  font-size: 13px;
  text-align: center;
}

.auth-switch a {
  color: var(--el-color-primary);
}
</style>
