<script setup>
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useAuth } from '../stores/auth'

const router = useRouter()
const auth = useAuth()

const oldPassword = ref('')
const newPassword = ref('')
const confirmPassword = ref('')
const error = ref('')
const saving = ref(false)

async function submit() {
  error.value = ''
  if (!oldPassword.value || !newPassword.value) {
    error.value = '请填写旧密码和新密码'
    return
  }
  if (newPassword.value.length < 6 || newPassword.value.length > 16) {
    error.value = '新密码长度必须为 6~16 位'
    return
  }
  if (newPassword.value !== confirmPassword.value) {
    error.value = '两次输入的新密码不一致'
    return
  }
  saving.value = true
  try {
    await auth.changePassword({
      oldPassword: oldPassword.value,
      newPassword: newPassword.value,
    })
    ElMessage.success('密码已修改')
    await router.replace(auth.defaultRoute.value)
  } catch (e) {
    error.value = e.message || '修改密码失败'
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <div class="auth-page">
    <div class="auth-card">
      <h2>修改密码</h2>
      <p class="auth-subtitle">
        {{ auth.state.user?.mustChangePassword ? '首次登录请先修改初始密码' : '修改后旧登录凭证会立即失效' }}
      </p>

      <el-input
        v-model="oldPassword"
        type="password"
        show-password
        placeholder="旧密码"
        size="large"
        autocomplete="current-password"
      />
      <el-input
        v-model="newPassword"
        type="password"
        show-password
        placeholder="新密码（6~16 位）"
        size="large"
        maxlength="16"
        autocomplete="new-password"
      />
      <el-input
        v-model="confirmPassword"
        type="password"
        show-password
        placeholder="确认新密码"
        size="large"
        maxlength="16"
        autocomplete="new-password"
        @keyup.enter="submit"
      />

      <p v-if="error" class="auth-error">{{ error }}</p>

      <el-button type="primary" size="large" class="auth-submit" :loading="saving" @click="submit">
        保存新密码
      </el-button>
    </div>
  </div>
</template>

<style scoped>
.auth-page {
  min-height: 60vh;
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

.auth-card h2 {
  margin: 0;
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
</style>
