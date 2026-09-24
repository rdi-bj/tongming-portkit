import type { ThemeConfig } from 'antdv-next'

export const darkThemeToken: Omit<ThemeConfig, 'algorithm'> = {
  token: {},
  components: {
    Layout: {
      siderBg: '#020203',
      headerBg: '#020203',
    },
    Menu: {
      darkItemBg: '#020203',
      darkSubMenuItemBg: '#020203',
    },
  },
}
