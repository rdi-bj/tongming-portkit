import type {
  ColorMode,
  ContentWidth,
  LayoutMode,
  Locale,
  MenuColorMode,
  PrimaryColor,
} from '@/constants/app'

export interface AppConfig {
  /** Theme configuration */
  theme: Theme
  /** Layout configuration */
  layout: Layout
  /** Locale */
  locale: Locale
}

interface Theme {
  /** Color mode */
  colorMode: ColorMode
  /** Menu style */
  menuColorMode: MenuColorMode
  /** Theme color */
  primaryColor: PrimaryColor
}

interface Layout {
  /**
   * Layout mode
   * - side: sidebar layout
   * - header: top bar layout
   * - mix: mixed layout
   */
  mode: LayoutMode
  /** Whether the sidebar is collapsed */
  siderCollapsed: boolean
  /** Content width */
  contentWidth: ContentWidth
  /** Whether to show the logo */
  logo: boolean
  /** Whether to show the breadcrumb */
  breadcrumb: boolean
}
