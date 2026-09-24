import type { ThemeConfig } from 'antdv-next'

export const lightThemeToken: Omit<ThemeConfig, 'algorithm'> = {
  token: {
    colorBgLayout: '#f5f5f5',
  },
  components: {
    Layout: {
      headerBg: '#ffffff',
    },
    Menu: {
      subMenuItemBg: '#ffffff',
    },
  },
}
