<script setup lang="ts">
const appStore = useAppStore()
const { config } = storeToRefs(appStore)
</script>

<template>
  <AButton type="text" @click="appStore.toggleSiderCollapse">
    <template #icon>
      <RenderIcon
        icon="i-ant-design:menu-fold-outlined"
        :class="clsx('size-5 transition', config.layout.siderCollapsed ? 'scale-x--100' : '')"
      />
    </template>
  </AButton>
</template>

<style lang="scss" scoped></style>
