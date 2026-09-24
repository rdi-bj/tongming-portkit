<script setup lang="ts">
import Breadcrumb from '../breadcrumb/index.vue'
import Menu from '../menu/index.vue'
import UserProfile from './user-profile/index.vue'

const appStore = useAppStore()
const { config, menuColorMode, isDark } = storeToRefs(appStore)
</script>

<template>
  <ALayoutHeader
    :class="
      clsx(
        ':uno: border-b flex-bc left-0 top-0 sticky z-900 border-b-split',
        menuColorMode === 'dark' && !isDark
          ? 'bg-[var(--ant-layout-color-bg-header)]'
          : 'bg-[var(--ant-layout-header-bg)]',
      )
    "
  >
    <div
      class=":uno: flex-c gap-1 h-full"
      :class="menuColorMode === 'dark' && !isDark && 'dark-mode'"
    >
      <!-- Collapse the sidebar -->
      <SwitchSiderCollapse v-if="config.layout.mode === 'side'" />
      <Logo
        v-if="config.layout.mode !== 'side' && config.layout.logo"
        :class="
          clsx(
            'w-256px',
            menuColorMode === 'dark' && !isDark
              ? 'text-[var(--ant-layout-light-sider-bg)]'
              : 'ant-c-text',
          )
        "
      />
      <Breadcrumb v-if="config.layout.breadcrumb && config.layout.mode === 'side'" class="ml-2" />
    </div>
    <Menu
      v-if="config.layout.mode !== 'side'"
      :split="config.layout.mode === 'mix'"
      is-top
      :class="clsx('grow', !config.layout.logo && 'pl-4')"
      mode="horizontal"
    />
    <div
      class=":uno: flex-c shrink-0 gap-1 h-full"
      :class="menuColorMode === 'dark' && !isDark && 'dark-mode'"
    >
      <SwitchLocale />
      <SwitchColorMode />
      <UserProfile />
    </div>
  </ALayoutHeader>
</template>

<style lang="scss" scoped>
.dark-mode {
  :deep(.ant-btn) {
    --ant-button-text-text-color: var(--ant-layout-light-sider-bg);
    opacity: 0.8;

    &:hover {
      --ant-btn-text-color-hover: var(--ant-layout-light-sider-bg);
    }
  }
}
</style>
