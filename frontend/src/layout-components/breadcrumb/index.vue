<script setup lang="ts">
import type { BreadcrumbItemType } from 'antdv-next'

const routesStore = useRoutesStore()
const route = useRoute()
const { t } = useI18n()

const breadcrumbList = computed<BreadcrumbItemType[]>(() => {
  const routePath = routesStore.getRoutePath(route.path)
  return routePath
    .filter((r) => {
      // Filter out layout wrapper routes and routes without a title
      const meta = r.meta as Record<string, unknown> | undefined
      if (!meta) return false
      if (meta.isLayout) return false
      if (!meta.title) return false
      return true
    })
    .map((item) => ({
      title: (item.meta as Record<string, unknown>).title as string,
      path: item.path,
    }))
})

/**
 * antdv types the itemRender slot's `title` loosely (string | number | boolean |
 * VNode | function), while our items only ever carry i18n keys. Only strings and
 * numbers can be translated; anything else renders as empty text.
 */
function renderTitle(title: unknown): string {
  if (typeof title === 'string') return title ? t(title) : ''
  if (typeof title === 'number') return t(String(title))
  return ''
}
</script>

<template>
  <ABreadcrumb v-if="breadcrumbList.length" :items="breadcrumbList">
    <template #itemRender="item">
      <RouterLink
        v-if="item.path !== breadcrumbList[breadcrumbList.length - 1].path"
        :to="item.path as any"
      >
        {{ renderTitle(item.title) }}
      </RouterLink>
      <span v-else>
        {{ renderTitle(item.title) }}
      </span>
    </template>
  </ABreadcrumb>
</template>

<style lang="scss" scoped></style>
