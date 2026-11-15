/**
 * Squaremap 坐标与 Leaflet 坐标的互转工具。
 *
 * Squaremap 的 Web 端使用 `L.CRS.Simple`，并把「最大原生缩放」当作基准：
 *   scale = 1 / 2^maxZoom
 *   latlng = (-z * scale, x * scale)
 * 因此反向换算必须同样以 maxZoom 为基准，不能使用当前地图缩放级别，
 * 否则点击拾取的坐标会随滚轮缩放而漂移。
 */

export const DEFAULT_MAX_ZOOM = 3
export const DEFAULT_DEF_ZOOM = 0
export const DEFAULT_EXTRA_ZOOM = 2
export const DEFAULT_TILE_SIZE = 512

function toInt(value, fallback) {
  const num = Number(value)
  if (!Number.isFinite(num)) {
    return fallback
  }
  return Math.trunc(num)
}

function clamp(value, min, max) {
  return Math.min(max, Math.max(min, value))
}

/**
 * 创建某个世界的坐标投影器。
 *
 * @param {number} maxZoom Squaremap 世界 settings.json 中的 zoom.max
 */
export function createProjection(maxZoom = DEFAULT_MAX_ZOOM) {
  const baseZoom = clamp(toInt(maxZoom, DEFAULT_MAX_ZOOM), 0, 8)
  const scale = 1 / 2 ** baseZoom

  return {
    maxZoom: baseZoom,
    scale,
    /** Minecraft 方块坐标 → Leaflet [lat, lng]。 */
    toLatLng(x, z) {
      return [-z * scale, x * scale]
    },
    /** Leaflet latlng → Minecraft 方块坐标（向下取整）。 */
    toBlock(latlng) {
      return {
        x: Math.floor(latlng.lng / scale),
        z: Math.floor(-latlng.lat / scale),
      }
    },
  }
}

/** 去掉末尾斜杠，兼容 `/squaremap` 这类相对地址。 */
export function normalizeBaseUrl(url) {
  if (typeof url !== 'string') {
    return ''
  }
  return url.trim().replace(/\/+$/, '')
}

/** 生成 Leaflet 瓦片模板；Squaremap 的瓦片固定为 512px。 */
export function tileUrlTemplate(baseUrl, world) {
  const base = normalizeBaseUrl(baseUrl)
  const worldName = encodeURIComponent(world || 'world')
  return `${base}/tiles/${worldName}/{z}/{x}_{y}.png`
}

/** Squaremap settings.json 地址。 */
export function settingsUrl(baseUrl, world) {
  const base = normalizeBaseUrl(baseUrl)
  const worldName = encodeURIComponent(world || 'world')
  return `${base}/tiles/${worldName}/settings.json`
}

/**
 * 解析世界 settings.json，字段缺失时回退到 Squaremap 默认值。
 * 返回值中的 maxZoom 为「原生最大缩放」，地图 maxZoom 需再加上 extraZoom。
 */
export function parseSquaremapSettings(raw, fallback = {}) {
  const zoom = raw && typeof raw === 'object' ? raw.zoom || {} : {}
  const spawn = raw && typeof raw === 'object' ? raw.spawn || {} : {}

  const maxZoom = clamp(
    toInt(zoom.max, fallback.maxZoom ?? DEFAULT_MAX_ZOOM),
    0,
    8,
  )
  const extraZoom = clamp(
    toInt(zoom.extra, fallback.extraZoom ?? DEFAULT_EXTRA_ZOOM),
    0,
    4,
  )
  const defZoom = clamp(
    toInt(zoom.def, fallback.defZoom ?? DEFAULT_DEF_ZOOM),
    0,
    maxZoom + extraZoom,
  )

  return {
    maxZoom,
    extraZoom,
    defZoom,
    spawn: {
      x: toInt(spawn.x, 0),
      z: toInt(spawn.z, 0),
    },
  }
}

/** 统一坐标展示文本，例如 `X: 12, Y: 64, Z: -30`。 */
export function formatBlockCoordinates(x, y, z) {
  return `X: ${toInt(x, 0)}, Y: ${toInt(y, 0)}, Z: ${toInt(z, 0)}`
}
