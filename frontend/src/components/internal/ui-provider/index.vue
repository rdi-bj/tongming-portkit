<script setup lang="ts">
import { message, Modal, notification, theme } from 'antdv-next'
import enUS from 'antdv-next/locale/en_US'
import zhCN from 'antdv-next/locale/zh_CN'

defineOptions({
  name: 'UiProvider',
})

const appStore = useAppStore()
const { isDark, themeToken } = storeToRefs(appStore)

const { locale } = useI18n()

const localeMap = new Map([
  ['zh-CN', zhCN],
  ['en-US', enUS],
])

const [messageApi, MessageContextHolder] = message.useMessage()
const [notificationApi, NotificationContextHolder] = notification.useNotification()
const [modalApi, ModalContextHolder] = Modal.useModal()

window.$message = messageApi
window.$notification = notificationApi
window.$modal = modalApi
</script>

<template>
  <AConfigProvider
    :locale="localeMap.get(locale)"
    :theme="{
      algorithm: isDark ? theme.darkAlgorithm : theme.defaultAlgorithm,
      ...themeToken,
    }"
  >
    <AStyleProvider hash-priority="high">
      <AApp class=":uno: size-full" :message="{ maxCount: 5 }" :notification="{ maxCount: 3 }">
        <MessageContextHolder />
        <NotificationContextHolder />
        <ModalContextHolder />
        <slot />
      </AApp>
    </AStyleProvider>
  </AConfigProvider>
</template>

<style lang="scss" scoped></style>
