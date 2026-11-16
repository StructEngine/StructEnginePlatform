const API_BASE = '/api'

const FALLBACK_MESSAGES = {
  400: '输入不合法',
  401: '请先登录',
  403: '没有权限',
  404: '资源不存在',
  500: '服务端处理失败',
  503: '游戏服务器未连接',
  504: '游戏服务器响应超时',
}

export class ApiError extends Error {
  constructor(message, code = 0) {
    super(message)
    this.name = 'ApiError'
    this.code = code
  }
}

async function request(path, options = {}) {
  let response
  try {
    response = await fetch(`${API_BASE}${path}`, {
      credentials: 'include',
      headers: { 'Content-Type': 'application/json', ...(options.headers || {}) },
      ...options,
    })
  } catch (e) {
    throw new ApiError('网络异常或服务不可用')
  }
  if (response.status === 401) {
    throw new ApiError('请先登录', 401)
  }
  let payload
  try {
    payload = await response.json()
  } catch (e) {
    throw new ApiError('服务端返回格式异常')
  }
  if (payload && payload.code === 200) {
    return payload.data
  }
  throw new ApiError(
    (payload && payload.message) || FALLBACK_MESSAGES[payload && payload.code] || '请求失败',
    payload ? payload.code : 0,
  )
}

export function authStatus() {
  return request('/auth/status')
}

export function login(password) {
  return request('/auth/login', { method: 'POST', body: JSON.stringify({ password }) })
}

export function logout() {
  return request('/auth/logout', { method: 'POST', body: '{}' })
}

export function listServers() {
  return request('/servers')
}

export function sendChat({ sessionId, message }) {
  return request('/ai/chat', {
    method: 'POST',
    body: JSON.stringify({ sessionId, message }),
  })
}

export function chatHistory(sessionId) {
  return request(`/ai/chat/${encodeURIComponent(sessionId)}`)
}

export function generateModel({ serverId, playerUuid, playerName, prompt }) {
  return request('/models/generate', {
    method: 'POST',
    body: JSON.stringify({ serverId, playerUuid, playerName, prompt }),
  })
}

export function deployModel({ serverId, playerUuid, playerName, modelId, centerX, centerZ, rotation }) {
  return request('/tasks/deploy', {
    method: 'POST',
    body: JSON.stringify({ serverId, playerUuid, playerName, modelId, centerX, centerZ, rotation }),
  })
}

export function getTask(taskId) {
  return request(`/tasks/${encodeURIComponent(taskId)}`)
}

export function listHistory(serverId, playerUuid, playerName) {
  const params = new URLSearchParams({ playerUuid, playerName })
  return request(`/servers/${encodeURIComponent(serverId)}/history?${params}`)
}

export function rollback(serverId, { playerUuid, playerName, historyId }) {
  return request(`/servers/${encodeURIComponent(serverId)}/rollback`, {
    method: 'POST',
    body: JSON.stringify({ playerUuid, playerName, historyId }),
  })
}

export function redo(serverId, { playerUuid, playerName, historyId }) {
  return request(`/servers/${encodeURIComponent(serverId)}/redo`, {
    method: 'POST',
    body: JSON.stringify({ playerUuid, playerName, historyId }),
  })
}
