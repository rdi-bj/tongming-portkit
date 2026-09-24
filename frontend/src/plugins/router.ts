import type { App } from 'vue'
import type { RouteRecordRaw } from 'vue-router'
import { setupLayouts } from 'virtual:generated-layouts'
import { createRouter, createWebHistory } from 'vue-router'
import { handleHotUpdate, routes } from 'vue-router/auto-routes'
import { useAppStoreHook } from '@/stores/app'
import { i18n } from './i18n'
import { progressBar } from './progress-bar'

function addDefaultMeta(routes: readonly RouteRecordRaw[]) {
  return routes
    .map((item) => {
      if (!item) return item as RouteRecordRaw
      item.meta ??= {}
      item.meta.requiresLogin = item.meta.requiresLogin ?? true
      if (item.children) {
        item.children = addDefaultMeta(item.children).filter(Boolean)
      }
      return item
    })
    .filter(Boolean)
}

const extendedRoutes = setupLayouts(addDefaultMeta(routes))

export const router = createRouter({
  history: createWebHistory(import.meta.env.VITE_APP_BASE_PATH),
  routes: extendedRoutes,
})

if (import.meta.hot) {
  handleHotUpdate(router)
}

router.beforeEach((to, _from) => {
  progressBar.start()
  const userStore = useUserStoreHook()
  // Already logged in but visiting the login page: redirect to the home page
  if (userStore.isLogin && to.path === '/login') {
    return '/'
  }

  // No permission: redirect to the no-permission page
  if (to.meta.auth && !userStore.hasPermission(to.meta.auth)) {
    return { path: '/error/403', replace: true }
  }

  // Not logged in and visiting a non-public page: redirect to the login page
  if (to.meta.requiresLogin && !userStore.isLogin) {
    return { path: '/login', query: { redirect: to.fullPath }, replace: true }
  }

  // Set the page title
  const appStore = useAppStoreHook()
  const { t } = i18n.global
  const title = to.meta.title
    ? `${t(to.meta.title, {}, { locale: appStore.config.locale })} - `
    : ''
  useTitle(title + t('app.title', {}, { locale: appStore.config.locale }))

  return true
})

router.afterEach(() => {
  progressBar.done()
})

export function setupRouter(app: App) {
  app.use(router)
}
