interface ViteTypeOptions {
  strictImportMetaEnv: unknown
}

interface ImportMetaEnv {
  /** Application title */
  readonly VITE_APP_TITLE: string
  /** Storage key prefix */
  readonly VITE_APP_STORAGE_PREFIX: string
  /** Deployment base path (must start and end with /) */
  readonly VITE_APP_BASE_PATH: string
  /** API path prefix (without the base path), shared by requests and the dev proxy */
  readonly VITE_API_BASE_PATH: string
  /** Backend origin (scheme://host:port), used only as the dev server proxy target */
  readonly VITE_API_BASE_URL: string
  /** Whether to enable Vue devtools */
  readonly VITE_ENABLE_VUE_DEVTOOLS: 'true' | string
  /** Whether to enable source maps */
  readonly VITE_BUILD_SOURCEMAP: 'true' | string
}

interface ImportMeta {
  readonly env: ImportMetaEnv
}
