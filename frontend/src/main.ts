import { loadingFadeOut } from 'virtual:app-loading'
import { createApp } from 'vue'
import App from './App.vue'
import { router, setupPlugins } from './plugins'
import 'antdv-next/dist/reset.css'
import 'virtual:uno.css'
import '@/styles/global.scss'
import '@/styles/view-transition.scss'

const app = createApp(App)

void setupPlugins(app)

async function bootstrap() {
  await router.isReady()

  const appStore = useAppStoreHook()
  appStore.setDefaultLocale()
  appStore.mergeThemeToken()

  const routesStore = useRoutesStoreHook()
  routesStore.generateMenu()

  app.mount('#app')
  loadingFadeOut()
}

void bootstrap()
