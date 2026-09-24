<script setup lang="ts">
import type { HTMLAttributes } from 'vue'
import { Icon } from '@iconify/vue'
import { REGEX_URL } from '@/constants/regex'

defineOptions({
  name: 'RenderIcon',
})

const props = defineProps<{
  icon: string
  class?: HTMLAttributes['class']
}>()

const outputType = computed(() => {
  if (!props.icon) {
    return ''
  }
  if (/i-[^:]+[:-][^:]+/.test(props.icon)) {
    return 'unocss'
  } else if (props.icon.includes(':')) {
    return 'iconify'
  } else if (REGEX_URL.test(props.icon)) {
    return 'web-image'
  }
  return ''
})
</script>

<template>
  <i
    v-if="outputType === 'unocss'"
    :class="clsx('shrink-0 relative fill-current leading-1', props.class, props.icon)"
  />
  <Icon
    v-else-if="outputType === 'iconify'"
    :icon="icon"
    :class="clsx('shrink-0 relative fill-current leading-1', props.class)"
  />
  <AImage
    v-else-if="outputType === 'web-image'"
    :src="icon"
    alt="icon"
    :class="clsx('shrink-0 relative', props.class)"
  />
</template>

<style lang="scss" scoped></style>
