import type { App } from 'vue'
import { setupI18n } from './i18n'
import { setupRouter } from './router'
import { setupStore } from './store'

export async function setupPlugins(app: App) {
  setupStore(app)
  const userStore = useUserStore()
  if (userStore.isLogin) {
    // Fetch user permissions
    await userStore.getPermissionList()
  }
  setupI18n(app)
  setupRouter(app)
}

export { default as echarts, type EChartsOption, type EChartsType } from './echarts'
export { i18n } from './i18n'
export * from './progress-bar'
export { router } from './router'
export { store } from './store'
