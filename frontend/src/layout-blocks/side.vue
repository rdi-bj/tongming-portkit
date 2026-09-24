<script setup lang="ts">
import Content from '../layout-components/content/index.vue'
import Footer from '../layout-components/footer/index.vue'
import Header from '../layout-components/header/index.vue'
import Sider from '../layout-components/sider/index.vue'
</script>

<template>
  <ALayout class=":uno: size-full" has-sider>
    <Sider />
    <ALayout>
      <Header class="px-4" />
      <Content />
      <Footer />
    </ALayout>
  </ALayout>
</template>

<style lang="scss" scoped></style>
