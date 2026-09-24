<script setup lang="ts">
import type { SegmentedOptions } from 'antdv-next'
import type { ColorMode, MenuColorMode } from '@/constants/app'
import { colorModeList, menuColorModeList, primaryColorList } from '@/constants/app'

defineOptions({
  name: 'ConfigPanelTheme',
})

const { t } = useI18n()

const appStore = useAppStore()
const { config, isDark } = storeToRefs(appStore)

const colorModeOptions: SegmentedOptions = colorModeList.map((item) => ({
  label: t(item.label),
  value: item.value,
}))

const menuColorModeOptions: SegmentedOptions = menuColorModeList.map((item) => ({
  label: t(item.label),
  value: item.value,
}))
</script>

<template>
  <div>
    <p>{{ t('app.config.color-mode') }}</p>
    <p>
      <ASegmented
        :value="config.theme.colorMode"
        :options="colorModeOptions"
        @change="(value) => appStore.setColorMode(value as ColorMode)"
      />
    </p>
    <template v-if="!isDark">
      <p>{{ t('app.config.menu-color-mode') }}</p>
      <p>
        <ASegmented
          :value="config.theme.menuColorMode"
          :options="menuColorModeOptions"
          @change="(value) => appStore.setMenuColorMode(value as MenuColorMode)"
        />
      </p>
    </template>
    <p>{{ t('app.config.primary-color') }}</p>
    <p>
      <AFlex gap="8" wrap>
        <ATooltip v-for="item in primaryColorList" :key="item.value" :title="t(item.label)">
          <div
            class=":uno: flex-c inline-flex size-6 rounded-sm hover:cursor-pointer"
            :style="{
              backgroundColor: item.color,
            }"
            @click="appStore.setPrimaryColor(item.value)"
          >
            <RenderIcon
              v-if="config.theme.primaryColor === item.value"
              icon="i-ant-design:check-outlined"
              class="text-white"
            />
          </div>
        </ATooltip>
      </AFlex>
    </p>
  </div>
</template>

<style lang="scss" scoped></style>
