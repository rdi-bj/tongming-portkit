// Color mode
export const colorModeList = [
  {
    // Dark mode
    label: 'app.color-mode.dark',
    value: 'dark',
  },
  {
    // Light mode
    label: 'app.color-mode.light',
    value: 'light',
  },
  {
    // Follow system
    label: 'app.color-mode.auto',
    value: 'auto',
  },
] as const
export type ColorMode = (typeof colorModeList)[number]['value']

// Layout mode
export const layoutModeList = [
  {
    label: 'app.layout.side',
    value: 'side',
  },
  {
    label: 'app.layout.top',
    value: 'top',
  },
  {
    label: 'app.layout.mix',
    value: 'mix',
  },
] as const
export type LayoutMode = (typeof layoutModeList)[number]['value']

// Locales
export const localeList = [
  {
    label: 'locale_zh_CN',
    value: 'zh-CN',
  },
  {
    label: 'locale_en_US',
    value: 'en-US',
  },
] as const
export type Locale = (typeof localeList)[number]['value']

// Theme colors
export const primaryColorList = [
  {
    label: 'app.primary-color.default',
    color: '#1677ff',
    value: 'default',
  },
  {
    label: 'app.primary-color.awake',
    color: '#1890ff',
    value: 'awake',
  },
  {
    label: 'app.primary-color.mourning',
    color: '#f5222d',
    value: 'mourning',
  },
  {
    label: 'app.primary-color.volcano',
    color: '#fa541c',
    value: 'volcano',
  },
  {
    label: 'app.primary-color.sunset',
    color: '#faad14',
    value: 'sunset',
  },
  {
    label: 'app.primary-color.morning',
    color: '#13c2c2',
    value: 'morning',
  },
  {
    label: 'app.primary-color.green',
    color: '#52c41a',
    value: 'green',
  },
  {
    label: 'app.primary-color.geek',
    color: '#2f54eb',
    value: 'geek',
  },
  {
    label: 'app.primary-color.purple',
    color: '#722ed1',
    value: 'purple',
  },
] as const
export type PrimaryColor = (typeof primaryColorList)[number]['value']

// Menu color mode
export const menuColorModeList = [
  {
    // Dark mode
    label: 'app.color-mode.dark',
    value: 'dark',
  },
  {
    // Light mode
    label: 'app.color-mode.light',
    value: 'light',
  },
] as const
export type MenuColorMode = (typeof menuColorModeList)[number]['value']

// Content width
export const contentWidthList = [
  {
    // Fluid
    label: 'app.content-width.fluid',
    value: 'fluid',
  },
  {
    // Fixed
    label: 'app.content-width.fixed',
    value: 'fixed',
  },
] as const
export type ContentWidth = (typeof contentWidthList)[number]['value']
