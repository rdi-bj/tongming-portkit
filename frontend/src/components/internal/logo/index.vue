<script setup lang="ts">
defineOptions({
  name: 'Logo',
})

const props = defineProps<{ size?: 'large'; iconOnly?: boolean }>()

const { t } = useI18n()
</script>

<template>
  <span
    class=":uno: flex-c inline-flex shrink-0 whitespace-nowrap text-ellipsis overflow-hidden hover:cursor-pointer"
    @click="$router.push('/dashboard')"
  >
    <svg
      viewBox="0 0 50 50"
      :class="clsx(':uno: shrink-0', props.size === 'large' ? 'size-16' : 'size-8')"
      xmlns="http://www.w3.org/2000/svg"
    >
      <defs>
        <clipPath id="logo-clip">
          <path d="m 12,344 h 72 v 56.47 H 12 Z" />
        </clipPath>
      </defs>
      <g
        transform="matrix(1.1941724,0,0,-1.2036551,-31.721997,482.02774)"
        clip-path="url(#logo-clip)"
      >
        <path
          d="m 49.92,387.3 c 0,-5.937 -3.726,-11.54 -10.94,-12.77 l 10.16,-11.88 1.014,1.346 18.28,25.65 v 10.87 h -29.57 c 7.334,-1.231 11.06,-7.172 11.06,-13.22 z"
          style="fill: #f6b21a"
        />
        <path
          d="m 55.11,360.5 13.32,18.6 v -20.17 l -14.33,-1.4e-4 z m -25.95,19.49 h 7.108 c 5.081,0 7.677,3.699 7.677,7.283 0,3.588 -2.486,7.287 -7.677,7.287 h -9.704 v -35.63014 h 17.72 L 29.044,377.19 v 2.801 z"
          style="fill: #2a3172"
        />
      </g>
    </svg>
    <span
      v-if="!iconOnly"
      class="logo-title"
      :class="clsx(':uno: text-14px font-bold ml-2', props.size === 'large' && 'text-8')"
    >
      {{ t('app.title') }}
    </span>
  </span>
</template>

<style lang="scss" scoped></style>

<style lang="scss">
html[lang='en-US'] .logo-title {
  font-size: 10px !important;
}
</style>
