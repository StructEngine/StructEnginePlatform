import { test } from 'node:test'
import assert from 'node:assert/strict'

import {
  createBuild,
  fetchGameServers,
  MapBuildError,
  NETWORK_ERROR,
} from './mapBuild.js'

function mockFetch(t, handler) {
  const original = globalThis.fetch
  globalThis.fetch = handler
  t.after(() => {
    globalThis.fetch = original
  })
}

function jsonResponse(body, ok = true) {
  return {
    ok,
    async json() {
      return body
    },
  }
}

test('createBuild 提交完整选点参数并返回 Y 轴结果', async (t) => {
  let captured
  mockFetch(t, async (url, options) => {
    captured = { url, options }
    return jsonResponse({
      code: 200,
      message: 'success',
      data: { taskId: 't-1', playerName: 'Steve', x: 10, y: 64, z: -5, modelUrl: 'http://x/models/t-1.json' },
    })
  })

  const data = await createBuild({ serverId: 'survival', x: 10, z: -5, playerName: 'Steve', prompt: '小木屋' })

  assert.equal(captured.url, '/api/map/build')
  assert.equal(captured.options.method, 'POST')
  assert.equal(captured.options.headers['Content-Type'], 'application/json')
  assert.deepEqual(JSON.parse(captured.options.body), {
    serverId: 'survival',
    x: 10,
    z: -5,
    playerName: 'Steve',
    prompt: '小木屋',
  })
  assert.equal(data.y, 64)
  assert.equal(data.taskId, 't-1')
})

test('createBuild 透传后端业务错误信息', async (t) => {
  mockFetch(t, async () =>
    jsonResponse({ code: 504, message: '游戏服务器响应超时' }),
  )

  await assert.rejects(
    () => createBuild({ x: 0, z: 0, playerName: 'Alex', prompt: '塔' }),
    (error) => {
      assert.ok(error instanceof MapBuildError)
      assert.equal(error.code, 504)
      assert.equal(error.message, '游戏服务器响应超时')
      return true
    },
  )
})

test('非 200 HTTP 状态映射为网络错误', async (t) => {
  mockFetch(t, async () => jsonResponse({}, false))

  await assert.rejects(
    () => createBuild({ x: 0, z: 0, playerName: 'Alex', prompt: '塔' }),
    (error) => error instanceof MapBuildError && error.message === NETWORK_ERROR,
  )
})

test('fetch 抛异常时映射为网络错误', async (t) => {
  mockFetch(t, async () => {
    throw new Error('boom')
  })

  await assert.rejects(
    () => fetchGameServers(),
    (error) => error instanceof MapBuildError && error.message === NETWORK_ERROR,
  )
})

test('fetchGameServers 兼容数组与 servers 包装两种返回', async (t) => {
  let body = [{ serverId: 'survival' }]
  mockFetch(t, async () => jsonResponse({ code: 200, data: body }))

  assert.deepEqual(await fetchGameServers(), [{ serverId: 'survival' }])

  body = { servers: [{ serverId: 'creative' }] }
  assert.deepEqual(await fetchGameServers(), [{ serverId: 'creative' }])
})

test('未知错误码回退到网络错误提示', async (t) => {
  mockFetch(t, async () => jsonResponse({ code: 418, message: '' }))

  await assert.rejects(
    () => fetchGameServers(),
    (error) => error instanceof MapBuildError && error.code === 418 && error.message === NETWORK_ERROR,
  )
})
