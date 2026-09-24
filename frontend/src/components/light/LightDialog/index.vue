<script setup lang="ts">
withDefaults(
  defineProps<{
    width?: string
    height?: string
    minHeight?: string
    padding?: string
    wrapClassName?: string
  }>(),
  {
    width: '50%',
    padding: '20px 30px',
  },
)

const { t } = useI18n()

const visible = defineModel<boolean>('open', { default: false })

function handleCancel() {
  visible.value = false
}

function open() {
  visible.value = true
}
function close() {
  visible.value = false
}

defineExpose({ open, close })
</script>

<template>
  <AModal
    v-model:open="visible"
    :width="width"
    :footer="false"
    :closable="false"
    :mask-closable="false"
    :keyboard="false"
    :wrap-class-name="wrapClassName"
    :centered="true"
    :z-index="1000"
    class="light-dialog"
    @cancel="handleCancel"
  >
    <div
      class="light-dialog-context"
      :style="{
        height: height || undefined,
        minHeight: minHeight || undefined,
        padding,
      }"
    >
      <div class="light-dialog-header">
        <div class="title">
          <slot name="title">{{ t('common.light.dialog-default-title') }}</slot>
        </div>
        <div class="extra">
          <slot name="extra">
            <LightButton @click="close()">
              <img class="icon small" src="@/assets/svg/close.svg" alt="" />
              <span class="text">{{ t('common.close') }}</span>
            </LightButton>
          </slot>
        </div>
      </div>
      <div class="light-dialog-body">
        <slot />
      </div>
    </div>
  </AModal>
</template>

<style lang="scss" scoped>
.light-dialog-context {
  box-sizing: border-box;
  display: flex;
  flex-direction: column;
  background: #f0f3fc;
  border-radius: 16px;
}
</style>

<style lang="scss">
.light-dialog-header {
  display: flex;
  align-items: center;
  padding-bottom: 15px;
  margin-bottom: 15px;
  border-bottom: 1px solid #eee;

  .title {
    display: flex;
    align-items: center;
    min-width: 0;
    overflow: hidden;
    font-family:
      Source Han Sans,
      sans-serif;
    font-size: 20px;
    font-weight: 600;
    line-height: normal;
    color: #3b4477;
  }

  .extra {
    display: flex;
    align-items: center;
    margin-left: auto;
  }
}

.light-dialog-body {
  display: flex;
  flex: 1;
  flex-direction: column;
  overflow: auto;
}
</style>

<style lang="scss">
.ant-modal.light-dialog .ant-modal-container {
  padding: 0;
  background: #f0f3fc;
}
</style>

<style lang="scss">
.dark {
  .light-dialog-context {
    background: #0f1219;
  }

  .light-dialog-header {
    border-bottom-color: #2a3050;

    .title {
      color: #e8edf5;
    }
  }

  .ant-modal.light-dialog .ant-modal-container {
    background: #0f1219;
  }
}
</style>
