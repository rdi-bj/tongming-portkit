<script setup lang="ts">
import type { EChartsOption } from 'echarts'
import type { VisualTone } from '@/composables/useAtlasData'
import { PieChart } from 'echarts/charts'
import { LegendComponent, TitleComponent, TooltipComponent } from 'echarts/components'
import { use } from 'echarts/core'
import { CanvasRenderer } from 'echarts/renderers'
import { nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import VChart from 'vue-echarts'
import { nodeToneColor } from '@/composables/useAtlasData'

// --- Props: all computed values from useAtlasData ---
const props = defineProps<{
  atlasSourceLanguageRows: Array<{
    key: string
    label: string
    fileCount: number
    lineCount: number
    widthPercent: number
  }>
  atlasSourceHiddenTypeCount: number
  atlasSourceTotalLineText: string
  atlasSourceTotalLineStyle: Record<string, string>
  atlasSourceTotalFileCount: number
  atlasMainLanguageLabel: string
  atlasSourceLanguageText: string
  atlasSourceBuildSystemsText: string
  atlasAssemblyTotalCount: number
  atlasAssemblyFileCount: number
  atlasInlineAsmCount: number
  atlasAssemblyNotice: null | { title: string; detail: string; tone: 'neutral' | 'info' }
  atlasAssemblyArchitectureRows: Array<{
    key: string
    label: string
    total: number
    widthPercent: number
  }>
  atlasMainboardStats: Array<{
    key: string
    label: string
    value: number
    tone: VisualTone
  }>
  atlasMainboardPieRows: Array<{
    key: string
    label: string
    count: number
    color: string
    percent: number
  }>
  atlasMainboardPieOption: EChartsOption
  atlasDepTotalCount: number
  atlasDepSupportedCount: number
  atlasDepUnsupportedCount: number
  atlasDependencyNotice: null | { title: string; detail: string; tone: 'neutral' | 'info' }
  atlasBuildAnalyzedFiles: number
  atlasBuildPatternCount: number
  atlasBuildArchCount: number
  atlasBuildArchRows: Array<{ key: string; label: string; count: number; widthPercent: number }>
  atlasBuildNotice: null | { title: string; detail: string; tone: 'neutral' | 'info' }
  depLibGroups: Array<{
    category: string
    libs: Array<{ libName: string; libCategory: string; supportRiscv: string }>
  }>
}>()

use([CanvasRenderer, PieChart, TitleComponent, TooltipComponent, LegendComponent])

const { t } = useI18n()

// --- Auto-fit font size for the total lines counter ---
// Long numbers (several comma-separated groups) that exceed the column width are clipped by
// overflow:hidden (comma or last digit lost); scale the font to the available width so numbers always fit.
const totalLineRef = ref<HTMLElement | null>(null)
const totalLineFontSize = ref<string | undefined>(undefined)
let totalLineInitialFs = 0
let totalLineResizeObserver: ResizeObserver | null = null

function fitTotalLineFont() {
  const el = totalLineRef.value
  if (!el || !props.atlasSourceTotalLineText) return
  const availWidth = el.clientWidth
  if (!availWidth) return
  if (!totalLineInitialFs) {
    totalLineInitialFs = Number.parseFloat(props.atlasSourceTotalLineStyle.fontSize ?? '24') || 24
  }
  const probe = document.createElement('canvas').getContext('2d')
  if (!probe) return
  // tabular-nums is wider than proportional for long numbers, the parent style adds letter-spacing, and canvas
  // measureText supports neither, which can underestimate the width; a 0.92 safety factor keeps the number fully visible
  probe.font = `700 ${totalLineInitialFs}px ${getComputedStyle(el).fontFamily}`
  const naturalWidth = probe.measureText(props.atlasSourceTotalLineText).width * 0.92
  const target =
    naturalWidth > availWidth
      ? (totalLineInitialFs * availWidth) / naturalWidth
      : totalLineInitialFs
  totalLineFontSize.value = `${Math.max(8, Math.floor(target))}px`
}

function setupTotalLineObserver() {
  const parent = totalLineRef.value?.parentElement
  if (parent) {
    totalLineResizeObserver = new ResizeObserver(() => fitTotalLineFont())
    totalLineResizeObserver.observe(parent)
  }
  fitTotalLineFont()
}

onMounted(() => setupTotalLineObserver())
watch(
  () => props.atlasSourceTotalLineText,
  () => nextTick(() => fitTotalLineFont()),
)
onBeforeUnmount(() => totalLineResizeObserver?.disconnect())
</script>

<template>
  <div class="remediation-atlas remediation-atlas--html">
    <div class="remediation-atlas__mesh"></div>
    <div class="remediation-atlas__glow remediation-atlas__glow--left"></div>
    <div class="remediation-atlas__glow remediation-atlas__glow--right"></div>
    <div class="remediation-atlas__glow remediation-atlas__glow--bottom"></div>
    <div class="remediation-atlas__ribbon remediation-atlas__ribbon--top"></div>
    <div class="remediation-atlas__ribbon remediation-atlas__ribbon--bottom"></div>

    <div class="remediation-atlas__content">
      <div class="space-y-4">
        <div class="space-y-2">
          <div class="atlas-label">{{ t('scan.atlas.porting-snapshot') }}</div>
          <div class="atlas-title">{{ t('scan.atlas.title') }}</div>
        </div>
      </div>

      <div class="remediation-atlas__layout">
        <!-- Left side -->
        <div class="remediation-atlas__side remediation-atlas__side--left">
          <!-- Source overview -->
          <section class="remediation-atlas__panel">
            <div class="atlas-panel-title">{{ t('scan.atlas.source-overview') }}</div>
            <div class="atlas-panel-subtitle">{{ t('scan.atlas.source-overview-sub') }}</div>

            <div class="atlas-section-title atlas-section-title--spaced">
              {{ t('scan.atlas.file-type-distribution') }}
            </div>
            <div v-if="atlasSourceLanguageRows.length > 0" class="remediation-atlas__source-bars">
              <div class="remediation-atlas__source-bars-head">
                <span class="atlas-table-header">{{ t('scan.atlas.file-type') }}</span>
                <span aria-hidden="true"></span>
                <span class="atlas-table-header atlas-table-header--summary">{{
                  t('scan.atlas.files-lines')
                }}</span>
              </div>
              <div
                v-for="item in atlasSourceLanguageRows"
                :key="`atlas-source-language-row-${item.key}`"
                class="remediation-atlas__source-bar-row"
              >
                <span class="atlas-table-text">{{ item.label }}</span>
                <div class="remediation-atlas__source-bar-track">
                  <div
                    class="remediation-atlas__source-bar-fill"
                    :style="{ width: `${item.widthPercent}%` }"
                  />
                </div>
                <span class="atlas-table-muted atlas-table-muted--summary">
                  {{ `${item.fileCount} / ${item.lineCount.toLocaleString()}` }}
                </span>
              </div>
            </div>
            <div v-else class="atlas-footnote atlas-footnote--plain">
              {{ t('scan.atlas.no-file-type-distribution') }}
            </div>

            <div class="atlas-section-title atlas-section-title--spaced">
              {{ t('scan.atlas.code-scale') }}
            </div>
            <div class="remediation-atlas__metric-row remediation-atlas__metric-row--source">
              <div class="remediation-atlas__metric-pill remediation-atlas__metric-pill--orange">
                <div class="atlas-mini-label">{{ t('scan.atlas.total-lines') }}</div>
                <div
                  ref="totalLineRef"
                  class="atlas-mini-value"
                  :style="{ ...atlasSourceTotalLineStyle, fontSize: totalLineFontSize }"
                >
                  {{ atlasSourceTotalLineText }}
                </div>
              </div>
              <div class="remediation-atlas__metric-pill remediation-atlas__metric-pill--info">
                <div class="atlas-mini-label">{{ t('scan.atlas.source-files') }}</div>
                <div class="atlas-mini-value">{{ atlasSourceTotalFileCount }}</div>
              </div>
              <div class="remediation-atlas__metric-pill remediation-atlas__metric-pill--orange">
                <div class="atlas-mini-label">{{ t('scan.atlas.main-language') }}</div>
                <div class="atlas-mini-value">{{ atlasMainLanguageLabel }}</div>
              </div>
            </div>

            <div class="atlas-section-title atlas-section-title--spaced">
              {{ t('scan.atlas.tech-stack') }}
            </div>
            <div class="remediation-atlas__stack-list">
              <div class="remediation-atlas__stack-row">
                <span class="atlas-table-header">{{ t('scan.atlas.dev-language') }}</span>
                <span class="atlas-stack-value">{{ atlasSourceLanguageText }}</span>
              </div>
              <div class="remediation-atlas__stack-row">
                <span class="atlas-table-header">{{ t('scan.atlas.build-system') }}</span>
                <span class="atlas-stack-value">{{ atlasSourceBuildSystemsText }}</span>
              </div>
            </div>

            <div v-if="atlasSourceHiddenTypeCount > 0" class="atlas-footnote">
              {{ t('scan.atlas.more-types', { count: atlasSourceHiddenTypeCount }) }}
            </div>
          </section>

          <!-- Assembly -->
          <section class="remediation-atlas__panel">
            <div class="atlas-panel-title">{{ t('scan.atlas.assembly') }}</div>
            <div class="atlas-panel-subtitle">{{ t('scan.atlas.assembly-sub') }}</div>

            <div class="remediation-atlas__metric-row">
              <div class="remediation-atlas__metric-pill remediation-atlas__metric-pill--orange">
                <div class="atlas-mini-label">{{ t('scan.atlas.assembly-total') }}</div>
                <div class="atlas-mini-value">{{ atlasAssemblyTotalCount }}</div>
              </div>
              <div class="remediation-atlas__metric-pill remediation-atlas__metric-pill--warning">
                <div class="atlas-mini-label">{{ t('scan.atlas.assembly-files') }}</div>
                <div class="atlas-mini-value">{{ atlasAssemblyFileCount }}</div>
              </div>
              <div class="remediation-atlas__metric-pill remediation-atlas__metric-pill--info">
                <div class="atlas-mini-label">{{ t('scan.atlas.inline-asm') }}</div>
                <div class="atlas-mini-value">{{ atlasInlineAsmCount }}</div>
              </div>
            </div>

            <div
              v-if="atlasAssemblyNotice"
              class="remediation-atlas__notice"
              :style="{
                backgroundColor: atlasAssemblyNotice.tone === 'info' ? '#eff6ff' : '#f8fafc',
                borderColor: atlasAssemblyNotice.tone === 'info' ? '#bfdbfe' : '#cbd5e1',
              }"
            >
              <div class="atlas-blocker-title">{{ atlasAssemblyNotice.title }}</div>
              <div class="atlas-blocker-detail">{{ atlasAssemblyNotice.detail }}</div>
            </div>

            <div
              v-if="atlasAssemblyArchitectureRows.length > 0"
              class="remediation-atlas__assembly-bars"
            >
              <div class="remediation-atlas__assembly-bars-head">
                <span class="atlas-table-header">{{ t('scan.atlas.arch-stats') }}</span>
                <span class="atlas-table-header">
                  {{ t('scan.atlas.arch-count', { count: atlasAssemblyArchitectureRows.length }) }}
                </span>
              </div>

              <div
                v-for="item in atlasAssemblyArchitectureRows"
                :key="`atlas-assembly-arch-row-${item.key}`"
                class="remediation-atlas__assembly-bar-row"
              >
                <span class="atlas-table-text">{{ item.label }}</span>
                <div class="remediation-atlas__assembly-bar-track">
                  <div
                    class="remediation-atlas__assembly-bar-fill"
                    :style="{ width: `${item.widthPercent}%` }"
                  />
                </div>
                <span class="atlas-table-muted">{{ item.total }}</span>
              </div>
            </div>
          </section>
        </div>

        <!-- Center: mainboard -->
        <section class="remediation-atlas__mainboard">
          <div class="remediation-atlas__mainboard-shell">
            <div class="atlas-board-kicker">{{ t('scan.atlas.porting-mainboard') }}</div>
            <div class="atlas-panel-title">{{ t('scan.atlas.framework') }}</div>
            <div class="atlas-panel-subtitle">{{ t('scan.atlas.framework-sub') }}</div>

            <div class="remediation-atlas__board">
              <div class="remediation-atlas__board-section">
                <div class="atlas-section-title">{{ t('scan.atlas.file-dimension') }}</div>
                <div class="remediation-atlas__board-stats">
                  <div
                    v-for="item in atlasMainboardStats"
                    :key="`atlas-mainboard-stat-${item.key}`"
                    class="remediation-atlas__board-stat"
                    :style="{ '--board-stat-color': nodeToneColor(item.tone) }"
                  >
                    <div class="atlas-board-stat-label">{{ item.label }}</div>
                    <div class="atlas-board-stat-value">{{ item.value }}</div>
                  </div>
                </div>
              </div>

              <div class="remediation-atlas__board-section">
                <div class="atlas-section-title">{{ t('scan.atlas.issue-dimension') }}</div>
                <div class="remediation-atlas__board-distribution-body">
                  <template v-if="atlasMainboardPieRows.length > 0">
                    <div class="remediation-atlas__board-pie-panel">
                      <div class="remediation-atlas__board-pie-shell">
                        <VChart
                          class="remediation-atlas__board-pie"
                          :option="atlasMainboardPieOption"
                          autoresize
                        />
                        <div class="remediation-atlas__board-pie-center">
                          <div class="atlas-board-pie-label">
                            {{ t('scan.atlas.issue-distribution') }}
                          </div>
                          <div class="atlas-board-pie-value">
                            {{ atlasMainboardPieRows.length }}
                          </div>
                          <div class="atlas-board-pie-caption">
                            {{ t('scan.atlas.issue-category') }}
                          </div>
                        </div>
                      </div>
                    </div>

                    <div class="remediation-atlas__board-legend-list">
                      <div
                        v-for="item in atlasMainboardPieRows"
                        :key="`atlas-main-category-${item.key}`"
                        class="remediation-atlas__board-legend-row"
                      >
                        <div class="remediation-atlas__board-legend-main">
                          <span
                            class="remediation-atlas__board-legend-dot"
                            :style="{ backgroundColor: item.color }"
                          />
                          <span class="atlas-table-text">{{ item.label }}</span>
                        </div>
                        <span class="atlas-table-muted atlas-table-muted--summary">{{
                          `${item.count} / ${item.percent}%`
                        }}</span>
                      </div>
                    </div>
                  </template>
                  <div
                    v-else
                    class="remediation-atlas__notice"
                    :style="{ backgroundColor: '#f8fafc', borderColor: '#cbd5e1' }"
                  >
                    <div class="atlas-blocker-title">{{ t('scan.atlas.no-issues') }}</div>
                    <div class="atlas-blocker-detail">{{ t('scan.atlas.no-issues-detail') }}</div>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </section>

        <!-- Right side -->
        <div class="remediation-atlas__side remediation-atlas__side--right">
          <!-- Dependencies -->
          <section class="remediation-atlas__panel">
            <div class="atlas-panel-title">{{ t('scan.atlas.dependencies') }}</div>

            <div class="remediation-atlas__metric-row">
              <div class="remediation-atlas__metric-pill remediation-atlas__metric-pill--orange">
                <div class="atlas-mini-label">{{ t('scan.atlas.total-libs') }}</div>
                <div class="atlas-mini-value">{{ atlasDepTotalCount }}</div>
              </div>
              <div class="remediation-atlas__metric-pill remediation-atlas__metric-pill--success">
                <div class="atlas-mini-label">{{ t('scan.atlas.supported-riscv') }}</div>
                <div class="atlas-mini-value">{{ atlasDepSupportedCount }}</div>
              </div>
              <div class="remediation-atlas__metric-pill remediation-atlas__metric-pill--danger">
                <div class="atlas-mini-label">{{ t('scan.atlas.unsupported-riscv') }}</div>
                <div class="atlas-mini-value">{{ atlasDepUnsupportedCount }}</div>
              </div>
            </div>

            <div
              v-if="atlasDependencyNotice"
              class="remediation-atlas__notice"
              :style="{
                backgroundColor: atlasDependencyNotice.tone === 'info' ? '#eff6ff' : '#f8fafc',
                borderColor: atlasDependencyNotice.tone === 'info' ? '#bfdbfe' : '#cbd5e1',
              }"
            >
              <div class="atlas-blocker-title">{{ atlasDependencyNotice.title }}</div>
              <div class="atlas-blocker-detail">{{ atlasDependencyNotice.detail }}</div>
            </div>

            <template v-for="group in depLibGroups" :key="group.category">
              <div v-if="group.libs.length > 0" class="remediation-atlas__table">
                <div
                  class="remediation-atlas__table-head remediation-atlas__table-head--dependency"
                >
                  <span class="atlas-table-header">{{ group.category }}</span>
                  <span class="atlas-table-header atlas-table-header--center">RV64</span>
                </div>

                <div
                  class="remediation-atlas__table-body remediation-atlas__table-body--dependency"
                >
                  <div
                    v-for="lib in group.libs"
                    :key="`atlas-dep-lib-${lib.libName}`"
                    class="remediation-atlas__table-row remediation-atlas__table-row--dependency"
                  >
                    <span class="atlas-table-text">{{ lib.libName }}</span>
                    <span class="atlas-support-cell">
                      <span
                        class="atlas-support-mark"
                        :class="
                          lib.supportRiscv === '1'
                            ? 'atlas-support-mark--yes'
                            : lib.supportRiscv === '0'
                              ? 'atlas-support-mark--no'
                              : ''
                        "
                      >
                        {{ lib.supportRiscv === '1' ? '✓' : lib.supportRiscv === '0' ? '✗' : '?' }}
                      </span>
                    </span>
                  </div>
                </div>
              </div>
            </template>
          </section>

          <!-- Build system -->
          <section class="remediation-atlas__panel">
            <div class="atlas-panel-title">{{ t('scan.atlas.build-system') }}</div>

            <div
              class="remediation-atlas__metric-row"
              style="grid-template-columns: repeat(2, minmax(0, 1fr))"
            >
              <div class="remediation-atlas__metric-pill remediation-atlas__metric-pill--orange">
                <div class="atlas-mini-label">{{ t('scan.atlas.analyzed-files') }}</div>
                <div class="atlas-mini-value">{{ atlasBuildAnalyzedFiles }}</div>
              </div>
              <div class="remediation-atlas__metric-pill remediation-atlas__metric-pill--warning">
                <div class="atlas-mini-label">{{ t('scan.atlas.build-patterns') }}</div>
                <div class="atlas-mini-value">{{ atlasBuildPatternCount }}</div>
              </div>
            </div>

            <div v-if="atlasBuildArchRows.length > 0" class="remediation-atlas__assembly-bars">
              <div class="remediation-atlas__assembly-bars-head">
                <span class="atlas-table-header">{{ t('scan.atlas.arch-stats') }}</span>
                <span class="atlas-table-header">
                  {{ t('scan.atlas.arch-count', { count: atlasBuildPatternCount }) }}
                </span>
              </div>

              <div
                v-for="item in atlasBuildArchRows"
                :key="`atlas-build-arch-row-${item.key}`"
                class="remediation-atlas__assembly-bar-row"
              >
                <span class="atlas-table-text">{{ item.label }}</span>
                <div class="remediation-atlas__assembly-bar-track">
                  <div
                    class="remediation-atlas__assembly-bar-fill"
                    :style="{ width: `${item.widthPercent}%` }"
                  />
                </div>
                <span class="atlas-table-muted">{{ item.count }}</span>
              </div>
            </div>

            <div
              v-if="atlasBuildNotice"
              class="remediation-atlas__notice"
              :style="{ backgroundColor: '#f8fafc', borderColor: '#cbd5e1' }"
            >
              <div class="atlas-blocker-title">{{ atlasBuildNotice.title }}</div>
              <div class="atlas-blocker-detail">{{ atlasBuildNotice.detail }}</div>
            </div>
          </section>
        </div>
      </div>
    </div>
  </div>
</template>

<style>
@import '@/styles/atlas.css';
</style>
