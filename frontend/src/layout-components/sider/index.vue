<script setup lang="ts">
import Menu from '../menu/index.vue'

const appStore = useAppStore()
const { config, menuColorMode, isDark } = storeToRefs(appStore)
const routesStore = useRoutesStore()

const hasChild = computed(() => !!routesStore.mixSideMenuList?.length)
</script>

<template>
  <ALayoutSider
    :class="
      clsx(
        ':uno: border-r z-900 ant-b-split sticky',
        config.layout.mode === 'side' ? 'h-screen top-0' : 'h-[calc(100vh-64px)] top-16',
      )
    "
    :theme="menuColorMode"
    :width="256"
    :collapsed-width="config.layout.mode === 'mix' && !hasChild ? 0 : 64"
    collapsible
    :collapsed="config.layout.siderCollapsed || (config.layout.mode === 'mix' && !hasChild)"
  >
    <div
      v-if="config.layout.mode === 'side' && config.layout.logo"
      class=":uno: flex-c h-[var(--ant-layout-header-height)] w-full"
    >
      <Logo
        :icon-only="config.layout.siderCollapsed"
        :class="
          clsx(
            menuColorMode === 'dark' && !isDark
              ? 'text-[var(--ant-layout-light-sider-bg)]'
              : 'ant-c-text',
          )
        "
      />
    </div>
    <div class=":uno: px-1 grow h-full overflow-y-auto">
      <Menu :split="config.layout.mode === 'mix'" />
    </div>
    <SwitchSiderCollapse v-if="config.layout.mode === 'mix'" class="bottom-3 right-4 absolute" />
  </ALayoutSider>
</template>

<style lang="scss" scoped>
:deep(.ant-layout-sider-children) {
  display: flex;
  flex-direction: column;
  height: 100%;
}
</style>
