<script setup lang="ts">
import type { MenuItemType, MenuProps } from 'antdv-next'

const props = defineProps<{
  split?: boolean
  isTop?: boolean
}>()

const routesStore = useRoutesStore()
const { menuList } = storeToRefs(routesStore)

const appStore = useAppStore()
const { config, menuColorMode } = storeToRefs(appStore)

const { t } = useI18n()

const route = useRoute()
const router = useRouter()

const onMenuSelect: MenuProps['onSelect'] = (item) => {
  // Clicking a top-level menu item in mixed mode
  if (props.split && props.isTop) {
    routesStore.getChildMenu(item.key)
  }
  router.push(item.key)
}

const openKeys = ref<string[]>([])

watch(
  () => [route.path, config.value?.layout?.siderCollapsed],
  () => {
    const pathList = routesStore.getRoutePath(route.path).map((item) => item.path)
    if (!pathList.length) return
    pathList.pop()
    if (props.isTop) {
      openKeys.value = []
      return
    }
    // Non-accordion mode: merge paths so already-open submenus stay open
    const merged = new Set([...openKeys.value, ...pathList])
    openKeys.value = [...merged]
  },
  {
    immediate: true,
    deep: true,
  },
)

const menuItems = computed<MenuItemType[]>(() => {
  // Whether to split (in mixed mode)
  if (props.split) {
    // Top-level menu, first level only
    if (props.isTop) {
      return menuList.value.map(
        (item) =>
          ({
            ...item,
            children: undefined,
          }) as MenuItemType,
      )
    }
    // Sidebar menu of mixed mode
    return routesStore.mixSideMenuList
  }
  return menuList.value
})

const selectedKeys = computed<string[]>(() => {
  const path = route.path
  const matched: string[] = []
  const collect = (items: MenuItemType[]) => {
    for (const item of items) {
      // antdv's ItemType union includes null (dividers may also be empty slots)
      if (!item) continue
      const key = item.key as string
      if (key && (path === key || path.startsWith(`${key}/`))) {
        matched.push(key)
      }
      // Only sub-menus and groups carry children
      if ('children' in item && item.children) {
        collect(item.children as MenuItemType[])
      }
    }
  }
  collect(menuItems.value)
  return matched.length ? matched : [path]
})

// Clicking a top-level menu item in mixed mode
if (props.split && props.isTop) {
  routesStore.getChildMenu(selectedKeys.value[0])
}
</script>

<template>
  <AMenu
    v-model:open-keys="openKeys"
    :items="menuItems"
    :mode="props.isTop ? 'horizontal' : 'inline'"
    :theme="menuColorMode"
    :style="{
      border: 'none',
    }"
    :inline-indent="16"
    :selected-keys
    @select="onMenuSelect"
  >
    <template #labelRender="item">
      <span>{{ t(item.label) }}</span>
    </template>
    <template #iconRender="item">
      <RenderIcon :icon="item.icon" class="size-4"></RenderIcon>
    </template>
  </AMenu>
</template>

<style lang="scss" scoped></style>
