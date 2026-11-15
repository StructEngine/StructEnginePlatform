const API_BASE = '/api/map'

const FALLBACK_MESSAGES = {
  400: '输入不合法，请检查坐标、玩家名与提示词',
  413: '请求内容过大',
  422: '模型数据解析失败',
  502: '上游推理失败',
  503: '游戏服务器未连接',
  504: '游戏服务器响应超时',
  500: '服务端处理失败',
}

export const NETWORK_ERROR = '网络异常或服务不可用'

export class MapBuildError extends Error {
  constructor(message, code = 0) {
    super(message)
    this.name = 'MapBuildError'
    this.code = code
  }
}

function fallbackMessage(code) {
  return FALLBACK_MESSAGES[code] || NETWORK_ERROR
}

async function readPayload(response) {
  try {
    return await response.json()
  } catch (e) {
    throw new MapBuildError(NETWORK_ERROR)
  }
}

async function request(path, options = {}, timeoutMs = 30000) {
  const controller = new AbortController()
  const timer = setTimeout(() => controller.abort(), timeoutMs)

  let response
  try {
    response = await fetch(`${API_BASE}${path}`, {
      ...options,
      signal: controller.signal,
    })
  } catch (e) {
    if (e && e.name === 'AbortError') {
      throw new MapBuildError('请求超时，请稍后重试', 504)
    }
    throw new MapBuildError(NETWORK_ERROR)
  } finally {
    clearTimeout(timer)
  }

  if (!response.ok) {
    throw new MapBuildError(NETWORK_ERROR)
  }

  const payload = await readPayload(response)
  if (payload && payload.code === 200) {
    return payload.data
  }

  const code = payload ? payload.code : 0
  const message = (payload && payload.message) || fallbackMessage(code)
  throw new MapBuildError(message, code)
}

/** 查询当前已连接的 Minecraft 游戏服。 */
export async function fetchGameServers() {
  const data = await request('/servers', { method: 'GET' }, 8000)
  if (Array.isArray(data)) {
    return data
  }
  return data && Array.isArray(data.servers) ? data.servers : []
}

/**
 * 提交地图选点构建任务。
 * 后端会同步等待游戏服返回 Y 轴，再生成模型并下发 BUILD。
 */
export async function createBuild({ serverId, x, z, playerName, prompt }) {
  return request(
    '/build',
    {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ serverId, x, z, playerName, prompt }),
    },
    30000,
  )
}

export { FALLBACK_MESSAGES }
