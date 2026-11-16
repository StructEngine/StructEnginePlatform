import { test } from 'node:test'
import assert from 'node:assert/strict'
import { generateModel, GenerateError } from './generate.js'

function image(name = 'front.png') {
  return new File([new Uint8Array([0x89, 0x50, 0x4e, 0x47])], name, { type: 'image/png' })
}

function jsonResponse(body, status = 200) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'content-type': 'application/json' },
  })
}

test('提交参考图与补充说明', async (t) => {
  const originalFetch = globalThis.fetch
  let captured
  globalThis.fetch = async (url, options) => {
    captured = { url, options }
    return jsonResponse({ code: 200, message: 'ok', data: { json_url: 'http://platform/models/a.json' } })
  }
  t.after(() => { globalThis.fetch = originalFetch })

  const url = await generateModel([image()], '两层石砖小屋')

  assert.equal(url, 'http://platform/models/a.json')
  assert.equal(captured.url, '/api/generate')
  assert.equal(captured.options.method, 'POST')
  assert.equal(captured.options.body.getAll('images').length, 1)
  assert.equal(captured.options.body.get('description'), '两层石砖小屋')
})

test('补充说明为空时不发送 description 字段', async (t) => {
  const originalFetch = globalThis.fetch
  let captured
  globalThis.fetch = async (url, options) => {
    captured = { url, options }
    return jsonResponse({ code: 200, data: { json_url: 'http://platform/models/a.json' } })
  }
  t.after(() => { globalThis.fetch = originalFetch })

  await generateModel([image(), image('side.png')], '   ')

  assert.equal(captured.options.body.getAll('images').length, 2)
  assert.equal(captured.options.body.get('description'), null)
})

test('透传后端业务错误信息', async (t) => {
  const originalFetch = globalThis.fetch
  globalThis.fetch = async () => jsonResponse({
    code: 400,
    message: '请至少上传 1 张参考图（三视图只是建议，1~3 张均可）',
  })
  t.after(() => { globalThis.fetch = originalFetch })

  await assert.rejects(
    () => generateModel([image()]),
    (error) => error instanceof GenerateError
      && error.code === 400
      && error.message.includes('三视图只是建议'),
  )
})

test('HTTP 错误且无 JSON body 时使用状态码兜底文案', async (t) => {
  const originalFetch = globalThis.fetch
  globalThis.fetch = async () => new Response('bad gateway', { status: 502 })
  t.after(() => { globalThis.fetch = originalFetch })

  await assert.rejects(
    () => generateModel([image()]),
    (error) => error instanceof GenerateError
      && error.code === 502
      && error.message === '上游推理失败',
  )
})
