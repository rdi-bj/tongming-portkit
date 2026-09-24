import type { ProxyOptions } from 'vite'
import * as process from 'node:process'
import { defineConfig, loadEnv } from 'vite'
import { createPostcssConfig, createVitePlugins, resolvePath } from './build'

// The proxy key is the API prefix itself: the prefix is an absolute path on the origin (e.g. /tongming-portkit-api)
// and is independent of the frontend base path, so base must not (and need not) be concatenated here.
function escapeRegExp(s: string) {
  return s.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')
}

// https://vite.dev/config/
export default defineConfig(({ mode }) => {
  console.log(`current mode: ${mode}`)

  const env = loadEnv(mode, process.cwd()) as unknown as ImportMetaEnv

  const appBase = env.VITE_APP_BASE_PATH
  const apiPrefix = env.VITE_API_BASE_PATH.replace(/\/+$/, '')

  return {
    base: appBase,
    server: {
      port: 5500,
      host: true,
      proxy: {
        [apiPrefix]: {
          target: env.VITE_API_BASE_URL,
          changeOrigin: true,
          ws: true,
          rewrite: (path) => path.replace(new RegExp(`^${escapeRegExp(apiPrefix)}`), ''),
        } satisfies ProxyOptions,
      },
    },
    resolve: {
      alias: {
        '@': resolvePath('src'),
        '#': resolvePath('src/types'),
      },
      tsconfigPaths: true,
    },
    plugins: createVitePlugins(env),
    css: {
      postcss: createPostcssConfig(),
    },
    build: {
      chunkSizeWarningLimit: 3000,
      reportCompressedSize: false,
      sourcemap: env.VITE_BUILD_SOURCEMAP === 'true',
    },
    optimizeDeps: {
      include: ['@iconify-json/ant-design', '@iconify-json/lucide'],
      exclude: ['monaco-editor'],
    },
  }
})
