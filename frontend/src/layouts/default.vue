<script setup lang="ts">
import type { Component } from 'vue'
import type { LayoutMode } from '@/constants/app'
import Mix from '../layout-blocks/mix.vue'
import Side from '../layout-blocks/side.vue'
import Top from '../layout-blocks/top.vue'

const appStore = useAppStore()
const { config } = storeToRefs(appStore)

const isDev = import.meta.env.DEV

const layoutMap = new Map<LayoutMode, Component>([
  ['side', Side],
  ['mix', Mix],
  ['top', Top],
])
</script>

<template>
  <ConfigPanel v-if="isDev" />
  <component :is="layoutMap.get(config.layout.mode) ?? Side" />
  <AFloatBackTop />
</template>

<style lang="scss" scoped></style>
