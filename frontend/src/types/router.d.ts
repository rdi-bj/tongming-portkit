declare module 'vue-router' {
  interface RouteMeta {
    /** Page title (i18n key) */
    title?: string
    /** Whether login is required, defaults to true */
    requiresLogin?: boolean
    /** Permission identifier */
    auth?: string[]
    /** Whether to generate a menu entry */
    menu?: boolean
    /** Menu order */
    menuSort?: number
    /** Menu icon */
    icon?: string
  }
}

export {}
