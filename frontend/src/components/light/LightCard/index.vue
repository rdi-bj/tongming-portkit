<script setup lang="ts">
import { computed } from 'vue'

const props = withDefaults(
  defineProps<{
    padding?: string
    bordered?: boolean
    shadow?: boolean
  }>(),
  {
    padding: '30px 25px',
    bordered: false,
    shadow: true,
  },
)

const appStore = useAppStore()

const cardStyle = computed(() => ({
  padding: props.padding,
  boxShadow: props.shadow
    ? appStore.isDark
      ? '0px 4px 32px 0px rgba(0, 0, 0, 0.3)'
      : '0px 4px 32px 0px rgba(160, 175, 222, 0.2), inset 0px 1px 0px 0px #ffffff'
    : 'none',
  border: props.bordered ? (appStore.isDark ? '1px solid #2a3050' : '1px solid #ebeef5') : 'none',
}))
</script>

<template>
  <div class="light-card" :style="cardStyle">
    <slot />
  </div>
</template>

<style lang="scss" scoped>
.light-card {
  box-sizing: border-box;
  background: #f9fbff;
  border-radius: 12px;
}
</style>

<style lang="scss">
.dark .light-card {
  background: #1a1f2e;
}
</style>
