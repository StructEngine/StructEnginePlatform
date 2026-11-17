<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { request } from '../api/http'

const users = ref([])
const total = ref(0)
const page = ref(1)
const size = ref(20)
const keyword = ref('')
const loading = ref(false)

const createVisible = ref(false)
const createForm = reactive({ username: '', password: '' })
const createError = ref('')
const creating = ref(false)

const editVisible = ref(false)
const editForm = reactive({ id: '', username: '', status: 'ACTIVE' })
const editError = ref('')
const editing = ref(false)

const resetVisible = ref(false)
const resetForm = reactive({ id: '', username: '', password: '' })
const resetError = ref('')
const resetting = ref(false)

function formatTime(value) {
  return value ? new Date(value).toLocaleString() : '-'
}

async function load() {
  loading.value = true
  try {
    const params = new URLSearchParams({
      page: String(page.value),
      size: String(size.value),
    })
    if (keyword.value.trim()) {
      params.set('keyword', keyword.value.trim())
    }
    const data = await request(`/api/admin/users?${params}`)
    users.value = data.items || []
    total.value = data.total || 0
  } catch (e) {
    ElMessage.error(e.message || '加载用户失败')
  } finally {
    loading.value = false
  }
}

function search() {
  page.value = 1
  load()
}

function openCreate() {
  createForm.username = ''
  createForm.password = ''
  createError.value = ''
  createVisible.value = true
}

async function submitCreate() {
  createError.value = ''
  if (!/^[A-Za-z]{4,16}$/.test(createForm.username.trim())) {
    createError.value = '用户名必须为 4~16 位英文字母'
    return
  }
  if (createForm.password.length < 6 || createForm.password.length > 16) {
    createError.value = '密码长度必须为 6~16 位'
    return
  }
  creating.value = true
  try {
    await request('/api/admin/users', {
      method: 'POST',
      body: JSON.stringify({
        username: createForm.username.trim(),
        password: createForm.password,
      }),
    })
    createVisible.value = false
    ElMessage.success('用户已创建')
    await load()
  } catch (e) {
    createError.value = e.message || '创建失败'
  } finally {
    creating.value = false
  }
}

function openEdit(user) {
  editForm.id = user.id
  editForm.username = user.username
  editForm.status = user.status
  editError.value = ''
  editVisible.value = true
}

async function submitEdit() {
  editError.value = ''
  if (!/^[A-Za-z]{4,16}$/.test(editForm.username.trim())) {
    editError.value = '用户名必须为 4~16 位英文字母'
    return
  }
  editing.value = true
  try {
    await request(`/api/admin/users/${encodeURIComponent(editForm.id)}`, {
      method: 'PUT',
      body: JSON.stringify({
        username: editForm.username.trim(),
        status: editForm.status,
      }),
    })
    editVisible.value = false
    ElMessage.success('用户已更新')
    await load()
  } catch (e) {
    editError.value = e.message || '更新失败'
  } finally {
    editing.value = false
  }
}

function openReset(user) {
  resetForm.id = user.id
  resetForm.username = user.username
  resetForm.password = ''
  resetError.value = ''
  resetVisible.value = true
}

async function submitReset() {
  resetError.value = ''
  if (resetForm.password.length < 6 || resetForm.password.length > 16) {
    resetError.value = '密码长度必须为 6~16 位'
    return
  }
  resetting.value = true
  try {
    await request(`/api/admin/users/${encodeURIComponent(resetForm.id)}/password`, {
      method: 'PUT',
      body: JSON.stringify({ password: resetForm.password }),
    })
    resetVisible.value = false
    ElMessage.success('密码已重置，旧 token 已失效')
  } catch (e) {
    resetError.value = e.message || '重置失败'
  } finally {
    resetting.value = false
  }
}

async function toggleStatus(user) {
  const next = user.status === 'ACTIVE' ? 'DISABLED' : 'ACTIVE'
  const action = next === 'DISABLED' ? '禁用' : '启用'
  try {
    await ElMessageBox.confirm(`确定${action}用户 ${user.username} 吗？`, '确认操作', {
      type: 'warning',
    })
  } catch (e) {
    return
  }
  try {
    await request(`/api/admin/users/${encodeURIComponent(user.id)}`, {
      method: 'PUT',
      body: JSON.stringify({ status: next }),
    })
    ElMessage.success(`已${action}`)
    await load()
  } catch (e) {
    ElMessage.error(e.message || `${action}失败`)
  }
}

async function remove(user) {
  try {
    await ElMessageBox.confirm(
      `确定删除用户 ${user.username} 吗？删除后不可恢复。`,
      '删除用户',
      { type: 'error' },
    )
  } catch (e) {
    return
  }
  try {
    await request(`/api/admin/users/${encodeURIComponent(user.id)}`, { method: 'DELETE' })
    ElMessage.success('用户已删除')
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
  <section class="admin-users">
    <div class="page-head">
      <div>
        <h2>用户管理</h2>
        <p>管理 Minecraft 服主账号；禁用或重置密码会立即使旧 token 失效。</p>
      </div>
      <div class="head-actions">
        <router-link to="/change-password" class="plain-link">修改管理员密码</router-link>
        <el-button type="primary" @click="openCreate">新建用户</el-button>
      </div>
    </div>

    <div class="toolbar">
      <el-input
        v-model="keyword"
        placeholder="搜索用户名"
        clearable
        class="search-input"
        @keyup.enter="search"
      />
      <el-button @click="search">搜索</el-button>
      <el-button @click="load">刷新</el-button>
      <span class="total">共 {{ total }} 个用户</span>
    </div>

    <el-table v-loading="loading" :data="users" border class="user-table">
      <el-table-column prop="id" label="ID" width="190" />
      <el-table-column prop="username" label="用户名" min-width="140" />
      <el-table-column label="状态" width="100">
        <template #default="{ row }">
          <el-tag :type="row.status === 'ACTIVE' ? 'success' : 'info'" size="small">
            {{ row.status === 'ACTIVE' ? '正常' : '禁用' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="创建时间" width="180">
        <template #default="{ row }">{{ formatTime(row.createdAt) }}</template>
      </el-table-column>
      <el-table-column label="最后登录" width="180">
        <template #default="{ row }">{{ formatTime(row.lastLoginAt) }}</template>
      </el-table-column>
      <el-table-column label="操作" width="300" fixed="right">
        <template #default="{ row }">
          <el-button size="small" @click="openEdit(row)">编辑</el-button>
          <el-button size="small" @click="openReset(row)">重置密码</el-button>
          <el-button size="small" @click="toggleStatus(row)">
            {{ row.status === 'ACTIVE' ? '禁用' : '启用' }}
          </el-button>
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

    <el-dialog v-model="createVisible" title="新建用户" width="420px">
      <div class="dialog-form">
        <el-input v-model="createForm.username" placeholder="用户名（4~16 位英文字母）" maxlength="16" />
        <el-input
          v-model="createForm.password"
          type="password"
          show-password
          placeholder="密码（6~16 位）"
          maxlength="16"
        />
        <p v-if="createError" class="dialog-error">{{ createError }}</p>
      </div>
      <template #footer>
        <el-button @click="createVisible = false">取消</el-button>
        <el-button type="primary" :loading="creating" @click="submitCreate">创建</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="editVisible" title="编辑用户" width="420px">
      <div class="dialog-form">
        <el-input v-model="editForm.username" placeholder="用户名" maxlength="16" />
        <el-select v-model="editForm.status" placeholder="状态">
          <el-option label="正常" value="ACTIVE" />
          <el-option label="禁用" value="DISABLED" />
        </el-select>
        <p v-if="editError" class="dialog-error">{{ editError }}</p>
      </div>
      <template #footer>
        <el-button @click="editVisible = false">取消</el-button>
        <el-button type="primary" :loading="editing" @click="submitEdit">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="resetVisible" title="重置密码" width="420px">
      <div class="dialog-form">
        <p class="dialog-hint">为用户 {{ resetForm.username }} 设置新密码，旧 token 会立即失效。</p>
        <el-input
          v-model="resetForm.password"
          type="password"
          show-password
          placeholder="新密码（6~16 位）"
          maxlength="16"
        />
        <p v-if="resetError" class="dialog-error">{{ resetError }}</p>
      </div>
      <template #footer>
        <el-button @click="resetVisible = false">取消</el-button>
        <el-button type="primary" :loading="resetting" @click="submitReset">重置</el-button>
      </template>
    </el-dialog>
  </section>
</template>

<style scoped>
.admin-users {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.page-head {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 16px;
  flex-wrap: wrap;
}

.page-head h2 {
  margin: 0;
  font-size: 24px;
}

.page-head p {
  margin: 8px 0 0;
  color: var(--rc-text-dim);
  font-size: 13px;
}

.head-actions {
  display: flex;
  align-items: center;
  gap: 12px;
}

.plain-link {
  color: var(--rc-text-dim);
  font-size: 13px;
}

.toolbar {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.search-input {
  width: 260px;
}

.total {
  margin-left: auto;
  color: var(--rc-text-dim);
  font-size: 13px;
}

.user-table {
  width: 100%;
}

.pager {
  display: flex;
  justify-content: flex-end;
}

.dialog-form {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.dialog-error {
  margin: 0;
  color: #ff9b9b;
  font-size: 13px;
}

.dialog-hint {
  margin: 0;
  color: var(--rc-text-dim);
  font-size: 13px;
  line-height: 1.6;
}
</style>
