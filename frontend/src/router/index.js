import { createRouter, createWebHistory } from 'vue-router'
import { useAuth } from '../stores/auth'

const routes = [
  {
    path: '/login',
    name: 'login',
    component: () => import('../views/LoginPage.vue'),
    meta: { public: true, title: '登录' },
  },
  {
    path: '/register',
    name: 'register',
    component: () => import('../views/RegisterPage.vue'),
    meta: { public: true, title: '注册' },
  },
  {
    path: '/change-password',
    name: 'change-password',
    component: () => import('../views/ChangePasswordPage.vue'),
    meta: { title: '修改密码' },
  },
  {
    path: '/servers',
    name: 'servers',
    component: () => import('../views/ServersPlaceholderPage.vue'),
    meta: { title: '我的服务器', subtitle: '服务器管理将在 Phase 2 开放' },
  },
  {
    path: '/admin/users',
    name: 'admin-users',
    component: () => import('../views/AdminUsersPage.vue'),
    meta: { admin: true, title: '用户管理', subtitle: '管理员账号与 Minecraft 服主账号' },
  },
  {
    path: '/build',
    name: 'build',
    component: () => import('../views/BuildStudioPage.vue'),
    meta: {
      admin: true,
      title: 'StructEngine AI 地图建造',
      subtitle: 'BlueMap 选点 · 玩家选择 · AI 模型生成 · 部署与回滚',
    },
  },
  {
    path: '/studio',
    name: 'studio',
    component: () => import('../views/GeneratePage.vue'),
    meta: {
      admin: true,
      title: 'StructEngine 参考图生成器',
      subtitle: '上传 1~3 张参考图，多模态 AI 推断生成 Minecraft 体素模型',
    },
  },
  {
    path: '/',
    redirect: '/servers',
  },
  {
    path: '/:pathMatch(.*)*',
    redirect: '/servers',
  },
]

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes,
  scrollBehavior: () => ({ top: 0 }),
})

router.beforeEach(async (to) => {
  const auth = useAuth()
  await auth.ensureReady()

  if (to.meta.public) {
    if (auth.state.token) {
      return auth.state.user?.mustChangePassword
        ? '/change-password'
        : auth.defaultRoute.value
    }
    return true
  }

  if (!auth.state.token) {
    return { path: '/login', query: { redirect: to.fullPath } }
  }

  if (auth.state.user?.mustChangePassword && to.path !== '/change-password') {
    return '/change-password'
  }

  if (to.meta.admin && !auth.isAdmin.value) {
    return '/servers'
  }

  if (to.path === '/') {
    return auth.defaultRoute.value
  }

  return true
})

export default router
