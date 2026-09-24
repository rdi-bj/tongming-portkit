<script setup lang="ts">
import type { SegmentedOptions } from 'antdv-next'
import type { ContentWidth, LayoutMode } from '@/constants/app'
import { contentWidthList, layoutModeList } from '@/constants/app'

defineOptions({
  name: 'ConfigPanelLayout',
})

const { t } = useI18n()

const appStore = useAppStore()
const { config } = storeToRefs(appStore)

const layoutModeOptions: SegmentedOptions = layoutModeList.map((item) => ({
  label: t(item.label),
  value: item.value,
}))

const contentWidthOptions: SegmentedOptions = contentWidthList.map((item) => ({
  label: t(item.label),
  value: item.value,
}))
</script>

<template>
  <div>
    <p>{{ t('app.config.layout') }}</p>
    <p class=":uno: mb-4">
      <ASegmented
        :value="config.layout.mode"
        :options="layoutModeOptions"
        @change="(value) => appStore.setLayoutMode(value as LayoutMode)"
      />
    </p>
    <p class=":uno: mb-6 flex-bc">
      <span>{{ t('app.config.content-width') }}</span>
      <ASegmented
        :value="config.layout.contentWidth"
        :options="contentWidthOptions"
        @change="(value) => appStore.setContentWidth(value as ContentWidth)"
      />
    </p>
    <p class=":uno: mb-6 flex-bc">
      <span>{{ t('app.config.breadcrumb') }}</span>
      <ASwitch v-model:checked="config.layout.breadcrumb" />
    </p>
    <p class=":uno: flex-bc">
      <span>{{ t('app.logo') }}</span>
      <ASwitch v-model:checked="config.layout.logo" />
    </p>
  </div>
</template>

<style lang="scss" scoped></style>
