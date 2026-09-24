<script setup lang="ts">
import Content from '../layout-components/content/index.vue'
import Footer from '../layout-components/footer/index.vue'
import Header from '../layout-components/header/index.vue'
import Sider from '../layout-components/sider/index.vue'
</script>

<template>
  <ALayout class=":uno: size-full">
    <Header class="pl-0 pr-4" />
    <ALayout has-sider>
      <Sider />
      <ALayout>
        <Content />
        <Footer />
      </ALayout>
    </ALayout>
  </ALayout>
</template>

<style lang="scss" scoped></style>
