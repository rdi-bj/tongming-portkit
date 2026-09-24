<script setup lang="ts">
import { computed, nextTick, ref, watch } from 'vue'

// --- File data interfaces ---
export interface ScannedFileData {
  fileName: string
  filePath: string
  timeConsuming: number
  scanDuration?: number
  reviewDuration?: number
  minor: number
  medium: number
  serious: number
  totalNum?: number
  waitNum?: number
  acNum?: number
  state?: string
  order?: number
}

const props = withDefaults(
  defineProps<{
    projectName?: string
    taskId?: string
    total?: number
    completed?: number
    currentFile?: string
    latestDoneFile?: string
    scanStatus?: string
    files?: ScannedFileData[]
    /** Standalone data of stage 03, copied from scanFiles when AI_VERIFY starts */
    reviewFiles?: ScannedFileData[]
    verifyCompleted?: number
    verifyTotal?: number
    scanPhase?: string
    scanType?: string
    unzipPercent?: number
    /** Incremental counters */
    issueFiles?: number
    totalIssues?: number
  }>(),
  {
    projectName: '',
    taskId: '',
    total: 0,
    completed: 0,
    currentFile: '',
    latestDoneFile: '',
    scanStatus: '',
    files: () => [],
    reviewFiles: () => [],
    verifyCompleted: 0,
    verifyTotal: 0,
    scanPhase: '',
    scanType: '1',
    unzipPercent: 0,
    issueFiles: 0,
    totalIssues: 0,
  },
)

const { t } = useI18n()

const open = defineModel<boolean>('open', { default: false })

// --- Stages ---
type StageKey = 'extract' | 'ast' | 'review'
const isDeep = computed(() => props.scanType === '1')
const allStages = computed(() => {
  // Typed explicitly: stage 03 is appended below and must be allowed in the array
  const s: Array<{ key: StageKey; num: string; title: string }> = [
    { key: 'extract', num: '01', title: t('scan.progress.stage-extract') },
    { key: 'ast', num: '02', title: t('scan.progress.stage-ast') },
  ]
  if (isDeep.value)
    s.push({ key: 'review' as const, num: '03', title: t('scan.progress.stage-review') })
  return s
})

const activeStage = computed<StageKey>(() => {
  const ph = props.scanPhase
  if (ph === '') return 'extract'
  if (ph === 'SCANNING') return 'ast'
  if (ph === 'AI_VERIFY' && isDeep.value) return 'review'
  if (ph === 'SUCCESS' || ph === 'FAILED') return isDeep.value ? 'review' : 'ast'
  return 'extract'
})

const userStage = ref<StageKey | null>(null)
watch(activeStage, (v) => {
  userStage.value = v
})
const displayStage = computed(() => userStage.value || activeStage.value)
function switchStage(k: StageKey) {
  userStage.value = k
}
function isStageDone(key: StageKey) {
  const idx = allStages.value.findIndex((s) => s.key === key)
  const ai = allStages.value.findIndex((s) => s.key === activeStage.value)
  return idx < ai || props.scanPhase === 'SUCCESS'
}
// Three stage states: done = finished, active = running, pending = not started
// (isStageDone only tells finished from not finished, so a pending stage would look like running)
function stageState(key: StageKey): 'done' | 'active' | 'pending' {
  if (isStageDone(key)) return 'done'
  if (key === activeStage.value) return 'active'
  return 'pending'
}

// Stage 03 data is copied by the parent at AI_VERIFY time and is fully independent, no longer derived here

function stagePct(key: StageKey): number {
  if (isStageDone(key) || props.scanPhase === 'SUCCESS') return 100
  if (key === 'extract') return props.scanPhase !== '' ? 100 : 0
  if (key === 'ast') return props.total > 0 ? Math.round((props.completed / props.total) * 100) : 0
  const rfs = props.reviewFiles ?? []
  const d = rfs.filter((f) => f.state === 'COMPLETE').length
  return rfs.length > 0 ? Math.round((d / rfs.length) * 100) : 0
}

// --- Filtering ---
type Filter = 'all' | 'processing' | 'attention' | 'excluded' | 'done'
const filter = ref<Filter>('all')
watch(displayStage, () => {
  filter.value = 'all'
})

const filterButtons = computed(() => {
  if (displayStage.value === 'extract') return []
  if (displayStage.value === 'ast') {
    return [
      { key: 'all' as const, label: t('scan.progress.filter-all') },
      { key: 'processing' as const, label: t('scan.progress.filter-processing') },
      { key: 'attention' as const, label: t('scan.progress.filter-has-issues') },
      { key: 'excluded' as const, label: t('scan.progress.filter-no-issues') },
    ]
  }
  return [
    { key: 'all' as const, label: t('scan.progress.filter-all') },
    { key: 'processing' as const, label: t('scan.progress.filter-processing') },
    { key: 'done' as const, label: t('scan.progress.filter-done') },
  ]
})

// Filtered data (filter === all returns the original reference, i.e. the raw-data baseline)
const filteredFiles = computed<ScannedFileData[]>(() => {
  const list = displayStage.value === 'review' ? (props.reviewFiles ?? []) : props.files
  if (filter.value === 'all') return list
  if (displayStage.value === 'ast') {
    if (filter.value === 'processing')
      return list.filter((f) => f.state === 'SCANFING' || f.state === 'WATING')
    if (filter.value === 'attention')
      return list.filter((f) => f.state === 'COMPLETE' && f.serious > 0)
    return list.filter((f) => f.state === 'COMPLETE' && f.serious === 0)
  }
  if (filter.value === 'processing')
    return list.filter((f) => f.state === 'VERIFYING' || f.state === 'WATING')
  if (filter.value === 'done') return list.filter((f) => f.state === 'COMPLETE')
  return list
})

// Sorting

// Review stage statistics (read straight from the file fields, not from the incremental counters)
const reviewDone = computed(() => {
  let done = 0
  for (const f of props.reviewFiles ?? []) {
    if (f.totalNum && f.totalNum > 0) {
      done += f.totalNum - (f.waitNum ?? 0)
    }
  }
  return done
})
const reviewTotal = computed(() => {
  let total = 0
  for (const f of props.reviewFiles ?? []) {
    if (f.totalNum) total += f.totalNum
  }
  return total
})
const excludedCount = computed(() => {
  let excluded = 0
  for (const f of props.reviewFiles ?? []) {
    if (f.state === 'COMPLETE') {
      excluded += f.acNum ?? 0
    }
  }
  return excluded
})

// Order: done (finished first, latest finish last) → active → queued
// Rebuild only when a file is added, finishes, or the stage changes, so frequent file updates do not trigger a full sort+map
// Prefetch the excluded-n template and use replace instead of calling t() inside map
const excludedTmpl = computed(() => t('scan.progress.excluded-n', { n: '{n}' }))

const sortedFiles = ref<any[]>([])
let _sortTimer: ReturnType<typeof setTimeout> | null = null
const scrollRef = ref<HTMLElement | null>(null)
function scrollToFp(fp: string) {
  const box = scrollRef.value
  // Scroll to the latest finished row: it sits at the end of the done block and keeps moving
  // down as files finish, so the viewport follows the progress and ends near the bottom when all are done
  const el = box?.querySelector(`[data-file="${CSS.escape(fp)}"]`) as HTMLElement | null
  if (!box || !el) return
  el.scrollIntoView({ block: 'center', behavior: 'smooth' })
}
function rebuildSorted() {
  if (_sortTimer) return
  _sortTimer = setTimeout(() => {
    _sortTimer = null
    const list = filteredFiles.value
    const tmpl = excludedTmpl.value
    sortedFiles.value = [...list]
      .sort((a, b) => {
        // Group: 0 = done, 1 = active, 2 = queued
        const g = (s?: string) => (s === 'WATING' ? 2 : s === 'COMPLETE' ? 0 : 1)
        const ga = g(a.state)
        const gb = g(b.state)
        if (ga !== gb) return ga - gb
        // Inside the done group: earlier finishes on top, latest finish at the end (scrolls down naturally)
        if (ga === 0) return (a.order ?? 0) - (b.order ?? 0)
        return 0
      })
      .map((f) => ({
        ...f,
        _statusText: statusLabel(f),
        _dotCls: dotLabel(f),
        _excludedNote:
          f.state !== 'WATING' && f.acNum && f.acNum > 0
            ? tmpl.replace('{n}', String(f.acNum))
            : null,
      }))
    // After the sort rebuild and the DOM update, scroll to the latest finished row (one debounce does both)
    const fp = props.latestDoneFile
    if (fp) nextTick(() => scrollToFp(fp))
  }, 120)
}
// Rebuild when a file is added/finished, the stage or filter changes, or review data updates
watch(
  () =>
    [
      props.files.length,
      props.completed,
      props.latestDoneFile,
      displayStage.value,
      filter.value,
      // Review data signature: any change of file state or waitNum triggers a rebuild
      (props.reviewFiles ?? []).map((f) => `${f.filePath}:${f.state}:${f.waitNum ?? 0}`).join(),
    ] as const,
  () => rebuildSorted(),
  { immediate: true },
)

// i18n status text lookup (recomputed only on locale change, avoiding frequent t() calls inside .map())
const statusTexts = computed(() => ({
  ast: {
    SCANFING: t('scan.progress.status-scanning'),
    WATING: t('scan.progress.status-waiting'),
    hasIssues: t('scan.progress.status-has-issues'),
    noIssues: t('scan.progress.status-no-issues'),
  },
  review: {
    VERIFYING: t('scan.progress.status-reviewing'),
    WATING: t('scan.progress.status-waiting'),
    attention: t('scan.progress.status-attention'),
    excluded: t('scan.progress.status-excluded'),
  },
}))
function statusLabel(f: ScannedFileData): string {
  const map = statusTexts.value
  if (displayStage.value === 'ast') {
    if (f.state === 'SCANFING') return map.ast.SCANFING
    if (f.state === 'WATING') return map.ast.WATING
    return f.serious > 0 ? map.ast.hasIssues : map.ast.noIssues
  }
  if (f.state === 'VERIFYING') return map.review.VERIFYING
  if (f.state === 'WATING') return map.review.WATING
  // COMPLETE: acNum < totalNum still means something needs attention
  return (f.acNum ?? 0) < (f.totalNum ?? 0) ? map.review.attention : map.review.excluded
}
function dotLabel(f: ScannedFileData): string {
  if (f.state === 'SCANFING') return 'status-scan'
  if (f.state === 'VERIFYING') return 'status-review'
  if (f.state === 'COMPLETE') {
    return (f.acNum ?? 0) < (f.totalNum ?? 0) ? 'status-attention' : 'status-pass'
  }
  if (f.state === 'WATING') return ''
  return f.serious > 0 ? 'status-attention' : f.state === 'COMPLETE' ? 'status-pass' : ''
}
</script>

<template>
  <LightDialog v-model:open="open" width="1020px" min-height="620px" padding="20px 22px 8px">
    <template #title>
      <span style="font-size: 16px; font-weight: 500">
        {{ isDeep ? t('scan.steps.deep-detection') : t('scan.steps.quick-detection') }} -
        {{ projectName }}
      </span>
    </template>
    <template #extra>
      <span></span>
    </template>
    <div class="shell">
      <!-- Stage tabs -->
      <div class="rtabs">
        <div class="rtabs-row" :style="{ gridTemplateColumns: `repeat(${allStages.length}, 1fr)` }">
          <button
            v-for="st in allStages"
            :key="st.key"
            class="rtab"
            :class="{ active: displayStage === st.key, done: isStageDone(st.key) }"
            @click="switchStage(st.key)"
          >
            <span class="rtab-num">{{ st.num }}</span
            >{{ st.title }} ·
            {{
              stageState(st.key) === 'done'
                ? t('scan.progress.stage-done')
                : stageState(st.key) === 'active'
                  ? t('scan.progress.stage-active')
                  : t('scan.progress.stage-pending')
            }}
            <span class="rtab-pct">{{ stagePct(st.key) }}%</span>
          </button>
        </div>
        <!-- Stage progress bar -->
        <div class="stage-bar">
          <div v-for="st in allStages" :key="st.key" class="stage-bar-seg">
            <div class="stage-bar-fill" :style="{ width: `${stagePct(st.key)}%` }" />
          </div>
        </div>
      </div>

      <!-- Unzip stage panel (the backend pushes no unzip info, so just show the empty state) -->
      <div v-if="displayStage === 'extract'" class="sp sp-empty">
        <div class="empty-state">
          <div class="empty-icon">✓</div>
          <div class="empty-title">{{ t('scan.progress.extract-done') }}</div>
          <div class="empty-desc">{{ t('scan.progress.extract-done-note') }}</div>
        </div>
      </div>

      <!-- Raw data + metrics -->
      <div v-else class="sp" style="padding: 0; overflow: auto">
        <div class="fh" style="padding: 0 0 10px">
          <div class="im">
            <div class="mtr">
              <span class="mtr-label">{{
                displayStage === 'review'
                  ? t('scan.progress.metric-reviewed')
                  : t('scan.progress.metric-scanned')
              }}</span
              ><span class="mtr-value">{{
                displayStage === 'review'
                  ? `${reviewDone} / ${reviewTotal}`
                  : `${completed} / ${total}`
              }}</span>
            </div>
            <div class="mtr">
              <span class="mtr-label">{{ t('scan.progress.metric-issue-files') }}</span
              ><span class="mtr-value attn">{{ issueFiles }}</span>
            </div>
            <div class="mtr">
              <span class="mtr-label">{{ t('scan.progress.metric-issues') }}</span
              ><span class="mtr-value attn">{{ totalIssues }}</span>
            </div>
            <div v-if="displayStage === 'review'" class="mtr">
              <span class="mtr-label">{{ t('scan.progress.metric-excluded-count') }}</span
              ><span class="mtr-value">{{ excludedCount }}</span>
            </div>
          </div>
          <div class="vc">
            <button
              v-for="fb in filterButtons"
              :key="fb.key"
              class="btn"
              :class="{ 'btn-p': filter === fb.key }"
              @click="filter = fb.key"
            >
              {{ fb.label }}
            </button>
          </div>
        </div>
        <div v-if="true" ref="scrollRef" class="tbl-scroll">
          <table class="scan-table">
            <thead>
              <tr>
                <th class="col-file">{{ t('scan.progress.col-file') }}</th>
                <th class="col-status">
                  {{
                    displayStage === 'ast'
                      ? t('scan.progress.col-scan-status')
                      : t('scan.progress.col-review-status')
                  }}
                </th>
                <th class="col-issues">{{ t('scan.progress.col-issues') }}</th>
              </tr>
            </thead>
            <tbody>
              <tr
                v-for="f in sortedFiles"
                :key="f.filePath"
                :data-file="f.filePath"
                :class="{ 'is-issue': f.serious > 0 }"
              >
                <td>
                  <div class="fc">
                    <div class="fcp">
                      <div class="fcn">{{ f.fileName }}</div>
                      <div class="fcp2">{{ f.filePath }}</div>
                    </div>
                  </div>
                </td>
                <td class="col-status">
                  <div class="sc" :class="f._dotCls"><span class="sd" />{{ f._statusText }}</div>
                </td>
                <td
                  class="col-issues"
                  :class="{
                    isn: displayStage === 'review' ? (f.totalNum ?? 0) > 0 : f.serious > 0,
                    zn: displayStage === 'review' ? (f.totalNum ?? 0) === 0 : f.serious === 0,
                  }"
                >
                  <template v-if="displayStage === 'review' && f.state !== 'WATING'">
                    {{ f.totalNum ?? f.serious ?? 0
                    }}<span v-if="f.acNum && f.acNum > 0" class="ec"
                      >（{{ f._excludedNote }}）</span
                    >
                  </template>
                  <template v-else>
                    {{
                      displayStage === 'review' ? (f.totalNum ?? f.serious ?? 0) : f.serious || 0
                    }}
                  </template>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>
    </div>
  </LightDialog>
</template>

<style lang="scss" scoped>
.shell {
  display: flex;
  flex-direction: column;
  height: 600px;
  overflow: hidden;
}

// ===== Header =====
.rh {
  padding: 20px 0 16px;
  border-bottom: 1px solid rgba(26, 28, 31, 0.08);
}
.rs {
  display: flex;
  gap: 24px;
  align-items: flex-end;
  & > :first-child {
    flex: 1;
    min-width: 0;
  }
}
.eyebrow {
  margin-bottom: 4px;
  font-size: 11px;
  color: #757a82;
  text-transform: uppercase;
  letter-spacing: 0.06em;
}
h3 {
  margin: 0 0 4px;
  overflow: hidden;
  text-overflow: ellipsis;
  font-size: 19px;
  font-weight: 500;
  color: #1a1c1f;
  white-space: nowrap;
}
.rnote {
  font-size: 12px;
  color: #757a82;
}
.rp {
  flex: 0 1 300px;
  min-width: 180px;
}
.rpl {
  display: flex;
  justify-content: space-between;
  font-size: 12px;
  span {
    color: #757a82;
  }
  strong {
    font-weight: 500;
    color: #1a1c1f;
  }
}
.rpb {
  height: 5px;
  margin-top: 8px;
  overflow: hidden;
  background: rgba(26, 28, 31, 0.08);
  border-radius: 999px;
  span {
    display: block;
    height: 100%;
    background: #3578ff;
    transition: width 0.3s ease;
  }
}

// ===== Stage tabs =====
.rtabs {
  display: flex;
  flex-direction: column;
  margin-bottom: 12px;
  border-bottom: 0;
}
.rtabs-row {
  display: grid;
  grid-template-columns: repeat(auto-fit, 1fr);
}
.rtab {
  display: flex;
  align-items: center;
  padding: 11px 16px;
  margin: 0;
  font: inherit;
  font-size: 13px;
  color: #757a82;
  text-align: left;
  appearance: none;
  cursor: pointer;
  background: transparent;
  border: 0;
  border-right: 1px solid rgba(26, 28, 31, 0.08);
  border-radius: 0;
  &:last-child {
    border-right: 0;
  }
  &:hover {
    color: #1a1c1f;
    background: rgba(26, 28, 31, 0.03);
  }
  &.done {
    color: #1a1c1f;
  }
  &.active {
    color: #1a1c1f;
    background: rgba(53, 120, 255, 0.07);
  }
}
.rtab-num {
  margin-right: 6px;
  color: #757a82;
}

// ===== Panels =====
.sp {
  display: flex;
  flex: 1;
  flex-direction: column;
  min-height: 0;
  overflow: hidden;
}

.rtab-pct {
  margin-left: auto;
  font-size: 13px;
  font-weight: 500;
  color: #3578ff;
}

// Stage progress bar
.stage-bar {
  display: flex;
  gap: 0;
  height: 3px;
  overflow: hidden;
  background: rgba(26, 28, 31, 0.06);
}
.stage-bar-seg {
  flex: 1;
}
.stage-bar-fill {
  height: 100%;
  background: #3578ff;
  transition: width 0.3s ease;
}

// Statistics + filter
.fh {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  align-items: center;
  padding: 14px 22px 0;
}
.im {
  display: flex;
  flex-wrap: wrap;
  gap: 0;
}
.mtr {
  display: flex;
  gap: 7px;
  align-items: baseline;
  padding: 0 16px;
  white-space: nowrap;
  border-right: 1px solid rgba(26, 28, 31, 0.08);
  &:first-child {
    padding-left: 0;
  }
  &:last-child {
    padding-right: 0;
    border-right: 0;
  }
}
.mtr-label {
  font-size: 12px;
  color: #757a82;
}
.mtr-value {
  font-size: 14px;
  font-weight: 500;
  color: #1a1c1f;
  &.attn {
    color: #e25507;
  }
}
.vc {
  display: flex;
  gap: 6px;
  margin-left: auto;
}
.btn {
  padding: 4px 12px;
  font-size: 12px;
  color: #757a82;
  cursor: pointer;
  background: rgba(26, 28, 31, 0.05);
  border: 1px solid transparent;
  border-radius: 6px;
  &.btn-p {
    color: #fff;
    background: #1a1c1f;
  }
  &:hover:not(.btn-p) {
    background: rgba(26, 28, 31, 0.08);
  }
}

// Current file
.curf {
  display: flex;
  gap: 6px;
  align-items: center;
  padding: 8px 22px 0;
  font-size: 12px;
  color: #757a82;
}
.curf-dot {
  flex-shrink: 0;
  width: 6px;
  height: 6px;
  background: #f3883b;
  border-radius: 50%;
  animation: pulse 1.2s infinite;
}
@keyframes pulse {
  0%,
  100% {
    opacity: 1;
  }
  50% {
    opacity: 0.3;
  }
}
.curf-name {
  font-weight: 500;
  color: #1a1c1f;
}
.curf-path {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.tbl-scroll {
  flex: 1;
  min-height: 0;
  overflow: auto;
}
table {
  width: 100%;
  table-layout: fixed;
  border-collapse: collapse;
}
th {
  position: sticky;
  top: 0;
  z-index: 1;
  padding: 9px 10px;
  font-size: 12px;
  font-weight: 400;
  color: #757a82;
  text-align: center;
  background: #fff;
  border-bottom: 1px solid rgba(26, 28, 31, 0.08);
}
td {
  padding: 11px 10px;
  font-size: 13px;
  vertical-align: middle;
  border-bottom: 1px solid rgba(26, 28, 31, 0.08);
}
tbody tr:last-child td {
  border-bottom: 0;
}
tbody tr.is-issue {
  background: rgba(226, 85, 7, 0.09);
  td:first-child {
    box-shadow: inset 2px 0 0 #e25507;
  }
}
.col-file {
  width: 63%;
}
.col-status {
  width: 22%;
  text-align: center;
}
.col-issues {
  width: 15%;
  text-align: center;
}
.isn {
  font-weight: 500;
  color: #e25507;
}
.zn {
  color: #757a82;
}

// File cell
.fc {
  display: flex;
  gap: 9px;
  align-items: center;
  min-width: 0;
}
.fcp {
  min-width: 0;
}
.fcn {
  font-weight: 500;
}
.fcp2 {
  margin-top: 2px;
  overflow: hidden;
  text-overflow: ellipsis;
  font-size: 11px;
  color: #b0b8c4;
  white-space: nowrap;
}

// Status
.sc {
  display: flex;
  gap: 8px;
  align-items: center;
  justify-content: center;
}
.sc .sd {
  flex-shrink: 0;
  width: 7px;
  height: 7px;
  background: #757a82;
  border-radius: 50%;
}
.sc.status-review .sd {
  background: #f3883b;
}
.sc.status-scan .sd {
  background: #339cff;
}
.sc.status-attention .sd {
  background: #e25507;
}
.sc.status-pass .sd {
  background: #5dc977;
}

// Cell content
.fc {
  display: flex;
  gap: 9px;
  align-items: center;
  min-width: 0;
}
.fcp {
  min-width: 0;
}
.fcn {
  overflow: hidden;
  text-overflow: ellipsis;
  font-weight: 500;
  white-space: nowrap;
}
.fcp2 {
  margin-top: 2px;
  overflow: hidden;
  text-overflow: ellipsis;
  font-size: 11px;
  color: #b0b8c4;
  white-space: nowrap;
}

// Status dot
.sc {
  display: flex;
  gap: 8px;
  align-items: center;
  justify-content: center;
}
.ec {
  margin-left: 4px;
  font-size: 11px;
  font-weight: 400;
  color: #757a82;
  white-space: nowrap;
}
.dur-text {
  font-size: 12px;
  color: #757a82;
}
.isn {
  font-weight: 500;
  color: #e25507;
}
.zn {
  color: #757a82;
}
.sd-inline {
  display: inline-block;
  width: 7px;
  height: 7px;
  margin-right: 4px;
  vertical-align: middle;
  background: #757a82;
  border-radius: 50%;
  &.status-review {
    background: #f3883b;
  }
  &.status-scan {
    background: #339cff;
  }
  &.status-attention {
    background: #e25507;
  }
  &.status-pass {
    background: #5dc977;
  }
}

// Unzip stage empty state
.sp-empty {
  align-items: center;
  justify-content: center;
}
.empty-state {
  display: flex;
  flex-direction: column;
  gap: 8px;
  align-items: center;
  padding: 40px 20px;
}
.empty-icon {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 48px;
  height: 48px;
  font-size: 22px;
  font-weight: 600;
  color: #5dc977;
  background: rgba(93, 201, 119, 0.12);
  border-radius: 50%;
}
.empty-title {
  font-size: 15px;
  font-weight: 500;
  color: #1a1c1f;
}
.empty-desc {
  font-size: 12px;
  color: #757a82;
}

// Footer
.rf {
  display: flex;
  justify-content: flex-end;
  padding: 10px 0;
  border-top: 1px solid rgba(26, 28, 31, 0.08);
}
.stop-btn {
  padding: 6px 20px;
  font-size: 13px;
  color: #e25507;
  cursor: pointer;
  background: transparent;
  border: 1px solid rgba(226, 85, 7, 0.3);
  border-radius: 6px;
  &:hover {
    background: rgba(226, 85, 7, 0.06);
  }
}
</style>

<style lang="scss">
.dark {
  .shell {
    .rh {
      border-color: rgba(255, 255, 255, 0.08);
    }
    .eyebrow {
      color: rgba(255, 255, 255, 0.35);
    }
    h3 {
      color: #fff;
    }
    .rnote {
      color: rgba(255, 255, 255, 0.5);
    }
    .rpl {
      span {
        color: rgba(255, 255, 255, 0.5);
      }
      strong {
        color: #fff;
      }
    }
    .rpb {
      background: rgba(255, 255, 255, 0.08);
      span {
        background: #4d8aff;
      }
    }
    .rtabs {
      border-color: rgba(255, 255, 255, 0.08);
    }
    .rtab {
      color: rgba(255, 255, 255, 0.5);
      border-color: rgba(255, 255, 255, 0.08);
      &:hover {
        color: #fff;
        background: rgba(255, 255, 255, 0.03);
      }
      &.done {
        color: #fff;
      }
      &.active {
        color: #fff;
        background: rgba(53, 120, 255, 0.12);
      }
    }
    .rtab-num {
      color: rgba(255, 255, 255, 0.5);
    }
    .mtr {
      border-color: rgba(255, 255, 255, 0.08);
    }
    .mtr-label {
      .rtab-pct {
        color: #4d8aff;
      }
      .stage-bar {
        background: rgba(255, 255, 255, 0.08);
      }
      .stage-bar-fill {
        background: #4d8aff;
      }
      color: rgba(255, 255, 255, 0.5);
    }
    .mtr-value {
      color: #fff;
      &.attn {
        color: #ff8549;
      }
    }
    .btn {
      color: rgba(255, 255, 255, 0.5);
      background: rgba(255, 255, 255, 0.06);
      &.btn-p {
        color: #1a1c1f;
        background: #fff;
      }
      &:hover:not(.btn-p) {
        background: rgba(255, 255, 255, 0.1);
      }
    }
    th {
      color: rgba(255, 255, 255, 0.5);
      background: #1a1d2e;
      border-color: rgba(255, 255, 255, 0.08);
    }
    td {
      border-color: rgba(255, 255, 255, 0.08);
    }
    tbody tr.is-issue {
      background: rgba(255, 133, 73, 0.09);
      td:first-child {
        box-shadow: inset 2px 0 0 #ff8549;
      }
    }
    .isn {
      color: #ff8549;
    }
    .zn {
      color: rgba(255, 255, 255, 0.5);
    }
    .fcp2 {
      color: rgba(255, 255, 255, 0.35);
    }
    .sc .sd {
      background: rgba(255, 255, 255, 0.5);
    }
    .sc.status-review .sd {
      background: #f59a56;
    }
    .sc.status-scan .sd {
      background: #83c3ff;
    }
    .sc.status-attention .sd {
      background: #ff8549;
    }
    .sc.status-pass .sd {
      background: #74d58b;
    }
    .empty-icon {
      color: #74d58b;
      background: rgba(116, 213, 139, 0.12);
    }
    .empty-title {
      color: #fff;
    }
    .empty-desc {
      color: rgba(255, 255, 255, 0.5);
    }
    .ec {
      color: rgba(255, 255, 255, 0.5);
    }
    .sd {
      background: rgba(255, 255, 255, 0.5);
      &.status-review {
        background: #f59a56;
      }
      &.status-scan {
        background: #83c3ff;
      }
      &.status-attention {
        background: #ff8549;
      }
      &.status-pass {
        background: #74d58b;
      }
    }
    .curf {
      color: rgba(255, 255, 255, 0.5);
    }
    .curf-name {
      color: #fff;
    }
    .curf-dot {
      background: #f59a56;
    }
    .stop-btn {
      color: #ff8549;
      border-color: rgba(255, 133, 73, 0.3);
      &:hover {
        background: rgba(255, 133, 73, 0.06);
      }
    }
    .rf {
      border-color: rgba(255, 255, 255, 0.08);
    }
    .empty-row {
      color: rgba(255, 255, 255, 0.5);
    }
  }
}
</style>
