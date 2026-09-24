<script setup lang="ts">
import Footer from '../layout-components/footer/index.vue'

const route = useRoute()
const showFooter = computed(() => route.name !== 'Login')
</script>

<template>
  <ALayout>
    <ALayoutContent class=":uno: flex flex-col size-screen">
      <RouterView class="grow" />
      <Footer v-if="showFooter" class="shrink-0" />
    </ALayoutContent>
  </ALayout>
</template>

<style lang="scss" scoped></style>
