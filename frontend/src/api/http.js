const TOKEN_KEY = 'structengine.token'

const FALLBACK_MESSAGES = {
  400: '输入不合法',
  401: '请先登录',
  403: '没有权限',
  404: '资源不存在',
  409: '资源冲突',
  429: '请求过于频繁',
  500: '服务端处理失败',
  502: '上游服务失败',
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

export function getToken() {
  try {
    return window.localStorage.getItem(TOKEN_KEY)
  } catch (e) {
    return null
  }
}

export function setToken(token) {
  try {
    window.localStorage.setItem(TOKEN_KEY, token)
  } catch (e) {
    // localStorage 不可用时退化为仅内存，页面刷新后需重新登录
  }
}

export function clearToken() {
  try {
    window.localStorage.removeItem(TOKEN_KEY)
  } catch (e) {
    // ignore
  }
}

export async function request(path, options = {}) {
  const headers = { ...(options.headers || {}) }
  const isForm = typeof FormData !== 'undefined' && options.body instanceof FormData
  if (options.body !== undefined && !isForm && !headers['Content-Type']) {
    headers['Content-Type'] = 'application/json'
  }
  const token = getToken()
  if (token) {
    headers.Authorization = `Bearer ${token}`
  }

  let response
  try {
    response = await fetch(path, {
      credentials: 'same-origin',
      ...options,
      headers,
    })
  } catch (e) {
    throw new ApiError('网络异常或服务不可用')
  }

  let payload = null
  try {
    payload = await response.json()
  } catch (e) {
    // 非 JSON（网关错误页、空响应等）走状态码兜底
  }

  if (response.status === 401 && token) {
    clearToken()
    window.dispatchEvent(new CustomEvent('structengine:unauthorized'))
  }

  if (!response.ok) {
    const code = (payload && payload.code) || response.status
    const message = (payload && payload.message)
      || FALLBACK_MESSAGES[code]
      || FALLBACK_MESSAGES[response.status]
      || '请求失败'
    throw new ApiError(message, code)
  }

  if (!payload) {
    throw new ApiError('服务端返回格式异常')
  }
  if (payload.code === 200) {
    return payload.data
  }
  throw new ApiError(
    payload.message || FALLBACK_MESSAGES[payload.code] || '请求失败',
    payload.code || 0,
  )
}

export { FALLBACK_MESSAGES }
