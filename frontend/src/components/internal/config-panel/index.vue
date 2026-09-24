<script setup lang="ts">
import Panel from './panel.vue'

defineOptions({
  name: 'ConfigPanel',
})

const open = ref<boolean>(false)
</script>

<template>
  <div
    class=":uno: text-white p-3 right-0 top-1/3 fixed z-100 ant-bg-primary rounded-l hover:cursor-pointer"
    @click="open = true"
  >
    <RenderIcon
      :icon="open ? 'i-ant-design-close-outlined' : 'i-ant-design-setting-outlined'"
      class="size-6"
    />
  </div>
  <Panel v-model="open" />
</template>

<style lang="scss" scoped></style>
