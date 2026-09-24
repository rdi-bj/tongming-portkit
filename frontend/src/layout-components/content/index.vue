<script setup lang="ts">
import { useAppStore } from '@/stores/app'
import Breadcrumb from '../breadcrumb/index.vue'

const appStore = useAppStore()
const { config } = storeToRefs(appStore)
</script>

<template>
  <ALayoutContent class=":uno: px-10 py-8 size-full">
    <section
      :class="
        clsx(config.layout.contentWidth === 'fixed' ? 'max-w-1200px mx-auto' : 'w-full', 'h-full')
      "
    >
      <Breadcrumb v-if="config.layout.breadcrumb && config.layout.mode !== 'side'" class="mb-4" />
      <RouterView v-slot="{ Component, route }">
        <KeepAlive :key="route.path">
          <component :is="Component" />
        </KeepAlive>
      </RouterView>
    </section>
  </ALayoutContent>
</template>

<style lang="scss" scoped></style>
