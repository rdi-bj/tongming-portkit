import type { MenuItemType } from 'antdv-next'
import type { RouteRecordRaw } from 'vue-router'
import { cloneDeep } from 'es-toolkit'
import { routes } from 'vue-router/auto-routes'
import { router, store } from '@/plugins'

export const useRoutesStore = defineStore('routes', () => {
  const routeTree = ref<RouteRecordRaw[]>([])
  const menuList = ref<MenuItemType[]>([])

  // Build the menu
  function generateMenu() {
    routeTree.value = convertToRouteTree(routes)
    menuList.value = convertToMenu(routeTree.value)
  }

  // Process routes
  function convertToRouteTree(routes: RouteRecordRaw[], parentPath = ''): RouteRecordRaw[] {
    return cloneDeep(routes)
      .map((route) => {
        if (!route) return route
        // Work out the full path of the current route
        const fullPath = route.path.startsWith('/')
          ? route.path
          : `${parentPath}${parentPath.endsWith('/') ? '' : '/'}${route.path}`
        // Process the current route
        const processedRoute = { ...route, path: fullPath }
        // Recurse when the route has children
        if (processedRoute.children && processedRoute.children.length > 0) {
          // Process the child routes
          const processedChildren: RouteRecordRaw[] = []
          processedRoute.children.forEach((child) => {
            if (!child) return
            // Recurse into the child routes
            const flattenedChild = convertToRouteTree([child], processedRoute.path)[0]
            if (child.path === '') {
              // A child with an empty path is merged into its parent
              Object.assign(processedRoute, {
                name: flattenedChild?.name || processedRoute.name,
                redirect: flattenedChild?.redirect || processedRoute.redirect,
                meta: { ...processedRoute.meta, ...flattenedChild?.meta },
              })
              // Recurse when it has children, then add it to processedChildren
              if (flattenedChild?.children && flattenedChild.children.length > 0) {
                processedChildren.push(...flattenedChild.children)
              }
            } else {
              // Otherwise add the processed child directly
              if (flattenedChild) processedChildren.push(flattenedChild)
            }
          })
          // Update the children of the parent route
          processedRoute.children = processedChildren.length > 0 ? processedChildren : undefined
        }
        return processedRoute
      })
      .filter(Boolean)
  }

  const userStore = useUserStore()

  // Build the menu recursively
  function convertToMenu(routes: RouteRecordRaw[]): MenuItemType[] {
    return routes
      .filter((route) => {
        // Is this a menu entry?
        if (route.meta?.menu) {
          // Does the user have permission?
          if (route.meta?.auth?.length) {
            return userStore.hasPermission(route.meta.auth)
          }
          return true
        }
        return false
      })
      .sort((a, b) => (a.meta?.menuSort || 0) - (b.meta?.menuSort || 0))
      .map((route) => {
        const children = route.children?.length ? convertToMenu(route.children) : undefined
        // Include the children in the route
        if (route.children?.length) {
          // Has visible children → render as submenu
          if (children?.length) {
            return {
              type: 'submenu',
              key: route.path,
              label: (route.meta?.title as string) ?? 'common.unnamed-page',
              icon: (route.meta?.icon as string) ?? '',
              children,
            } as MenuItemType
          }
          // No visible child → demote the parent to a standalone menu item (fall through)
        }
        return {
          type: 'item',
          key: route.path,
          label: (route.meta?.title as string) ?? 'common.unnamed-page',
          icon: (route.meta?.icon as string) ?? '',
        } as MenuItemType
      })
      .filter(Boolean)
  }

  // Collect route paths through router.resolve (supports dynamic route matching)
  function getRoutePath(targetPath: string): RouteRecordRaw[] {
    try {
      const resolved = router.resolve(targetPath)
      const matched = resolved?.matched ?? []
      // Filter out layout wrapper routes (isLayout entries without a title)
      return matched.filter(
        (r) =>
          !(r.meta as Record<string, unknown>)?.isLayout &&
          (r.meta as Record<string, unknown>)?.title,
      ) as RouteRecordRaw[]
    } catch {
      return []
    }
  }

  // Sidebar submenu of mixed mode
  const mixSideMenuList = ref<MenuItemType[]>([])

  // Get the submenu
  function getChildMenu(path: string) {
    // @ts-expect-error unknown type item
    mixSideMenuList.value = menuList.value.find((item) => item.key === path)?.children ?? []
  }

  return { generateMenu, getRoutePath, menuList, getChildMenu, mixSideMenuList }
})

export const useRoutesStoreHook = () => useRoutesStore(store)
