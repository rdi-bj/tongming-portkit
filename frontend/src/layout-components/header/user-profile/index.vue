<script setup lang="ts">
import type { MenuItemType } from 'antdv-next'

const userStore = useUserStore()
const { userInfo } = storeToRefs(userStore)

const { t } = useI18n()
const router = useRouter()
const avatarError = ref(false)

/** antdv types Avatar's onError as `() => boolean`, so return false (do not bubble) */
function onAvatarError(): boolean {
  avatarError.value = true
  return false
}

const items: MenuItemType[] = [
  {
    label: t('common.logout'),
    key: 'logout',
    icon: 'i-ant-design:logout-outlined',
    onClick: () => {
      userStore.handleLogout()
      router.replace('/login')
    },
  },
]
</script>

<template>
  <ADropdown :menu="{ items }">
    <template #iconRender="item">
      <RenderIcon :icon="item.icon" />
    </template>
    <AButton type="text" size="large">
      <template #icon>
        <AAvatar :src="!avatarError ? userInfo?.avatar : undefined" @error="onAvatarError">
          <template #icon>
            <RenderIcon icon="i-ant-design:user-outlined" />
          </template>
        </AAvatar>
      </template>
      <span class="text-14px">
        {{ userInfo?.userName ?? userInfo?.loginName ?? '-' }}
      </span>
    </AButton>
  </ADropdown>
</template>

<style lang="scss" scoped></style>
