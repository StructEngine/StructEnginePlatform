<script setup>
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import CaptchaField from '../components/CaptchaField.vue'
import { useAuth } from '../stores/auth'

const router = useRouter()
const auth = useAuth()

const username = ref('')
const password = ref('')
const captchaCode = ref('')
const captchaId = ref('')
const error = ref('')
const captchaRef = ref(null)

async function submit() {
  error.value = ''
  if (!username.value.trim() || !password.value) {
    error.value = '请输入用户名和密码'
    return
  }
  if (!captchaCode.value.trim()) {
    error.value = '请输入图形验证码'
    return
  }
  try {
    const data = await auth.login({
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
    error.value = e.message || '登录失败'
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
      <h2>登录</h2>
      <p class="auth-subtitle">管理员与 Minecraft 服主统一入口</p>

      <el-input
        v-model="username"
        placeholder="用户名"
        size="large"
        autocomplete="username"
        @keyup.enter="submit"
      />
      <el-input
        v-model="password"
        type="password"
        show-password
        placeholder="密码"
        size="large"
        autocomplete="current-password"
        @keyup.enter="submit"
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
        登录
      </el-button>

      <p class="auth-switch">
        还没有账号？
        <router-link to="/register">注册 Minecraft 服主账号</router-link>
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
