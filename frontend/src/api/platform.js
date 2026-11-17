import { ApiError, request } from './http.js'

const API_BASE = '/api'

function api(path, options) {
  return request(`${API_BASE}${path}`, options)
}

export { ApiError }

export function listServers() {
  return api('/servers')
}

export function createServer({ name }) {
  return api('/servers', {
    method: 'POST',
    body: JSON.stringify({ name }),
  })
}

export function getServer(serverId) {
  return api(`/servers/${encodeURIComponent(serverId)}`)
}

export function updateServer(serverId, { name }) {
  return api(`/servers/${encodeURIComponent(serverId)}`, {
    method: 'PUT',
    body: JSON.stringify({ name }),
  })
}

export function rotateServerSecret(serverId) {
  return api(`/servers/${encodeURIComponent(serverId)}/secret/rotate`, { method: 'POST' })
}

export function disableServer(serverId) {
  return api(`/servers/${encodeURIComponent(serverId)}/disable`, { method: 'POST' })
}

export function enableServer(serverId) {
  return api(`/servers/${encodeURIComponent(serverId)}/enable`, { method: 'POST' })
}

export function deleteServer(serverId) {
  return api(`/servers/${encodeURIComponent(serverId)}`, { method: 'DELETE' })
}

export function sendChat({ sessionId, message }) {
  return api('/ai/chat', {
    method: 'POST',
    body: JSON.stringify({ sessionId, message }),
  })
}

export function chatHistory(sessionId) {
  return api(`/ai/chat/${encodeURIComponent(sessionId)}`)
}

export function generateModel({ serverId, playerUuid, playerName, prompt }) {
  return api('/models/generate', {
    method: 'POST',
    body: JSON.stringify({ serverId, playerUuid, playerName, prompt }),
  })
}

export function deployModel({ serverId, playerUuid, playerName, modelId, centerX, centerZ, rotation }) {
  return api('/tasks/deploy', {
    method: 'POST',
    body: JSON.stringify({ serverId, playerUuid, playerName, modelId, centerX, centerZ, rotation }),
  })
}

export function getTask(taskId) {
  return api(`/tasks/${encodeURIComponent(taskId)}`)
}

export function listHistory(serverId, playerUuid, playerName) {
  const params = new URLSearchParams({ playerUuid, playerName })
  return api(`/servers/${encodeURIComponent(serverId)}/history?${params}`)
}

export function rollback(serverId, { playerUuid, playerName, historyId }) {
  return api(`/servers/${encodeURIComponent(serverId)}/rollback`, {
    method: 'POST',
    body: JSON.stringify({ playerUuid, playerName, historyId }),
  })
}

export function redo(serverId, { playerUuid, playerName, historyId }) {
  return api(`/servers/${encodeURIComponent(serverId)}/redo`, {
    method: 'POST',
    body: JSON.stringify({ playerUuid, playerName, historyId }),
  })
}

export function me() {
  return api('/auth/me')
}

export function changePassword({ oldPassword, newPassword }) {
  return api('/auth/password', {
    method: 'POST',
    body: JSON.stringify({ oldPassword, newPassword }),
  })
}

export function listAdminUsers({ page = 1, size = 20, keyword = '' } = {}) {
  const params = new URLSearchParams({ page: String(page), size: String(size) })
  if (keyword && keyword.trim()) {
    params.set('keyword', keyword.trim())
  }
  return api(`/admin/users?${params}`)
}

export function createAdminUser({ username, password }) {
  return api('/admin/users', {
    method: 'POST',
    body: JSON.stringify({ username, password }),
  })
}

export function updateAdminUser(userId, { username, status }) {
  return api(`/admin/users/${encodeURIComponent(userId)}`, {
    method: 'PUT',
    body: JSON.stringify({ username, status }),
  })
}

export function resetAdminUserPassword(userId, password) {
  return api(`/admin/users/${encodeURIComponent(userId)}/password`, {
    method: 'PUT',
    body: JSON.stringify({ password }),
  })
}

export function deleteAdminUser(userId) {
  return api(`/admin/users/${encodeURIComponent(userId)}`, { method: 'DELETE' })
}
