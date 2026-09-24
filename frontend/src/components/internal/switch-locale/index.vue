<script setup lang="ts">
import type { DropdownProps, MenuItemType } from 'antdv-next'
import type { Locale } from '@/constants/app'
import { localeList } from '@/constants/app'

defineOptions({
  name: 'SwitchLocale',
})

const appStore = useAppStore()
const { config } = storeToRefs(appStore)

const iconMap = new Map([
  ['zh-CN', 'i-circle-flags:lang-zh'],
  ['en-US', 'i-circle-flags:lang-en-us'],
])

const { t } = useI18n()

const items = computed<MenuItemType[]>(() =>
  localeList.map(
    (item) =>
      ({
        icon: iconMap.get(item.value),
        key: item.value,
        label: t(`common.${item.label}`),
        disabled: item.value === config.value.locale,
      }) satisfies MenuItemType,
  ),
)

const handleMenuClick: DropdownProps['onMenuClick'] = (info) => {
  appStore.setLocale(info.key as Locale)
  window.location.reload()
}
</script>

<template>
  <ADropdown :menu="{ items }" @menu-click="handleMenuClick">
    <template #iconRender="item">
      <RenderIcon :icon="item.icon" />
    </template>
    <AButton type="text">
      <template #icon>
        <RenderIcon icon="i-lucide:languages" class="scale-120" />
      </template>
    </AButton>
  </ADropdown>
</template>

<style lang="scss" scoped></style>
