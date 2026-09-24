<script setup lang="ts">
import { computed } from 'vue'

const props = withDefaults(
  defineProps<{
    type?: 'default' | 'primary' | 'link'
  }>(),
  {
    type: 'default',
  },
)

const emit = defineEmits<{
  click: []
}>()

const btnClass = computed(() => `light-button--${props.type}`)

const handleClick = () => emit('click')
</script>

<template>
  <AButton class="light-button" :class="btnClass" v-bind="$attrs" @click="handleClick">
    <slot />
  </AButton>
</template>

<style lang="scss" scoped>
.light-button {
  box-sizing: border-box;

  display: inline-flex;
  align-items: center;
  justify-content: center;
  height: 42px;
  padding: 10px 20px;

  font-family:
    Microsoft YaHei,
    sans-serif;
  font-size: 16px;
  font-weight: normal;
  line-height: normal;
  color: #7182a8;
  cursor: pointer;
  background: #f9fbff;
  border: none !important;
  border-radius: 8px;
  box-shadow: 0px 4px 32px 0px rgba(160, 175, 222, 0.2);

  &:hover,
  &:focus {
    color: #7182a8;
    background: #f0f4ff;
    border-color: transparent !important;
  }

  :deep(img.icon) {
    width: 24px;
    height: 24px;

    &.small {
      width: 18px;
      height: 18px;
    }

    &:not(:last-child) {
      margin-right: 10px;
    }
    &:last-child {
      margin-left: 10px;
    }
  }
}

.light-button--primary {
  color: #ffffff;
  background: #3578ff;
  border-color: transparent !important;

  &:hover,
  &:focus {
    color: #ffffff;
    background: #4d8aff;
    border-color: transparent !important;
  }
}

.light-button--link {
  height: auto;
  padding: 4px 8px;
  background: transparent;
  box-shadow: none;

  &:hover,
  &:focus {
    color: #3578ff;
    background: transparent;
  }
}
</style>

<style lang="scss">
.dark {
  .light-button {
    color: #a0adde;
    background: #1a1f2e;
    box-shadow: 0px 4px 32px 0px rgba(0, 0, 0, 0.3);

    &:hover,
    &:focus {
      color: #a0adde;
      background: #22283b;
      border-color: transparent !important;
    }
  }

  .light-button--primary {
    color: #ffffff;
    background: #3578ff;

    &:hover,
    &:focus {
      color: #ffffff;
      background: #4d8aff;
    }
  }

  .light-button--link {
    background: transparent;
    box-shadow: none;

    &:hover,
    &:focus {
      color: #4d8aff;
      background: transparent;
    }
  }
}
</style>
