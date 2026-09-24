<script setup lang="ts">
definePage({
  name: 'Error403',
  meta: {
    title: 'routes.error.403',
    menu: false,
    menuSort: 1,
  },
})

const { t } = useI18n()
</script>

<template>
  <ACard>
    <AResult status="403" title="403" :sub-title="t('common.error.403')">
      <template #extra>
        <AButton @click="$router.back()"> {{ t('common.back') }} </AButton>
        <AButton type="primary" @click="$router.replace('/dashboard')">
          {{ t('common.back-home') }}
        </AButton>
      </template>
    </AResult>
  </ACard>
</template>

<style lang="scss" scoped></style>
