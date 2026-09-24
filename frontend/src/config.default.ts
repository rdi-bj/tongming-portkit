import type { AppConfig } from '#/app'

export const defaultAppConfig: AppConfig = {
  theme: {
    colorMode: 'auto',
    primaryColor: 'default',
    menuColorMode: 'light',
  },
  layout: {
    mode: 'side',
    siderCollapsed: false,
    contentWidth: 'fluid',
    logo: true,
    breadcrumb: true,
  },
  locale: 'zh-CN',
}
