<script setup lang="ts">
import Layout from './layout.vue'
import Theme from './theme.vue'

defineOptions({
  name: 'ConfigPanelPanel',
})

const { t } = useI18n()
const open = defineModel<boolean>()
const appStore = useAppStore()
const { config } = storeToRefs(appStore)

const { copy, isSupported, copied } = useClipboard()

async function handleCopy() {
  if (!isSupported.value) {
    window.$message.error(t('app.not-support-clipboard'))
    return
  }
  await copy(JSON.stringify(config.value))
  if (copied.value) {
    window.$message.success(t('app.config-copy-success'))
  }
}
</script>

<template>
  <ADrawer v-model:open="open" destroy-on-hidden :close-icon="false" :size="300">
    <Theme />
    <ADivider />
    <Layout />
    <template #footer>
      <div class="text-right">
        <AButton type="primary" @click="handleCopy">{{ t('app.copy-config') }}</AButton>
      </div>
    </template>
  </ADrawer>
</template>

<style lang="scss" scoped>
:deep(.ant-segmented .ant-segmented-item-icon + *) {
  margin-left: 0;
}
</style>
