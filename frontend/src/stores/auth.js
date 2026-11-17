import { computed, reactive } from 'vue'
import { clearToken, getToken, request, setToken } from '../api/http.js'

const state = reactive({
  token: getToken(),
  user: null,
  ready: false,
  loading: false,
})

function applyToken(data) {
  setToken(data.token)
  state.token = data.token
  state.user = data.user
  state.ready = true
}

async function ensureReady() {
  if (state.ready) {
    return
  }
  if (!state.token) {
    state.ready = true
    return
  }
  try {
    state.user = await request('/api/auth/me')
  } catch (e) {
    clearToken()
    state.token = null
    state.user = null
  } finally {
    state.ready = true
  }
}

async function login(payload) {
  state.loading = true
  try {
    const data = await request('/api/auth/login', {
      method: 'POST',
      body: JSON.stringify(payload),
    })
    applyToken(data)
    return data
  } finally {
    state.loading = false
  }
}

async function register(payload) {
  state.loading = true
  try {
    const data = await request('/api/auth/register', {
      method: 'POST',
      body: JSON.stringify(payload),
    })
    applyToken(data)
    return data
  } finally {
    state.loading = false
  }
}

async function changePassword(payload) {
  const data = await request('/api/auth/password', {
    method: 'POST',
    body: JSON.stringify(payload),
  })
  applyToken(data)
  return data
}

function logout() {
  clearToken()
  state.token = null
  state.user = null
  state.ready = true
}

const isAdmin = computed(() => state.user?.role === 'ADMIN')
const defaultRoute = computed(() => (isAdmin.value ? '/admin/users' : '/servers'))

export function useAuth() {
  return {
    state,
    isAdmin,
    defaultRoute,
    ensureReady,
    login,
    register,
    changePassword,
    logout,
  }
}

export { state as authState, isAdmin, defaultRoute }
