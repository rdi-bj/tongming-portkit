import type { UserConfig } from 'vite'
import { AntdvNextResolver } from '@antdv-next/auto-import-resolver'
import vue from '@vitejs/plugin-vue'
import vueJsx from '@vitejs/plugin-vue-jsx'
import unocss from 'unocss/vite'
import autoImport from 'unplugin-auto-import/vite'
import turboConsole from 'unplugin-turbo-console/vite'
import components from 'unplugin-vue-components/vite'
import appLoading from 'vite-plugin-app-loading'
import vueDevTools from 'vite-plugin-vue-devtools'
import layouts from 'vite-plugin-vue-layouts'
import { ViteWebfontDownload } from 'vite-plugin-webfont-dl'
import { VueRouterAutoImports } from 'vue-router/unplugin'
import vueRouter from 'vue-router/vite'
import { resolvePath } from './utils'

export function createVitePlugins(env: ImportMetaEnv): UserConfig['plugins'] {
  return [
    // https://uvr.esm.is/
    vueRouter({
      dts: resolvePath('src/types/generated/typed-router.d.ts'),
      exclude: ['**/components/**'],
    }),
    // https://github.com/johncampionjr/vite-plugin-vue-layouts
    layouts({
      pagesDirs: resolvePath('src/pages'),
      layoutsDirs: resolvePath('src/layouts'),
    }),
    vue(),
    vueJsx(),
    // https://unocss.dev/
    unocss(),
    // https://unplugin.unjs.io/showcase/unplugin-vue-components.html
    components({
      resolvers: [AntdvNextResolver()],
      globs: [resolvePath('src/components/**/index.vue')],
      dts: resolvePath('src/types/generated/components.d.ts'),
    }),
    // https://unplugin.unjs.io/showcase/unplugin-auto-import.html
    autoImport({
      imports: [
        'vue',
        '@vueuse/core',
        'vue-i18n',
        'pinia',
        VueRouterAutoImports,
        {
          from: 'alova/client',
          imports: ['useRequest'],
        },
        {
          from: 'clsx',
          imports: [['default', 'clsx']],
        },
      ],
      dirs: [resolvePath('src/hooks'), resolvePath('src/stores')],
      dts: resolvePath('src/types/generated/auto-imports.d.ts'),
      vueTemplate: true,
      vueDirectives: true,
    }),
    // https://utc.yuy1n.io/
    turboConsole(),
    // https://devtools.vuejs.org/guide/vite-plugin
    env.VITE_ENABLE_VUE_DEVTOOLS === 'true' && vueDevTools(),
    // https://webfont-dl.feat.agency/
    ViteWebfontDownload([
      // Noto Sans SC
      'https://unpkg.com/@fontsource/noto-sans-sc@5.2.9/chinese-simplified-400.css',
      'https://unpkg.com/@fontsource/noto-sans-sc@5.2.9/chinese-simplified-700.css',
    ]),
    appLoading('loading.html'),
  ].filter(Boolean)
}
