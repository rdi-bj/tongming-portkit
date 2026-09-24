<script setup lang="ts">
import { watch } from 'vue'

type Item = Record<string, string>

const props = withDefaults(
  defineProps<{
    list?: Item[]
    labelProp?: string
    idProp?: string
  }>(),
  {
    list: () => [],
    labelProp: 'label',
    idProp: 'name',
  },
)

const emit = defineEmits<{
  change: [key: string]
}>()

const activeName = defineModel<string>()

watch(
  () => props.list,
  () => {
    if (props.list.length > 0 && !activeName.value) {
      const firstItem = props.list[0]
      activeName.value = firstItem[props.idProp]
    }
  },
  { immediate: true, deep: true },
)

const handleChange = (key: string) => emit('change', key)
</script>

<template>
  <ATabs v-model:active-key="activeName" class="light-tabs" @change="handleChange">
    <ATabPane v-for="item in list" :key="item[idProp]" :tab="item[labelProp]">
      <slot :name="item[idProp]" />
      <slot :name="item[labelProp]" />
    </ATabPane>
  </ATabs>
</template>

<style lang="scss" scoped>
.light-tabs {
  display: flex;
  flex-direction: column;

  :deep(.ant-tabs-nav) {
    flex-shrink: 0;
    margin: 0;

    &::before {
      display: none;
    }
  }

  :deep(.ant-tabs-tab) {
    display: inline-flex;
    align-items: center;
    justify-content: center;
    padding: 10px 20px;
    margin: 0 !important;
    font-family:
      Microsoft YaHei,
      sans-serif;
    font-size: 20px;
    font-weight: normal;
    line-height: 20px;
    color: #606266;
    border-radius: 8px;
    transition: all 0.2s;

    &:hover {
      color: #3578ff;
    }

    &.ant-tabs-tab-active {
      background: #3578ff;

      .ant-tabs-tab-btn {
        color: #ffffff;
      }
    }
  }

  :deep(.ant-tabs-ink-bar) {
    display: none;
  }

  :deep(.ant-tabs-content-holder) {
    flex: 1;
    overflow: hidden;
    border: none;
  }

  :deep(.ant-tabs-content) {
    height: 100%;
  }

  :deep(.ant-tabs-tabpane) {
    height: 100%;
  }

  :deep(.ant-tabs-nav-list) {
    gap: 4px;
  }
}
</style>

<style lang="scss">
.dark .light-tabs .ant-tabs-tab {
  color: #a0adde;

  &:hover {
    color: #4d8aff;
  }
}
</style>
