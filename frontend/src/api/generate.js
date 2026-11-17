import { ApiError, request } from './http.js'

const FALLBACK_MESSAGES = {
  400: '输入不合法',
  413: '文件过大或数量超限',
  422: '数据解析失败',
  500: '存储失败',
  502: '上游推理失败',
}

const NETWORK_ERROR = '网络异常或服务不可用'

export class GenerateError extends Error {
  constructor(message, code = 0) {
    super(message)
    this.name = 'GenerateError'
    this.code = code
  }
}

export async function generateFromImages(serverId, files, description = '') {
  const formData = new FormData()
  for (const file of files) {
    formData.append('images', file)
  }
  const extraDescription = typeof description === 'string' ? description.trim() : ''
  if (extraDescription) {
    formData.append('description', extraDescription)
  }

  try {
    const data = await request(`/api/servers/${encodeURIComponent(serverId)}/models/generate-from-images`, {
      method: 'POST',
      body: formData,
    })
    if (!data || !data.modelUrl) {
      throw new GenerateError(NETWORK_ERROR)
    }
    return data
  } catch (e) {
    if (e instanceof GenerateError) {
      throw e
    }
    const code = e instanceof ApiError ? e.code : 0
    const message = FALLBACK_MESSAGES[code] && [413, 422, 500, 502].includes(code)
      ? FALLBACK_MESSAGES[code]
      : (e.message || FALLBACK_MESSAGES[code] || NETWORK_ERROR)
    throw new GenerateError(message, code)
  }
}

export async function fetchModel(jsonUrl) {
  let pathname
  try {
    pathname = new URL(jsonUrl).pathname
  } catch (e) {
    throw new GenerateError(NETWORK_ERROR)
  }

  let response
  try {
    response = await fetch(pathname)
  } catch (e) {
    throw new GenerateError(NETWORK_ERROR)
  }

  if (!response.ok) {
    throw new GenerateError(NETWORK_ERROR)
  }

  try {
    return await response.json()
  } catch (e) {
    throw new GenerateError(NETWORK_ERROR)
  }
}

export { FALLBACK_MESSAGES, NETWORK_ERROR }
