<script setup>
import { useAuth } from '../stores/auth'

const auth = useAuth()
</script>

<template>
  <section class="placeholder">
    <div class="placeholder-card">
      <span class="placeholder-badge">Phase 2</span>
      <h2>服务器管理即将开放</h2>
      <p>
        账号 <strong>{{ auth.state.user?.username }}</strong> 已创建成功。
        下一阶段将支持新建 Minecraft 服务器、生成注册 secret，并在服务器详情中使用
        AI 地图建造与参考图生成。
      </p>
      <p class="placeholder-hint">
        当前阶段管理员可在顶部导航进入“用户管理 / AI 地图建造 / 参考图生成”；普通用户的服务器入口将在
        Phase 2 接入。
      </p>
    </div>
  </section>
</template>

<style scoped>
.placeholder {
  padding: 60px 20px;
  display: flex;
  justify-content: center;
}

.placeholder-card {
  max-width: 620px;
  padding: 32px;
  border: 1px solid var(--rc-panel-border);
  border-radius: 16px;
  background: var(--rc-panel);
}

.placeholder-badge {
  display: inline-block;
  padding: 3px 10px;
  border: 1px solid rgba(124, 189, 75, 0.45);
  border-radius: 999px;
  color: var(--el-color-primary);
  font-size: 11px;
  letter-spacing: 1px;
}

.placeholder-card h2 {
  margin: 14px 0 10px;
  font-size: 24px;
}

.placeholder-card p {
  margin: 0 0 10px;
  color: var(--rc-text-dim);
  font-size: 14px;
  line-height: 1.8;
}

.placeholder-hint {
  font-size: 12px !important;
}
</style>
