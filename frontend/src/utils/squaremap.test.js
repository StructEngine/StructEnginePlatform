import { test } from 'node:test'
import assert from 'node:assert/strict'

import {
  createProjection,
  formatBlockCoordinates,
  normalizeBaseUrl,
  parseSquaremapSettings,
  settingsUrl,
  tileUrlTemplate,
} from './squaremap.js'

test('坐标换算以 maxZoom 为基准', () => {
  const projection = createProjection(3)
  assert.equal(projection.scale, 1 / 8)
  assert.deepEqual(projection.toLatLng(8, -16), [2, 1])
  assert.deepEqual(projection.toBlock({ lat: 2, lng: 1 }), { x: 8, z: -16 })
})

test('点击负坐标时向下取整而不是向零截断', () => {
  const projection = createProjection(3)
  assert.deepEqual(projection.toBlock({ lat: 0.001, lng: -0.001 }), { x: -1, z: -1 })
  assert.deepEqual(projection.toBlock({ lat: -0.001, lng: 0.001 }), { x: 0, z: 0 })
})

test('方块中心与边界拾取稳定', () => {
  const projection = createProjection(4)
  const [lat, lng] = projection.toLatLng(123, -456)
  assert.deepEqual(projection.toBlock({ lat, lng }), { x: 123, z: -456 })
  const latlng = projection.toLatLng(10, 20)
  assert.deepEqual(
    projection.toBlock({ lat: latlng[0] - 0.001, lng: latlng[1] + 0.001 }),
    { x: 10, z: 20 },
  )
})

test('异常 maxZoom 被夹到安全范围', () => {
  assert.equal(createProjection(-5).maxZoom, 0)
  assert.equal(createProjection(99).maxZoom, 8)
  assert.equal(createProjection('bad').maxZoom, 3)
})

test('URL 规范化与瓦片模板', () => {
  assert.equal(normalizeBaseUrl('http://map.local:8123///'), 'http://map.local:8123')
  assert.equal(normalizeBaseUrl(null), '')
  assert.equal(
    tileUrlTemplate('http://map.local:8123/', 'world'),
    'http://map.local:8123/tiles/world/{z}/{x}_{y}.png',
  )
  assert.equal(
    settingsUrl('/squaremap', 'world_nether'),
    '/squaremap/tiles/world_nether/settings.json',
  )
})

test('解析 settings.json 并夹取缩放范围', () => {
  const settings = parseSquaremapSettings({
    zoom: { max: 4, def: 2, extra: 1 },
    spawn: { x: 100, z: -200 },
  })
  assert.deepEqual(settings, {
    maxZoom: 4,
    extraZoom: 1,
    defZoom: 2,
    spawn: { x: 100, z: -200 },
  })
})

test('settings.json 缺字段时使用默认值', () => {
  const settings = parseSquaremapSettings(null)
  assert.equal(settings.maxZoom, 3)
  assert.equal(settings.extraZoom, 2)
  assert.equal(settings.defZoom, 0)
  assert.deepEqual(settings.spawn, { x: 0, z: 0 })
})

test('非法 def 缩放被夹到可视范围', () => {
  const settings = parseSquaremapSettings({ zoom: { max: 2, def: 99, extra: 1 } })
  assert.equal(settings.defZoom, 3)
})

test('坐标展示包含完整三维信息', () => {
  assert.equal(formatBlockCoordinates(1, 64, -2), 'X: 1, Y: 64, Z: -2')
})
