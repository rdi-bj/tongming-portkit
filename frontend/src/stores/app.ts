import type {
  ColorMode,
  ContentWidth,
  LayoutMode,
  Locale,
  MenuColorMode,
  PrimaryColor,
} from '@/constants/app'
import dayjs from 'dayjs'
import { toMerged } from 'es-toolkit'
import { appConfig } from '@/config'
import { primaryColorList } from '@/constants/app'
import { i18n, store } from '@/plugins'
import { getCommonThemeToken } from '@/theme'
import 'dayjs/locale/zh-cn'
import 'dayjs/locale/en'

const dayjsLocaleMap = new Map([
  ['zh-CN', 'zh-cn'],
  ['en-US', 'en'],
])

export const useAppStore = defineStore('app', () => {
  // System configuration
  const config = useLocalStorage(
    `${import.meta.env.VITE_APP_STORAGE_PREFIX}app-config`,
    appConfig,
    {
      listenToStorageChanges: false,
    },
  )

  // Color mode
  const { system: preferredColorMode, store: colorMode } = useColorMode({
    attribute: 'class',
    initialValue: config.value.theme.colorMode,
    storageKey: `${import.meta.env.VITE_APP_STORAGE_PREFIX}color-mode`,
    listenToStorageChanges: false,
  })

  // Whether dark mode is active
  const isDark = computed(() =>
    colorMode.value === 'auto' ? preferredColorMode.value === 'dark' : colorMode.value === 'dark',
  )

  const themeToken = ref()

  // Get the theme token
  function mergeThemeToken() {
    const color = primaryColorList.find(
      (item) => item.value === config.value.theme.primaryColor,
    )?.color
    const token = getCommonThemeToken(isDark.value)
    themeToken.value = toMerged(token, { token: { colorPrimary: color } })
  }

  // Set the theme color
  function setPrimaryColor(color: PrimaryColor) {
    config.value.theme.primaryColor = color
    mergeThemeToken()
  }

  // Set the color mode
  function setColorMode(mode: ColorMode) {
    colorMode.value = mode
    config.value.theme.colorMode = mode
    mergeThemeToken()
  }

  // Toggle dark mode
  function toggleDarkMode() {
    colorMode.value = isDark.value ? 'light' : 'dark'
    config.value.theme.colorMode = colorMode.value
    mergeThemeToken()
  }

  const menuColorMode = computed(() => (isDark.value ? 'dark' : config.value.theme.menuColorMode))

  // Set the menu color mode
  function setMenuColorMode(mode: MenuColorMode) {
    config.value.theme.menuColorMode = mode
  }

  // Switch the locale
  function setLocale(locale: Locale) {
    config.value.locale = locale
    i18n.global.locale = locale
    dayjs.locale(dayjsLocaleMap.get(locale))
    if (document) {
      document.documentElement.lang = locale
    }
  }

  // Set the default locale
  function setDefaultLocale() {
    setLocale(config.value.locale)
  }

  // Toggle the sidebar collapsed state
  function toggleSiderCollapse() {
    config.value.layout.siderCollapsed = !config.value.layout.siderCollapsed
  }

  // Set the layout mode
  function setLayoutMode(mode: LayoutMode) {
    config.value.layout.mode = mode
  }

  // Set the content width
  function setContentWidth(contentWidth: ContentWidth) {
    config.value.layout.contentWidth = contentWidth
  }

  return {
    config,
    colorMode,
    isDark,
    setColorMode,
    toggleDarkMode,
    setLocale,
    setDefaultLocale,
    toggleSiderCollapse,
    themeToken,
    mergeThemeToken,
    setPrimaryColor,
    setLayoutMode,
    menuColorMode,
    setMenuColorMode,
    setContentWidth,
  }
})

export const useAppStoreHook = () => useAppStore(store)
