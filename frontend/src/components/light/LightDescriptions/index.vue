<script setup lang="ts">
import type { CSSProperties } from 'vue'

interface ColItem {
  label: string
  span?: number
  content: string
  contentStyle?: CSSProperties
}

interface RowItem {
  key?: string | number
  cols: ColItem[]
}

withDefaults(
  defineProps<{
    column?: number
    rows?: RowItem[]
  }>(),
  {
    column: 2,
    rows: () => [],
  },
)
</script>

<template>
  <ADescriptions :column="column" :bordered="true" class="light-descriptions" size="large">
    <template v-for="row in rows" :key="row.key">
      <ADescriptionsItem
        v-for="col in row.cols"
        :key="col.label"
        :span="col.span"
        :label="col.label"
      >
        {{ col.content }}
      </ADescriptionsItem>
    </template>
    <template v-if="!rows || rows.length === 0">
      <slot />
    </template>
  </ADescriptions>
</template>

<style lang="scss" scoped>
.light-descriptions {
  :deep(.ant-descriptions-view) {
    width: 100%;
    border-collapse: collapse;
  }

  :deep(.ant-descriptions-item-label) {
    box-sizing: border-box;
    padding: 23px !important;
    font-family:
      Microsoft YaHei,
      sans-serif;
    font-size: 18px;
    font-weight: normal;
    line-height: 18px;
    color: #7182a8;
    text-align: center;
    letter-spacing: 0.05em;
    white-space: nowrap;
    background-color: #f8f9fd;
  }

  :deep(.ant-descriptions-item-content) {
    box-sizing: border-box;
    padding: 21px 20px !important;
    font-family:
      Microsoft YaHei,
      sans-serif;
    font-size: 18px;
    font-weight: normal;
    line-height: 22px;
    color: #000000;
  }
}
</style>

<style lang="scss">
.dark .light-descriptions .ant-descriptions-item-label {
  color: #a0adde;
  background-color: #2a3142;
}

.dark .light-descriptions .ant-descriptions-item-content {
  color: #e8edf5;
  background-color: transparent;
}

.dark .light-descriptions .ant-descriptions-view {
  border-color: #2a3050;
}

.dark .light-descriptions .ant-descriptions-view table,
.dark .light-descriptions .ant-descriptions-view th,
.dark .light-descriptions .ant-descriptions-view td {
  border-color: #2a3050 !important;
}
</style>
