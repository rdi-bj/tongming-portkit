<script setup lang="ts">
const emit = defineEmits<{
  click: []
}>()

const handleClick = () => emit('click')
</script>

<template>
  <AButton type="link" class="light-link" @click="handleClick">
    <slot />
  </AButton>
</template>

<style lang="scss" scoped>
.light-link {
  height: auto;
  padding: 0;

  font-family:
    Microsoft YaHei,
    sans-serif;
  font-size: 16px;
  font-weight: normal;
  line-height: normal;
  color: #7182a8;
  cursor: pointer;

  &:hover {
    color: #3578ff;
  }

  :deep(img.icon) {
    width: 24px;
    height: 24px;
    vertical-align: middle;

    &:not(:last-child) {
      margin-right: 4px;
    }
    &:last-child {
      margin-left: 4px;
    }
  }
}
</style>

<style lang="scss">
.dark .light-link {
  color: #a0adde;

  &:hover {
    color: #4d8aff;
  }
}
</style>
