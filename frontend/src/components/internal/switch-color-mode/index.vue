<script setup lang="ts">
import type { DropdownProps, MenuItemType } from 'antdv-next'
import type { ColorMode } from '@/constants/app'
import { colorModeList } from '@/constants/app'

const appStore = useAppStore()
const { colorMode, isDark } = storeToRefs(appStore)
const { t } = useI18n()

const iconMap = new Map([
  ['dark', 'i-ant-design:moon-outlined'],
  ['light', 'i-ant-design:sun-outlined'],
  ['auto', 'i-ant-design:sync-outlined'],
])

const items = computed<MenuItemType[]>(() =>
  colorModeList.map(
    (item) =>
      ({
        icon: iconMap.get(item.value),
        key: item.value,
        label: t(item.label),
        disabled: item.value === colorMode.value,
      }) satisfies MenuItemType,
  ),
)

function handleViewTransition(cb: () => void) {
  if (document.startViewTransition) {
    document.startViewTransition(() => cb())
  } else {
    cb()
  }
}

const handleMenuClick: DropdownProps['onMenuClick'] = (item) => {
  handleViewTransition(() => appStore.setColorMode(item.key as ColorMode))
}
</script>

<template>
  <ADropdown :menu="{ items }" @menu-click="handleMenuClick">
    <template #iconRender="item">
      <RenderIcon :icon="item.icon" />
    </template>
    <AButton type="text" @click="handleViewTransition(appStore.toggleDarkMode)">
      <template #icon>
        <RenderIcon
          :icon="isDark ? 'i-ant-design:sun-outlined' : 'i-ant-design:moon-outlined'"
          class="scale-120"
        />
      </template>
    </AButton>
  </ADropdown>
</template>

<style lang="scss" scoped></style>
