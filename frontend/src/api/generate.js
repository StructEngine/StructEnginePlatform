const FALLBACK_MESSAGES = {
  400: '输入不合法',
  413: '文件过大或数量超限',
  422: '数据解析失败',
  502: '上游推理失败',
  500: '存储失败',
}

const NETWORK_ERROR = '网络异常或服务不可用'

export class GenerateError extends Error {
  constructor(message, code = 0) {
    super(message)
    this.name = 'GenerateError'
    this.code = code
  }
}

export async function generateModel(files, description = '') {
  const formData = new FormData()
  for (const file of files) {
    formData.append('images', file)
  }
  const extraDescription = typeof description === 'string' ? description.trim() : ''
  if (extraDescription) {
    formData.append('description', extraDescription)
  }

  let response
  try {
    response = await fetch('/api/generate', {
      method: 'POST',
      body: formData,
    })
  } catch (e) {
    throw new GenerateError(NETWORK_ERROR)
  }

  let payload = null
  try {
    payload = await response.json()
  } catch (e) {
    // 非 JSON 错误响应（如网关超时页）走通用错误处理
  }

  if (!response.ok) {
    const code = (payload && payload.code) || response.status
    const message = (payload && payload.message)
      || FALLBACK_MESSAGES[code]
      || FALLBACK_MESSAGES[response.status]
      || NETWORK_ERROR
    throw new GenerateError(message, code)
  }

  if (!payload) {
    throw new GenerateError(NETWORK_ERROR)
  }

  if (payload.code === 200) {
    const jsonUrl = payload.data && payload.data.json_url
    if (!jsonUrl) {
      throw new GenerateError(NETWORK_ERROR)
    }
    return jsonUrl
  }

  const code = payload.code || 0
  const message = payload.message || FALLBACK_MESSAGES[code] || NETWORK_ERROR
  throw new GenerateError(message, code)
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
