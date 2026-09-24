import type { ThemeConfig } from 'antdv-next'
import { toMerged } from 'es-toolkit'
import { darkThemeToken } from './dark'
import { lightThemeToken } from './light'

const commonToken: Omit<ThemeConfig, 'algorithm'> = {
  token: {
    fontFamily: 'Noto Sans SC, PingFang SC, system-ui, sans-serif',
  },
}

export function getCommonThemeToken(isDark: boolean) {
  return toMerged(commonToken, isDark ? darkThemeToken : lightThemeToken)
}
