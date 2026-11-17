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
    component: () => import('../views/ServersPage.vue'),
    meta: { title: '服务器管理', subtitle: '创建服务器、获取 secret、管理插件注册与在线状态' },
  },
  {
    path: '/servers/:serverId',
    name: 'server-detail',
    component: () => import('../views/ServerDetailPage.vue'),
    meta: { title: '服务器详情', subtitle: 'AI 地图建造、参考图生成、模型产物与构建任务' },
  },
  {
    path: '/admin/users',
    name: 'admin-users',
    component: () => import('../views/AdminUsersPage.vue'),
    meta: { admin: true, title: '用户管理', subtitle: '管理员账号与 Minecraft 服主账号' },
  },
  {
    path: '/admin/models',
    name: 'admin-models',
    component: () => import('../views/AdminModelsPage.vue'),
    meta: { admin: true, title: '全部模型产物', subtitle: '管理员可查看与删除全部模型' },
  },
  {
    path: '/admin/audit',
    name: 'admin-audit',
    component: () => import('../views/AdminAuditPage.vue'),
    meta: { admin: true, title: '审计日志', subtitle: '服务器、模型、部署与回滚操作记录' },
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
