<script setup lang="ts">
definePage({
  name: '404',
  meta: {
    title: '404',
    layout: 'pure',
    requiresLogin: false,
  },
})

const { t } = useI18n()
</script>

<template>
  <div class="size-full">
    <AResult
      class="flex-c flex-col size-full"
      status="404"
      title="404"
      :sub-title="t('common.error.404')"
    >
      <template #extra>
        <AButton @click="$router.back()"> {{ t('common.back') }} </AButton>
        <AButton type="primary" @click="$router.replace('/')">
          {{ t('common.back-home') }}
        </AButton>
      </template>
    </AResult>
  </div>
</template>

<style lang="scss" scoped>
:deep(.ant-result .ant-result-image) {
  margin: 0 auto 24px;
}
</style>
