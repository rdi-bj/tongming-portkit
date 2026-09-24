<script setup lang="ts">
import type { ColProps } from 'antdv-next'
import type { ScannedFileData } from '@/components/scan/ScanProgressDialog.vue'
import type { BTAppInfo } from '@/types/scan'
import { Modal } from 'antdv-next'
import dayjs from 'dayjs'
import { nextTick, onBeforeUnmount, ref, watch } from 'vue'
import {
  createAppInfo,
  deleteAppInfo,
  downloadReport,
  getAppInfoDetail,
  getAppInfoPage,
  getScanQueueList,
  parseFileInfo,
  startScan,
  updateAppInfo,
} from '@/api/scan'
import ScanProgressDialog from '@/components/scan/ScanProgressDialog.vue'
import ScanReportDialog from '@/components/scan/ScanReportDialog.vue'
import { useScanListProgressWs } from '@/composables/useScanListProgressWs'
import { useScanWebSocket } from '@/composables/useScanWebSocket'
import { download } from '@/utils/element'

const { t } = useI18n()

/**
 * `wrapperCol` is forwarded to the rendered <Col>, but antdv's ColProps type omits
 * `style`; widen it locally so the inline margin stays typed.
 */
const dialogActionsCol: ColProps & { style: Record<string, string> } = {
  style: { marginLeft: '100px' },
}
definePage({
  name: 'MigrationDetection',
  meta: {
    title: 'routes.scan.index',
    menu: false,
  },
})

const projects = ref<BTAppInfo[]>([])
const loading = ref(false)
const keyword = ref('')
const viewMode = ref<'card' | 'table'>('card')

// --- Detection queue ---
const queueDialogVisible = ref(false)
const queueList = ref<BTAppInfo[]>([])
const queueLoading = ref(false)
let queueRefreshTimer: ReturnType<typeof setInterval> | null = null

/** Load the detection queue (queued + scanning) for the queue dialog */
async function loadQueue(silent = false) {
  if (!silent) queueLoading.value = true
  try {
    const list = await getScanQueueList()
    queueList.value = Array.isArray(list) ? list : []
  } catch {
    // Fail silently: queue info is not critical, try again next time
  } finally {
    queueLoading.value = false
  }
}

function stopQueueRefresh() {
  if (queueRefreshTimer) {
    clearInterval(queueRefreshTimer)
    queueRefreshTimer = null
  }
}

function openQueueDialog() {
  queueDialogVisible.value = true
  loadQueue()
  stopQueueRefresh()
  // Refresh the queue progress every 5s while the dialog is open
  queueRefreshTimer = setInterval(loadQueue, 5000, true)
}

watch(queueDialogVisible, (val) => {
  if (!val) stopQueueRefresh()
})

/** Is the project queued (not scanning yet): status === 'CREATED' (has a taskId but no scan started) */
function isQueued(p: ProgressProjectLike): boolean {
  return p.status === 'CREATED'
}

/** Progress text inside the queue dialog:
 *  Priority: task finished > scan progress (shown as soon as the WS has data, tolerating a stale scanQueueList status)
 *          > queued > fallback
 */
function queueItemProgress(p: BTAppInfo): string {
  // Terminal state first: isTaskFinished falls back to the persistent flag (states are dropped after a WS disconnect)
  if (p.taskId && isTaskFinished(p.taskId, p.status)) {
    return t('scan.projects.status-finished')
  }
  // Scan progress comes first: show it as soon as the WS has live data (stage/progress), even when
  // scanQueueList still says CREATED (which makes isQueued match): the WS shows the real progress
  if (p.taskId && hasWsProgressData(p)) {
    return projectProgressText(p)
  }
  // Queued (not started yet / no WS data and the status has not changed)
  if (isQueued(p)) {
    return t('scan.projects.queued')
  }
  // Any other taskId → started but no WS data yet, show "scanning"
  if (p.taskId) {
    return t('scan.projects.status-scanning')
  }
  return ''
}

/**
 * Operating system option values. These strings are submitted to the backend as
 * `systemName` (see `b_t_os`), so the values themselves must stay unchanged; only
 * the displayed labels are localized through `OS_LABEL_KEYS`.
 */
const OS_VALUES = [
  'Windows',
  'Debian',
  'Ubuntu',
  'CentOS',
  '银河麒麟',
  '统信',
  '深度',
  '中科方德',
  '麒麟信安',
  '凝思安全',
  '欧拉',
]

/** Localized display labels for the Chinese OS vendors (English names are the vendors' own). */
const OS_LABEL_KEYS: Record<string, string> = {
  银河麒麟: 'scan.projects.os-kylin',
  统信: 'scan.projects.os-uniontech',
  深度: 'scan.projects.os-deepin',
  中科方德: 'scan.projects.os-fangde',
  麒麟信安: 'scan.projects.os-kylinsec',
  凝思安全: 'scan.projects.os-linx',
  欧拉: 'scan.projects.os-openeuler',
}

const osOptions = computed(() =>
  OS_VALUES.map((value) => ({
    value,
    label: OS_LABEL_KEYS[value] ? t(OS_LABEL_KEYS[value]) : value,
  })),
)

const tableColumns = [
  {
    title: t('scan.projects.title-column'),
    dataIndex: 'name',
    key: 'name',
    minWidth: 180,
    ellipsis: true,
  },
  {
    title: t('scan.projects.description-column'),
    dataIndex: 'description',
    key: 'description',
    minWidth: 200,
    ellipsis: true,
  },
  { title: t('scan.projects.status-column'), key: 'status', width: 170 },
  {
    title: t('scan.projects.package-column'),
    dataIndex: 'fileName',
    key: 'fileName',
    width: 160,
    ellipsis: true,
  },
  {
    title: t('scan.projects.create-time-column'),
    dataIndex: 'createTime',
    key: 'createTime',
    width: 180,
  },
  {
    title: t('scan.projects.detect-time-column'),
    dataIndex: 'updateTime',
    key: 'updateTime',
    width: 180,
  },
  { title: t('scan.projects.actions-column'), key: 'action', width: 160 },
]

// Create/Edit dialog
const dialogVisible = ref(false)
const editingProjectId = ref<string | null>(null)
const submitting = ref(false)
const formRef = ref()
const projectForm = ref<{
  name: string
  description: string
  systemName: string
  systemVersion: string
}>({ name: '', description: '', systemName: '', systemVersion: '' })

// Scan report dialog
const reportTaskId = ref('')
const reportProjectName = ref('')
type FileInfoMap = Record<string, { fileCount: number; lineCount: number }>
const reportFileInfo = ref<FileInfoMap | null>(null)

// Report dialog (ref pattern, matches detection report demo)
const reportDialogVisible = ref(false)
const reportDialogRef = ref<InstanceType<typeof LightDialog>>()
/** The report export is downloading */
const exportingReport = ref(false)
const scanReportRef = ref<InstanceType<typeof ScanReportDialog>>()

const reportProjectForLight = ref<BTAppInfo | null>(null)

// Step dialog
const stepDialogOpen = ref(false)
const stepProject = ref<BTAppInfo | null>(null)
const stepIndex = ref(0)
const stepFile = ref<File | null>(null)
const fileInputRef = ref<HTMLInputElement | null>(null)
/** Detection starting (file uploading): button loading + "preparing the detection environment..." */
const startingScan = ref(false)

// Detection progress dialog
const progressDialogOpen = ref(false)
const progressProjectName = ref('')

// WebSocket live progress
const scanWs = useScanWebSocket()
// --- Dialog progress state (accumulated from WS messages) ---
const scanTotal = ref(0)
const scanCompleted = ref(0)
const scanStatus = ref('')
const scanCurrentFile = ref('')
const scanLatestDoneFile = ref('')
const scanFiles = ref<ScannedFileData[]>([])
/** filePath → index fast lookup */
const scanFileIndex = ref<Map<string, number>>(new Map())
/** Standalone data of stage 03, copied from scanFiles when AI_VERIFY starts */
const scanReviewFiles = ref<ScannedFileData[]>([])
const scanReviewFileIndex = ref<Map<string, number>>(new Map())
const scanDoingMap = ref<Record<string, number>>({})
/** Start timestamp of the review stage */
const scanReviewStartMap = ref<Record<string, number>>({})
const scanVerifyCompleted = ref(0)
const scanVerifyTotal = ref(0)
/** Current stage: SCANNING | AI_VERIFY | SUCCESS | FAILED | '' */
const scanPhase = ref('')
/** Unzip progress percentage 0-100 */
const scanUnzipPercent = ref(0)
/** Current detection type: '0' = quick, '1' = deep */
const currentScanType = ref('1')
/** Incremental counters, avoiding a full filter/reduce inside ScanProgressDialog */
const scanIssueFiles = ref(0) // Files with issues
const scanTotalIssues = ref(0) // Total issues
/** Completion sequence, incremented on every COMPLETE, used for sorting */
let scanOrder = 0

watch(scanWs.latestMessage, (msg) => {
  if (!msg) return

  switch (msg.type) {
    case 'CONNECTED': {
      // On the first connect, clear and initialise; if progress data exists this is a reconnect, keep it —
      // otherwise the repeated CONNECTED would wipe the running file list and the review info would empty out
      const hasData = scanFiles.value.length > 0 || scanReviewFiles.value.length > 0
      if (!hasData) {
        scanTotal.value = 0
        scanCompleted.value = 0
        scanCurrentFile.value = ''
        scanLatestDoneFile.value = ''
        scanFiles.value = []
        scanFileIndex.value = new Map()
        scanReviewFiles.value = []
        scanReviewFileIndex.value = new Map()
        scanDoingMap.value = {}
        scanReviewStartMap.value = {}
        scanVerifyCompleted.value = 0
        scanVerifyTotal.value = 0
        scanUnzipPercent.value = 0
        scanIssueFiles.value = 0
        scanTotalIssues.value = 0
        scanOrder = 0
      }
      scanStatus.value = t('scan.progress.connected')
      break
    }
    case 'STATE_CHANGE': {
      const state = (msg as any).state as string
      scanPhase.value = state
      if (state === 'SCANNING') {
        scanStatus.value = t('scan.progress.scanning')
      } else if (state === 'AI_VERIFY') {
        scanStatus.value = t('scan.progress.ai-verifying')
        // Copy the stage 03 data from the AST completion payload
        scanReviewFiles.value = scanFiles.value
          .filter((f) => f.serious > 0)
          .map((f, i) => ({
            ...f,
            state: 'WATING',
            totalNum: f.serious,
            waitNum: f.serious,
            acNum: 0,
            order: i,
          }))
        scanReviewFileIndex.value = new Map()
        scanReviewFiles.value.forEach((f, i) => scanReviewFileIndex.value.set(f.filePath, i))
      } else if (state === 'FAILED') {
        scanStatus.value = t('scan.progress.scan-failed')
        window.$message.error(t('scan.messages.scan-failed'))
        progressDialogOpen.value = false
        loadProjects()
      } else if (state === 'SUCCESS') {
        if (scanStatus.value !== t('scan.progress.scan-complete')) {
          scanStatus.value = t('scan.progress.scan-complete')
          scanWs.disconnect() // Stop a backend-side drop from reconnecting and wiping the data
          window.$message.success(t('scan.messages.detection-complete'))
          const proj = stepProject.value
          if (proj?.id && proj.taskId && progressDialogOpen.value) {
            setTimeout(async () => {
              progressDialogOpen.value = false
              try {
                const detail = await getAppInfoDetail(proj.id)
                if (detail.taskId && isFinished(detail.status)) {
                  reportTaskId.value = detail.taskId
                  reportProjectName.value = detail.name
                  reportFileInfo.value = parseFileInfo(detail.fileInfo) as FileInfoMap | null
                  reportProjectForLight.value = detail
                  reportDialogRef.value?.open()
                  await nextTick()
                  scanReportRef.value?.loadReportData()
                }
              } catch {
                /* Silently */
              }
            }, 300)
          }
        }
      }
      break
    }
    case 'PROGRESS_UPDATE': {
      const p = msg.progress
      if (p) {
        scanTotal.value = p.total
        // scanCompleted is counted from FILE_CHANGE COMPLETE
        const label =
          scanPhase.value === 'AI_VERIFY'
            ? t('scan.progress.ai-verifying')
            : t('scan.progress.scanning')
        scanStatus.value = `${label} (${p.completed}/${p.total})`
        // Unzip progress
        if (p.unzipPercent !== undefined) {
          scanUnzipPercent.value = p.unzipPercent
        }
        // Verification progress (pushed by the backend)
        if (p.totalVerifyFileCount !== undefined) {
          scanVerifyTotal.value = p.totalVerifyFileCount
        }
        if (p.alreadyVerifyFileCount !== undefined) {
          scanVerifyCompleted.value = p.alreadyVerifyFileCount
        }
      }
      break
    }
    case 'FILE_CHANGE': {
      const d = msg as {
        fileName?: string
        path?: string
        state?: string
        errorNum?: number | null
        acNum?: number
        totalNum?: number
        waitNum?: number
      }
      const fp = d.path ?? ''
      const parts = fp.split('/')
      const fileName = parts.pop() || d.fileName || fp || ''
      const isReview =
        d.totalNum !== undefined &&
        d.totalNum > 0 &&
        (d.state === 'WATING' || d.state === 'VERIFYING' || d.state === 'COMPLETE')

      if (isReview) {
        // --- Stage 03: update scanReviewFiles ---
        const existingIdx = scanReviewFileIndex.value.get(fp) ?? -1
        const existing = existingIdx >= 0 ? scanReviewFiles.value[existingIdx] : null

        function upsertReview(entry: ScannedFileData) {
          if (existingIdx >= 0) {
            scanReviewFiles.value[existingIdx] = entry
          } else {
            const idx = scanReviewFiles.value.push(entry) - 1
            scanReviewFileIndex.value.set(fp, idx)
          }
        }

        if (d.state === 'WATING') {
          upsertReview({
            fileName,
            filePath: fp,
            timeConsuming: 0,
            scanDuration: existing?.scanDuration ?? 0,
            reviewDuration: existing?.reviewDuration ?? 0,
            minor: 0,
            medium: 0,
            serious: d.errorNum ?? 0,
            totalNum: d.totalNum ?? 0,
            waitNum: d.waitNum ?? 0,
            acNum: d.acNum ?? 0,
            state: 'WATING',
          })
        } else if (d.state === 'VERIFYING') {
          scanCurrentFile.value = fp
          scanReviewStartMap.value[fp] = Date.now()
          upsertReview({
            fileName,
            filePath: fp,
            timeConsuming: 0,
            scanDuration: existing?.scanDuration ?? existing?.timeConsuming ?? 0,
            reviewDuration: 0,
            minor: 0,
            medium: 0,
            serious: d.errorNum ?? 0,
            totalNum: d.totalNum ?? 0,
            waitNum: d.waitNum ?? 0,
            acNum: d.acNum ?? 0,
            state: 'VERIFYING',
          })
        } else if (d.state === 'COMPLETE') {
          scanLatestDoneFile.value = fp
          const reviewStartTs = scanReviewStartMap.value[fp]
          const now = Date.now()
          const newReviewDur = reviewStartTs ? now - reviewStartTs : (existing?.reviewDuration ?? 0)
          const entry: ScannedFileData = {
            fileName,
            filePath: fp,
            timeConsuming: newReviewDur,
            scanDuration: existing?.scanDuration ?? 0,
            reviewDuration: newReviewDur,
            minor: 0,
            medium: 0,
            serious: d.errorNum ?? existing?.serious ?? 0,
            totalNum: d.totalNum ?? existing?.totalNum ?? 0,
            waitNum: d.waitNum ?? 0,
            acNum: d.acNum ?? existing?.acNum ?? 0,
            state: 'COMPLETE',
            order: ++scanOrder,
          }
          upsertReview(entry)
        }
        break
      }

      // --- Stage 02: update scanFiles ---
      const existingIdx = scanFileIndex.value.get(fp) ?? -1
      const existing = existingIdx >= 0 ? scanFiles.value[existingIdx] : null

      function upsert(entry: ScannedFileData) {
        if (existingIdx >= 0) {
          scanFiles.value[existingIdx] = entry
        } else {
          const idx = scanFiles.value.push(entry) - 1
          scanFileIndex.value.set(fp, idx)
        }
      }

      if (d.state === 'WATING') {
        upsert({
          fileName,
          filePath: fp,
          timeConsuming: 0,
          scanDuration: existing?.scanDuration ?? 0,
          reviewDuration: existing?.reviewDuration ?? 0,
          minor: 0,
          medium: 0,
          serious: d.errorNum ?? 0,
          totalNum: d.totalNum ?? 0,
          waitNum: d.waitNum ?? 0,
          acNum: d.acNum ?? 0,
          state: 'WATING',
        })
      } else if (d.state === 'SCANFING') {
        scanCurrentFile.value = fp
        scanDoingMap.value[fp] = Date.now()
        upsert({
          fileName,
          filePath: fp,
          timeConsuming: 0,
          scanDuration: existing?.scanDuration ?? 0,
          reviewDuration: existing?.reviewDuration ?? 0,
          minor: 0,
          medium: 0,
          serious: d.errorNum ?? 0,
          totalNum: d.totalNum ?? 0,
          waitNum: d.waitNum ?? 0,
          acNum: d.acNum ?? 0,
          state: 'SCANFING',
        })
      } else if (d.state === 'COMPLETE') {
        scanLatestDoneFile.value = fp
        const scanStartTs = scanDoingMap.value[fp]
        const now = Date.now()
        const newScanDur = scanStartTs ? now - scanStartTs : (existing?.scanDuration ?? 0)
        const entry: ScannedFileData = {
          fileName,
          filePath: fp,
          timeConsuming: newScanDur,
          scanDuration: newScanDur,
          reviewDuration: existing?.reviewDuration ?? 0,
          minor: 0,
          medium: 0,
          serious: d.errorNum ?? existing?.serious ?? 0,
          totalNum: d.totalNum ?? existing?.totalNum ?? 0,
          waitNum: d.waitNum ?? 0,
          acNum: d.acNum ?? existing?.acNum ?? 0,
          state: 'COMPLETE',
          order: ++scanOrder,
        }
        if (existingIdx >= 0) {
          if (existing?.state !== 'COMPLETE') {
            scanCompleted.value++
            if ((d.errorNum ?? 0) > 0) scanIssueFiles.value++
            scanTotalIssues.value += d.errorNum ?? 0
          }
          scanFiles.value[existingIdx] = entry
        } else {
          scanCompleted.value++
          const s = d.errorNum ?? 0
          if (s > 0) scanIssueFiles.value++
          scanTotalIssues.value += s
          scanFiles.value.push(entry)
          scanFileIndex.value.set(fp, scanFiles.value.length - 1)
        }
      }
      break
    }
    case 'FINISHED': {
      if (scanStatus.value === t('scan.progress.scan-complete')) break
      scanStatus.value = t('scan.progress.scan-complete')
      scanWs.disconnect() // Stop a backend-side drop from reconnecting and wiping the data
      window.$message.success(t('scan.messages.detection-complete'))
      // Jump to the detail report after a delay
      const proj = stepProject.value
      if (proj?.id && proj.taskId && progressDialogOpen.value) {
        setTimeout(async () => {
          progressDialogOpen.value = false
          try {
            const detail = await getAppInfoDetail(proj.id)
            if (detail.taskId && isFinished(detail.status)) {
              reportTaskId.value = detail.taskId
              reportProjectName.value = detail.name
              reportFileInfo.value = parseFileInfo(detail.fileInfo) as FileInfoMap | null
              reportProjectForLight.value = detail
              reportDialogRef.value?.open()
              await nextTick()
              scanReportRef.value?.loadReportData()
            }
          } catch {
            // Fail silently
          }
        }, 300)
      }
      break
    }
    default:
      console.warn('[WS]', msg.type, msg)
  }
})

async function loadProjects(silent = false) {
  if (!silent) loading.value = true
  try {
    const result = await getAppInfoPage(1, -1, keyword.value || undefined)
    projects.value = result.records
    syncListProgressWs()
  } catch (error) {
    // Swallow polling failures: retry on the next poll or on a manual refresh
    if (!silent) {
      window.$message.error(error instanceof Error ? error.message : t('scan.messages.load-failed'))
    }
  } finally {
    loading.value = false
  }
}

function handleSearch() {
  loadProjects()
}

function resetForm() {
  projectForm.value = { name: '', description: '', systemName: '', systemVersion: '' }
}

function openCreateDialog() {
  editingProjectId.value = null
  resetForm()
  formRef.value?.resetFields()
  dialogVisible.value = true
}

async function openEditDialog(row: BTAppInfo) {
  try {
    const detail = await getAppInfoDetail(row.id)
    editingProjectId.value = detail.id
    projectForm.value.name = detail.name
    projectForm.value.description = detail.description ?? ''
    projectForm.value.systemName = detail.systemName ?? ''
    projectForm.value.systemVersion = detail.systemVersion ?? ''
    formRef.value?.resetFields()
    dialogVisible.value = true
  } catch (error) {
    window.$message.error(error instanceof Error ? error.message : t('scan.messages.detail-failed'))
  }
}

async function submitProject() {
  if (!projectForm.value.name.trim()) {
    window.$message.warning(t('scan.messages.name-required'))
    return
  }
  if (!projectForm.value.systemName) {
    window.$message.warning(t('scan.messages.os-required'))
    return
  }
  submitting.value = true
  try {
    if (editingProjectId.value) {
      await updateAppInfo(
        editingProjectId.value,
        projectForm.value.name.trim(),
        projectForm.value.description.trim() || undefined,
        projectForm.value.systemName,
        projectForm.value.systemVersion.trim() || undefined,
      )
      window.$message.success(t('scan.messages.update-success'))
    } else {
      await createAppInfo(
        projectForm.value.name.trim(),
        projectForm.value.description.trim() || undefined,
        projectForm.value.systemName,
        projectForm.value.systemVersion.trim() || undefined,
      )
      window.$message.success(t('scan.messages.create-success'))
    }
    // Keep the project name in the step dialog in sync
    if (
      stepDialogOpen.value &&
      stepProject.value &&
      editingProjectId.value === stepProject.value.id
    ) {
      stepProject.value = {
        ...stepProject.value,
        name: projectForm.value.name.trim(),
        description: projectForm.value.description.trim(),
        systemName: projectForm.value.systemName,
        systemVersion: projectForm.value.systemVersion.trim() || undefined,
      }
    }
    dialogVisible.value = false
    editingProjectId.value = null
    resetForm()
    await loadProjects()
  } catch (error) {
    window.$message.error(
      error instanceof Error
        ? error.message
        : editingProjectId.value
          ? t('scan.messages.update-failed')
          : t('scan.messages.create-failed'),
    )
  } finally {
    submitting.value = false
  }
}

async function removeProject(row: BTAppInfo) {
  const detecting = isDetecting(row)
  const adapting = row.adaptStatus === '1'
  let content: string
  if (detecting && adapting) {
    content = t('scan.projects.delete-confirm-busy')
  } else if (detecting) {
    content = t('scan.projects.delete-confirm-scanning')
  } else if (adapting) {
    content = t('scan.projects.delete-confirm-adapting')
  } else {
    content = t('scan.projects.delete-confirm', { name: row.name })
  }
  Modal.confirm({
    title: t('scan.projects.delete-title'),
    content,
    okText: t('scan.projects.delete-ok'),
    cancelText: t('scan.projects.cancel-text'),
    okButtonProps: { danger: true },
    onOk: async () => {
      try {
        await deleteAppInfo(row.id)
        window.$message.success(t('scan.messages.delete-success'))
        await loadProjects()
      } catch (error) {
        window.$message.error(
          error instanceof Error ? error.message : t('scan.messages.delete-failed'),
        )
      }
    },
  })
}

function resetScanState() {
  scanTotal.value = 0
  scanCompleted.value = 0
  scanStatus.value = ''
  scanCurrentFile.value = ''
  scanFiles.value = []
  scanFileIndex.value = new Map()
  scanReviewFiles.value = []
  scanReviewFileIndex.value = new Map()
  scanDoingMap.value = {}
  scanReviewStartMap.value = {}
  scanVerifyCompleted.value = 0
  scanVerifyTotal.value = 0
  scanPhase.value = ''
  scanUnzipPercent.value = 0
  scanIssueFiles.value = 0
  scanTotalIssues.value = 0
  scanOrder = 0
}

function handleStopScan() {
  progressDialogOpen.value = false
  scanWs.disconnect()
}

async function openReportDialog(row: BTAppInfo) {
  try {
    const detail = await getAppInfoDetail(row.id)
    if (detail.taskId && isFinished(detail.status)) {
      // Scan finished → open the detection report dialog
      reportTaskId.value = detail.taskId
      reportProjectName.value = detail.name
      reportFileInfo.value = parseFileInfo(detail.fileInfo) as FileInfoMap | null
      reportProjectForLight.value = detail
      reportDialogRef.value?.open()
      await nextTick()
      scanReportRef.value?.loadReportData()
    } else if (detail.taskId) {
      // Scanning → open the progress dialog + WebSocket, guessing the stage from status
      resetScanState()
      // status guess: map the backend project status to scanPhase; a later WS STATE_CHANGE corrects it
      if (detail.status === 'SCANNING') scanPhase.value = 'SCANNING'
      else if (detail.status === 'AI_VERIFY') scanPhase.value = 'AI_VERIFY'
      else scanPhase.value = 'SCANNING' // Fallback
      stepProject.value = detail
      progressProjectName.value = detail.name
      scanWs.connect(detail.taskId)
      progressDialogOpen.value = true
    } else {
      // Not scanned → show the step wizard (a source package must be uploaded)
      stepProject.value = detail
      stepFile.value = null
      stepIndex.value = 0
      stepDialogOpen.value = true
    }
  } catch (error) {
    window.$message.error(error instanceof Error ? error.message : t('scan.messages.detail-failed'))
  }
}

function handlePickFile() {
  fileInputRef.value?.click()
}

/** Backend upload limit: 500MB (above it the backend answers 500 and drops the connection, which fetch surfaces as a NetworkError) */
const MAX_UPLOAD_SIZE_MB = 500
const MAX_UPLOAD_SIZE = MAX_UPLOAD_SIZE_MB * 1024 * 1024

function onFilePicked(e: Event) {
  const input = e.target as HTMLInputElement
  const file = input.files?.[0]
  if (!file) return
  if (!file.name.endsWith('.zip')) {
    window.$message.warning(t('scan.steps.select-zip'))
  } else if (file.size > MAX_UPLOAD_SIZE) {
    // 500MB backend limit: the backend drops the connection first and fetch gets no response, so block it here with a clear message
    window.$message.warning(t('scan.messages.file-too-large', { size: MAX_UPLOAD_SIZE_MB }))
  } else {
    stepFile.value = file
    stepIndex.value = 1
  }
  input.value = ''
}

function handleStep1Rewrite() {
  const proj = stepProject.value
  if (!proj) return
  editingProjectId.value = proj.id
  projectForm.value.name = proj.name
  projectForm.value.description = proj.description ?? ''
  projectForm.value.systemName = proj.systemName ?? ''
  projectForm.value.systemVersion = proj.systemVersion ?? ''
  dialogVisible.value = true
}

function handleResetStep() {
  stepIndex.value = 0
  stepFile.value = null
}

function handleReportReScan() {
  reportDialogRef.value?.close()
  const proj = reportProjectForLight.value
  if (!proj) return
  stepProject.value = proj
  stepFile.value = null
  // Re-scanning requires uploading the source package again
  stepIndex.value = 0
  stepDialogOpen.value = true
}

async function handleExportReport() {
  const proj = reportProjectForLight.value
  if (!proj?.id || exportingReport.value) return
  exportingReport.value = true
  try {
    const { blob, filename } = await downloadReport(proj.id, '0')
    // Prefer the file name from the backend Content-Disposition, fall back to a local one
    download(blob, filename ?? `${reportProjectName.value}${t('scan.report.file-suffix')}.docx`)
  } catch (error) {
    window.$message.error(error instanceof Error ? error.message : t('scan.report.export-failed'))
  } finally {
    exportingReport.value = false
  }
}

async function handleStartScan(scanType: string = '0') {
  const proj = stepProject.value
  if (!proj?.id) return
  if (!stepFile.value) {
    window.$message.warning(t('scan.messages.upload-required'))
    return
  }
  if (stepFile.value.size > MAX_UPLOAD_SIZE) {
    // Defensive: the selection may change through other paths, so check once more before submitting
    window.$message.warning(t('scan.messages.file-too-large', { size: MAX_UPLOAD_SIZE_MB }))
    return
  }
  if (startingScan.value) return
  // Remember the current detection type
  currentScanType.value = scanType
  // Reset the counters of the previous scan
  resetScanState()
  // Clear a message that may still be showing
  try {
    window.$message?.destroy()
  } catch {}
  // Show button loading while the file uploads / the environment is prepared (large files are slow)
  startingScan.value = true
  try {
    progressProjectName.value = proj.name
    // Start the scan: the backend returns a taskId and queues the job automatically
    const taskId = await startScan(stepFile.value, proj.id, scanType)
    // Update stepProject.taskId first, otherwise an early WS message is rejected by the guard
    stepProject.value = { ...proj, taskId }
    stepFile.value = null
    // Check the queue: open the progress view only when this app is first in line, otherwise report the queue position
    const queue = await getScanQueueList().catch(() => null)
    const isQueueHead = !queue || queue.length === 0 || queue[0]?.id === proj.id
    stepDialogOpen.value = false
    if (isQueueHead) {
      // First in line: report the start and open the progress view (avoid two popups at once)
      window.$message.success(t('scan.messages.scan-started'))
      // Connect the WebSocket
      scanWs.connect(taskId)
      progressDialogOpen.value = true
    } else {
      // Queued: tell the user to check the queue, with a shortcut to the queue dialog
      Modal.confirm({
        title: t('scan.messages.scan-queued-title'),
        content: t('scan.messages.scan-queued'),
        okText: t('scan.projects.view-queue'),
        cancelText: t('scan.messages.scan-queued-close'),
        okButtonProps: { type: 'primary' },
        centered: true,
        onOk() {
          openQueueDialog()
        },
        onCancel() {},
        afterClose() {},
      })
      loadProjects()
      loadQueue(true)
    }
  } catch (error) {
    // An over-limit upload or a network error drops the connection early and fetch throws a NetworkError without a body, so translate it
    const msg = error instanceof Error ? error.message : ''
    const isNetworkError =
      /networkerror|failed to fetch|fetch failed|load failed|network request failed|网络错误/i.test(
        msg,
      )
    window.$message.error(
      isNetworkError
        ? t('scan.messages.upload-network-error')
        : msg || t('scan.messages.scan-failed'),
    )
  } finally {
    startingScan.value = false
  }
}

loadProjects()

// Refresh the list after the report dialog closes
watch(reportDialogVisible, (val) => {
  if (!val) loadProjects()
})

// Disconnect the WebSocket when the progress dialog closes so the backend stops recomputing
watch(progressDialogOpen, (val) => {
  if (!val) scanWs.disconnect()
})

function isFinished(status: string): boolean {
  return status === 'FINISHED' || status === 'SUCCESS'
}

// --- List "view details" button: disabled until the scan finishes, showing the progress as text ---

/** Minimum fields needed to compute progress (works with the AnyObject record of the table bodyCell slot) */
interface ProgressProjectLike {
  id?: string
  taskId?: string | null
  status?: string
}

/** List-page project progress WebSocket (one connection per scanning project, heartbeat + auto reconnect) */
const listProgressWs = useScanListProgressWs()
/** Task ids that already reached FINISHED / a terminal state: avoids an immediate reconnect after a list refresh and keeps the flag when WS state is cleared */
const finishedTaskIds = new Set<string>()

/** Live WS stage of a task (the queue dialog uses it to cover the lag of scanQueueList polling; empty = no data yet) */
function queueItemWsPhase(taskId: string): string {
  return listProgressWs.states.value[taskId]?.phase ?? ''
}

/** Whether a task reached a terminal state: the persistent finishedTaskIds flag comes first, covering a WS disconnect that cleared the states */
function isTaskFinished(taskId: string, status?: string | null): boolean {
  if (finishedTaskIds.has(taskId)) return true
  const wsPhase = queueItemWsPhase(taskId)
  if (wsPhase === 'SUCCESS' || wsPhase === 'FINISHED' || wsPhase === 'FAILED') return true
  return isFinished(status ?? '')
}

/** Whether a project is being scanned (has a task that is not finished) */
function isDetecting(p: ProgressProjectLike): boolean {
  if (!p.taskId) return false
  // Queued (CREATED): the backend assigned a taskId but the scan has not started, so it is not scanning
  if (p.status === 'CREATED') return false
  // A task confirmed terminal (the WS reported success/failure) no longer counts as scanning; the flag survives WS state cleanup
  if (finishedTaskIds.has(p.taskId)) return false
  if (isFinished(p.status ?? '')) return false
  // The WS confirmed a terminal state: show the finished state right away, the list status refresh stays authoritative
  const wsPhase = listProgressWs.states.value[p.taskId]?.phase
  if (wsPhase === 'SUCCESS' || wsPhase === 'FINISHED' || wsPhase === 'FAILED') return false
  return true
}

/** Current stage info of a project (consistent with ScanProgressDialog): the live WS stage first, then the list status */
function projectStage(p: ProgressProjectLike): { num: string; title: string } | null {
  if (!p.taskId) return null
  const wsPhase = listProgressWs.states.value[p.taskId]?.phase
  const phase = wsPhase || p.status || ''
  // Known terminal state (success/failure): the task is over and belongs to no running stage
  if (phase === 'FINISHED' || phase === 'SUCCESS' || phase === 'FAILED') return null
  if (phase === 'AI_VERIFY') return { num: '03', title: t('scan.progress.stage-review') }
  if (phase === 'SCANNING') return { num: '02', title: t('scan.progress.stage-ast') }
  // Other states (unzipping, queued) fall back to stage 01, source unzip
  return { num: '01', title: t('scan.progress.stage-extract') }
}

/** Percentage of the current stage (computed per stage, aligned with ScanProgressDialog.stagePct) */
/** Percentage of the current stage (per stage, aligned with ScanProgressDialog.stagePct); null while the WS has no progress data yet */
function projectProgressPercent(p: ProgressProjectLike): number | null {
  const stage = projectStage(p)
  const prog = p.taskId ? listProgressWs.states.value[p.taskId]?.progress : undefined
  if (!stage || !prog) return null
  if (stage.num === '01') {
    return Math.min(100, prog.unzipPercent ?? 0)
  }
  if (stage.num === '02') {
    const total = prog.total ?? 0
    // completed may arrive as a serialised AtomicInteger object, so read it defensively
    const completed =
      typeof prog.completed === 'object'
        ? ((prog.completed as { plain?: number } | null)?.plain ?? 0)
        : (prog.completed ?? 0)
    return total > 0 ? Math.min(100, Math.round((completed / total) * 100)) : 0
  }
  const vTotal = prog.totalVerifyFileCount ?? 0
  const vDone = prog.alreadyVerifyFileCount ?? 0
  return vTotal > 0 ? Math.min(100, Math.round((vDone / vTotal) * 100)) : 0
}

/** Whether the WS data of this task arrived after a refresh (either stage or progress) */
function hasWsProgressData(p: ProgressProjectLike): boolean {
  if (!p.taskId) return false
  const st = listProgressWs.states.value[p.taskId]
  return !!st && (!!st.phase || !!st.progress)
}

/** Progress text such as "progress: AST check 20%" (stage names match the progress dialog, without the index); no percentage while the WS has no data */
function projectProgressText(p: ProgressProjectLike): string {
  if (!p.taskId) return ''
  // After a refresh, if the WS returned no stage/progress data yet, do not guess the stage, say it is being fetched
  if (!hasWsProgressData(p)) {
    return t('scan.projects.fetching-progress')
  }
  const stage = projectStage(p)
  if (!stage) {
    // Terminal task (success/failure): show no progress text (nor the "fetching" hint, which only appears when the WS data is still missing after a refresh)
    return ''
  }
  const percent = projectProgressPercent(p)
  if (percent === null) {
    return t('scan.projects.detecting-progress-no-percent', {
      stage: stage.title,
    })
  }
  return t('scan.projects.detecting-progress', {
    stage: stage.title,
    percent,
  })
}

/** Sync the WS connections with the current list: add scanning/queued projects, drop finished/failed ones */
function syncListProgressWs() {
  const wanted = new Set<string>()
  for (const p of projects.value) {
    // Queued (CREATED) projects stay connected too: the backend pushes STATE_CHANGE when the task starts,
    // which triggers the watcher below to refresh the list, so a quick small project does not stay "queued"
    if (p.taskId && (isDetecting(p) || isQueued(p)) && !finishedTaskIds.has(p.taskId)) {
      wanted.add(p.taskId)
    }
  }
  for (const taskId of listProgressWs.activeTaskIds.value) {
    if (!wanted.has(taskId)) {
      listProgressWs.disconnect(taskId)
    }
  }
  // Reclaim finishedTaskIds entries only when the project left the list (no matching record, e.g. re-scanned with a new taskId or deleted)
  // Keep the flag for finished projects that are still listed — the list status is terminal already, but the queue dialog
  // (scanQueueList) may still show a stale non-terminal status, and dropping the flag would make it fall back to "scanning"
  for (const taskId of [...finishedTaskIds]) {
    const proj = projects.value.find((p) => p.taskId === taskId)
    if (!proj) {
      finishedTaskIds.delete(taskId)
    }
  }
  for (const taskId of wanted) {
    listProgressWs.connect(taskId)
  }
}

// WS FINISHED: close that task connection and refresh the list (the button becomes available again)
// Stage change (including the SUCCESS/FAILED terminal states): refresh the list silently and let its status drive the finish state
watch(
  () => listProgressWs.states.value,
  (states, prev) => {
    let hadFinished = false
    for (const [taskId, st] of Object.entries(states)) {
      if (st.finished) {
        hadFinished = true
        finishedTaskIds.add(taskId)
        listProgressWs.disconnect(taskId)
      }
    }
    // Walk every task and freeze the flag of each terminal one (no break, so several tasks finishing
    // at once are all recorded and none falls back to "scanning")
    let phaseChanged = false
    for (const [taskId, st] of Object.entries(states)) {
      if (st.phase && st.phase !== prev?.[taskId]?.phase) {
        // Terminal state (success/failure): freeze the flag
        if (st.phase === 'SUCCESS' || st.phase === 'FINISHED' || st.phase === 'FAILED') {
          finishedTaskIds.add(taskId)
        }
        phaseChanged = true
      }
    }
    // Refresh the list only when something finished or a stage changed (one silent refresh, avoiding a burst of requests and a full-screen loading flicker)
    if (hadFinished || phaseChanged) {
      loadProjects(true)
    }
  },
)

// The list status stays authoritative for the end state: poll the list while scanning projects exist (20s safety net),
// so a missing or delayed WS terminal message never leaves a project stuck in "scanning"
const LIST_REFRESH_INTERVAL = 20_000
let listRefreshTimer: ReturnType<typeof setInterval> | null = null

function syncListRefreshTimer() {
  const hasDetecting = projects.value.some(isDetecting)
  if (hasDetecting && !listRefreshTimer) {
    listRefreshTimer = setInterval(loadProjects, LIST_REFRESH_INTERVAL, true)
  } else if (!hasDetecting && listRefreshTimer) {
    clearInterval(listRefreshTimer)
    listRefreshTimer = null
  }
}

watch(projects, syncListRefreshTimer)
onBeforeUnmount(() => {
  if (listRefreshTimer) {
    clearInterval(listRefreshTimer)
    listRefreshTimer = null
  }
})
</script>

<template>
  <div>
    <!-- Top bar -->
    <div
      style="
        display: flex;
        align-items: center;
        justify-content: space-between;
        margin-bottom: 30px;
      "
    >
      <AButton type="primary" @click="openCreateDialog">{{
        t('scan.projects.create-project')
      }}</AButton>
      <div style="display: flex; gap: 12px">
        <AButton @click="openQueueDialog">{{ t('scan.projects.view-queue') }}</AButton>
        <div
          style="
            display: inline-flex;
            overflow: hidden;
            border: 1px solid #d9d9d9;
            border-radius: 6px;
          "
        >
          <AButton
            :type="viewMode === 'card' ? 'primary' : 'text'"
            size="small"
            style="height: 100%; padding: 4px 18px; border: none; border-radius: 0"
            @click="viewMode = 'card'"
          >
            <template #icon>
              <RenderIcon icon="i-ant-design:appstore-outlined" class="size-4" />
            </template>
          </AButton>
          <div style="width: 1px; background: #d9d9d9" />
          <AButton
            :type="viewMode === 'table' ? 'primary' : 'text'"
            size="small"
            style="height: 100%; padding: 4px 18px; border: none; border-radius: 0"
            @click="viewMode = 'table'"
          >
            <template #icon>
              <RenderIcon icon="i-ant-design:unordered-list-outlined" class="size-4" />
            </template>
          </AButton>
        </div>
        <AInput
          v-model:value="keyword"
          :placeholder="t('scan.projects.search-placeholder')"
          allow-clear
          style="width: 300px"
          @press-enter="handleSearch"
        />
        <AButton @click="handleSearch">{{ t('scan.projects.search') }}</AButton>
      </div>
    </div>

    <!-- Loading -->
    <div v-if="loading" class="py-12 text-center">
      <ASpin size="large" />
    </div>

    <!-- Empty -->
    <ACard
      v-else-if="projects.length === 0"
      class="projects-empty-card py-20 text-center border-0 w-full shadow-none"
    >
      <div class="mb-6 flex justify-center">
        <RenderIcon
          v-if="keyword"
          icon="i-ant-design:search-outlined"
          class="projects-empty-icon size-24"
        />
        <RenderIcon v-else icon="i-ant-design:inbox-outlined" class="projects-empty-icon size-24" />
      </div>
      <div
        class="projects-empty-title"
        style="margin-bottom: 12px; font-size: 22px; font-weight: 600"
      >
        {{ keyword ? t('scan.projects.no-search-results') : t('scan.projects.no-projects') }}
      </div>
      <div class="projects-empty-desc" style="font-size: 15px">
        {{
          keyword ? t('scan.projects.no-search-results-hint') : t('scan.projects.no-projects-hint')
        }}
      </div>
    </ACard>

    <!-- Table view -->
    <ATable
      v-else-if="viewMode === 'table'"
      :data-source="projects"
      :columns="tableColumns"
      row-key="id"
      size="middle"
      :pagination="{
        pageSize: 10,
        showSizeChanger: true,
        showTotal: (total: number) => t('scan.projects.total-rows', { total }),
      }"
    >
      <!-- antdv types this slot's `record` as AnyObject no matter the row type,
           so keep it loose here; the handlers below keep their own row signatures -->
      <template #bodyCell="{ column, record }: { column: any; record: any }">
        <template v-if="column.key === 'status'">
          <div style="display: flex; flex-direction: column; gap: 4px">
            <ATag
              :color="
                isQueued(record)
                  ? 'warning'
                  : isFinished(record.status)
                    ? 'success'
                    : record.taskId
                      ? 'processing'
                      : 'default'
              "
              style="width: fit-content; margin: 0"
            >
              {{
                isQueued(record)
                  ? t('scan.projects.queued')
                  : isFinished(record.status)
                    ? t('scan.projects.status-finished')
                    : record.taskId
                      ? t('scan.projects.status-scanning')
                      : t('scan.projects.status-unscanned')
              }}
            </ATag>
            <span
              v-if="isDetecting(record)"
              style="font-size: 12px; color: #94a3b8; white-space: nowrap"
            >
              {{ projectProgressText(record) }}
            </span>
          </div>
        </template>
        <template v-else-if="column.key === 'createTime'">
          {{ record.createTime ? dayjs(record.createTime).format('YYYY-MM-DD HH:mm:ss') : '-' }}
        </template>
        <template v-else-if="column.key === 'updateTime'">
          {{
            isFinished(record.status) && record.updateTime
              ? dayjs(record.updateTime).format('YYYY-MM-DD HH:mm:ss')
              : '-'
          }}
        </template>
        <template v-else-if="column.key === 'action'">
          <ASpace :size="4">
            <AButton
              type="link"
              size="small"
              :disabled="isDetecting(record) || isQueued(record)"
              @click="openReportDialog(record)"
              >{{ isQueued(record) ? t('scan.projects.queued') : t('scan.projects.view') }}</AButton
            >
            <AButton type="link" size="small" danger @click="removeProject(record)">{{
              t('scan.projects.delete')
            }}</AButton>
          </ASpace>
        </template>
      </template>
    </ATable>

    <!-- Card view -->
    <div
      v-else
      class="project-card-grid"
      style="display: grid; grid-template-columns: repeat(auto-fill, minmax(370px, 1fr)); gap: 20px"
    >
      <div
        v-for="project in projects"
        :key="project.id"
        class="project-card"
        style="
          display: flex;
          flex-direction: column;
          justify-content: space-between;
          min-height: 120px;
          padding: 20px;
          background: #f9fbff;
          border-radius: 10px;
          box-shadow: 0px 4px 32px 0px rgba(160, 175, 222, 0.2);
        "
      >
        <div style="display: flex; align-items: flex-start; justify-content: space-between">
          <div
            class="project-card-name"
            style="
              min-width: 0;
              overflow: hidden;
              text-overflow: ellipsis;
              font-family: 'Microsoft YaHei', sans-serif;
              font-size: 24px;
              font-variation-settings: 'opsz' auto;
              font-weight: bold;
              line-height: 24px;
              color: #000000;
              letter-spacing: 0em;
              white-space: nowrap;
              cursor: pointer;
            "
            @click="openEditDialog(project)"
          >
            {{ project.name }}
          </div>
          <AButton
            type="text"
            :style="{
              width: '24px',
              height: '24px',
              borderRadius: '50%',
              background: '#7182a8',
              border: 'none',
              padding: '0',
              display: 'inline-flex',
              alignItems: 'center',
              justifyContent: 'center',
              flexShrink: '0',
              minWidth: 'unset',
            }"
            @click.stop="removeProject(project)"
          >
            <template #icon>
              <RenderIcon icon="i-ant-design:close-outlined" class="size-3" style="color: #fff" />
            </template>
          </AButton>
        </div>

        <!-- Status & file info -->
        <div style="display: flex; flex-direction: column; gap: 8px; margin-top: 16px">
          <div style="display: flex; gap: 8px; align-items: center">
            <ATag
              :color="
                isFinished(project.status) ? 'success' : project.taskId ? 'processing' : 'default'
              "
              style="margin: 0"
            >
              {{
                isFinished(project.status)
                  ? t('scan.projects.status-finished')
                  : project.taskId
                    ? t('scan.projects.status-scanning')
                    : t('scan.projects.status-unscanned')
              }}
            </ATag>
            <span v-if="project.fileName" style="font-size: 13px; color: #94a3b8">
              {{ project.fileName }}
            </span>
          </div>
        </div>

        <!-- Info lines -->
        <div style="display: flex; flex-direction: column; gap: 10px; margin-top: 16px">
          <!-- Created at -->
          <div
            style="
              display: flex;
              gap: 6px;
              align-items: center;
              font-family: 'Helvetica Neue LT Pro', sans-serif;
              font-size: 15px;
              font-weight: normal;
              line-height: 22px;
              color: #7182a8;
              letter-spacing: 0.05em;
            "
          >
            <RenderIcon icon="i-ant-design:calendar-outlined" class="size-4" />
            <span
              >{{ t('scan.projects.create-time-column') }}：{{
                project.createTime ? dayjs(project.createTime).format('YYYY-MM-DD HH:mm:ss') : '-'
              }}</span
            >
          </div>
          <!-- Scanned at -->
          <div
            style="
              display: flex;
              gap: 6px;
              align-items: center;
              font-family: 'Helvetica Neue LT Pro', sans-serif;
              font-size: 15px;
              font-weight: normal;
              line-height: 22px;
              color: #7182a8;
              letter-spacing: 0.05em;
            "
          >
            <RenderIcon icon="i-ant-design:clock-circle-outlined" class="size-4" />
            <span
              >{{ t('scan.projects.detect-time-column') }}：{{
                isFinished(project.status) && project.scanTime
                  ? dayjs(project.scanTime).format('YYYY-MM-DD HH:mm:ss')
                  : '-'
              }}</span
            >
          </div>
        </div>
        <div style="display: flex; justify-content: flex-end; margin-top: 12px">
          <AButton
            :disabled="isDetecting(project) || isQueued(project)"
            :style="{
              display: 'flex',
              flexDirection: 'column',
              gap: '10px',
              alignItems: 'center',
              justifyContent: 'center',
              width: '100%',
              padding: '8px 10px',
              fontFamily: '\'Microsoft YaHei\', sans-serif',
              fontSize: isDetecting(project) || isQueued(project) ? '14px' : '16px',
              fontVariationSettings: '\'opsz\' auto',
              fontWeight: 'normal',
              lineHeight: 'normal',
              color: isDetecting(project) || isQueued(project) ? '#94a3b8' : '#3578ff',
              letterSpacing: '0em',
              background:
                isDetecting(project) || isQueued(project)
                  ? 'rgba(148, 163, 184, 0.15)'
                  : 'rgba(53, 120, 255, 0.1)',
              border: 'none',
              borderRadius: '4px',
              cursor: isDetecting(project) || isQueued(project) ? 'not-allowed' : 'pointer',
            }"
            @click="openReportDialog(project)"
            >{{
              isQueued(project)
                ? t('scan.projects.queued')
                : isDetecting(project)
                  ? projectProgressText(project)
                  : t('scan.projects.view-details')
            }}</AButton
          >
        </div>
      </div>
    </div>
  </div>

  <!-- Create/Edit dialog -->
  <LightDialog
    v-model:open="dialogVisible"
    width="800px"
    height="600px"
    padding="20px 30px 30px 30px"
  >
    <template #title>{{
      editingProjectId
        ? t('scan.projects.edit-dialog-title')
        : t('scan.projects.create-dialog-title')
    }}</template>
    <div class="create-form">
      <LightCard style="flex: 1; padding: 40px 30px; border-radius: 10px">
        <AForm
          ref="formRef"
          :model="projectForm"
          style="max-width: 700px; margin: 0 auto"
          layout="horizontal"
          :label-col="{ style: { width: '100px' } }"
          :wrapper-col="{ style: { flex: 1 } }"
          @finish="submitProject"
        >
          <AFormItem
            :label="t('scan.projects.name-label')"
            name="name"
            required
            style="margin-bottom: 28px"
          >
            <AInput
              v-model:value="projectForm.name"
              :placeholder="t('scan.projects.name-placeholder')"
              size="large"
            />
          </AFormItem>
          <AFormItem
            :label="t('scan.projects.os-label')"
            name="systemName"
            required
            style="margin-bottom: 28px"
          >
            <ASelect
              v-model:value="projectForm.systemName"
              :placeholder="t('scan.projects.os-placeholder')"
              size="large"
              :options="osOptions"
            />
          </AFormItem>
          <AFormItem
            :label="t('scan.projects.os-version-label')"
            name="systemVersion"
            style="margin-bottom: 28px"
          >
            <AInput
              v-model:value="projectForm.systemVersion"
              :placeholder="t('scan.projects.os-version-placeholder')"
              size="large"
            />
          </AFormItem>
          <AFormItem :label="t('scan.projects.description-label')" style="margin-bottom: 28px">
            <ATextarea
              v-model:value="projectForm.description"
              :rows="4"
              :placeholder="t('scan.projects.description-placeholder')"
            />
          </AFormItem>
          <AFormItem :wrapper-col="dialogActionsCol">
            <div style="display: flex; gap: 12px; justify-content: flex-end">
              <AButton size="large" @click="dialogVisible = false">{{
                t('scan.projects.cancel')
              }}</AButton>
              <AButton type="primary" size="large" :loading="submitting" html-type="submit">
                {{ t('scan.projects.save') }}
              </AButton>
            </div>
          </AFormItem>
        </AForm>
      </LightCard>
    </div>
  </LightDialog>

  <!-- Step dialog for unscanned projects -->
  <LightDialog v-model:open="stepDialogOpen" width="1200px" height="630px">
    <template #title
      ><span
        style="
          min-width: 0;
          max-width: 25vw;
          overflow: hidden;
          text-overflow: ellipsis;
          white-space: nowrap;
        "
        >{{ stepProject?.name || '' }}</span
      ><span class="sub-title">{{ t('scan.steps.system-detection') }}</span></template
    >
    <div class="step-cards">
      <LightCard class="card">
        <img class="cover" src="@/assets/svg/survey-form-access-success.svg" alt="" />
        <div class="step">STEP 1</div>
        <div class="title">{{ t('scan.steps.step1-title') }}</div>
        <div class="description">{{ t('scan.steps.step1-desc') }}</div>
        <div class="footer mt-auto">
          {{ t('scan.steps.step1-status') }}
          <AButton class="oper-btn success" @click="handleStep1Rewrite">
            {{ t('scan.steps.rewrite') }}
          </AButton>
        </div>
      </LightCard>
      <LightCard class="card">
        <img
          v-if="stepIndex >= 1"
          class="cover"
          src="@/assets/svg/local-code-upload-success.svg"
          alt=""
        />
        <img v-else class="cover" src="@/assets/svg/local-code-upload.svg" alt="" />
        <div class="step">STEP 2</div>
        <div class="title">{{ t('scan.steps.step2-title') }}</div>
        <div class="description">{{ t('scan.steps.step2-desc') }}</div>
        <div class="scope-hint">
          <ATooltip :title="t('scan.steps.upload-scope-hint')">
            <span class="scope-hint-inner">
              <svg
                class="warn-icon"
                viewBox="0 0 1024 1024"
                width="14"
                height="14"
                fill="none"
                xmlns="http://www.w3.org/2000/svg"
              >
                <circle cx="512" cy="512" r="460" stroke="currentColor" stroke-width="64" />
                <circle cx="512" cy="300" r="42" fill="currentColor" />
                <line
                  x1="512"
                  y1="430"
                  x2="512"
                  y2="700"
                  stroke="currentColor"
                  stroke-width="72"
                  stroke-linecap="round"
                />
              </svg>
              <span>{{ t('scan.steps.upload-scope') }}</span>
            </span>
          </ATooltip>
        </div>
        <input
          ref="fileInputRef"
          type="file"
          accept=".zip"
          style="display: none"
          @change="onFilePicked"
        />
        <div v-if="stepIndex >= 1" class="footer mt-auto">
          <span class="file-name">{{
            stepFile?.name || stepProject?.fileName || t('scan.steps.step2-status')
          }}</span>
          <AButton class="oper-btn success" @click="handleResetStep">
            {{ t('scan.steps.rewrite') }}
          </AButton>
        </div>
        <AButton v-else class="oper-btn mt-auto" type="primary" @click="handlePickFile">{{
          t('scan.steps.upload-code')
        }}</AButton>
      </LightCard>
      <LightCard class="card" :class="{ disabled: stepIndex < 1 }">
        <img class="cover" src="@/assets/svg/one-click-detection.svg" alt="" />
        <div class="step">STEP 3</div>
        <div class="title">{{ t('scan.steps.step3-title') }}</div>
        <div class="description">{{ t('scan.steps.step3-desc') }}</div>
        <div class="eval-buttons mt-auto">
          <AButton
            v-if="startingScan"
            class="oper-btn"
            type="primary"
            loading
            disabled
            style="width: 100%"
            >{{ t('scan.steps.preparing') }}</AButton
          >
          <template v-else>
            <AButton
              class="oper-btn"
              type="primary"
              :disabled="stepIndex < 1"
              @click="handleStartScan('0')"
              >{{ t('scan.steps.quick-detection') }}</AButton
            >
            <AButton
              class="oper-btn"
              type="primary"
              :disabled="stepIndex < 1"
              @click="handleStartScan('1')"
              >{{ t('scan.steps.deep-detection') }}</AButton
            >
          </template>
        </div>
      </LightCard>
    </div>
  </LightDialog>

  <!-- Detection queue dialog -->
  <AModal
    v-model:open="queueDialogVisible"
    :title="t('scan.projects.queue-title')"
    :footer="null"
    width="640px"
    centered
  >
    <ASpin :spinning="queueLoading">
      <div v-if="queueList.length === 0" class="text-slate-400 py-10 text-center">
        {{ t('scan.projects.queue-empty') }}
      </div>
      <div v-else class="flex flex-col gap-2">
        <div
          v-for="(item, index) in queueList"
          :key="item.id"
          class="px-3 py-2.5 flex gap-3 items-center justify-between border rounded-lg"
          style="border-color: #e8edf5"
        >
          <div class="flex gap-3 min-w-0 items-center">
            <span
              class="text-xs font-semibold rounded-full flex shrink-0 h-6 w-6 items-center justify-center"
              :style="{
                background: index === 0 ? 'rgba(53, 120, 255, 0.12)' : 'rgba(148, 163, 184, 0.15)',
                color: index === 0 ? '#3578ff' : '#94a3b8',
              }"
              >{{ index + 1 }}</span
            >
            <span class="truncate text-sm">{{ item.name }}</span>
          </div>
          <span
            class="text-xs whitespace-nowrap"
            :style="{ color: isDetecting(item) ? '#3578ff' : '#94a3b8' }"
          >
            {{ queueItemProgress(item) }}
          </span>
        </div>
      </div>
    </ASpin>
  </AModal>

  <!-- Detection report dialog (reusing the LightDialog report demo styling) -->
  <LightDialog
    ref="reportDialogRef"
    v-model:open="reportDialogVisible"
    width="1840px"
    height="auto"
    padding="30px"
  >
    <template #title>
      <span
        style="
          min-width: 0;
          max-width: 25vw;
          overflow: hidden;
          text-overflow: ellipsis;
          white-space: nowrap;
        "
        >{{ reportProjectName }}</span
      >
      <span class="sub-title">{{ t('scan.report.title') }}</span>
    </template>
    <template #extra>
      <div class="btns" style="display: flex; align-items: center">
        <LightLink
          class="btn"
          style="
            display: inline-flex;
            gap: 8px;
            align-items: center;
            margin-right: 20px;
            color: #333;
            text-decoration: none;
            cursor: pointer;
          "
          @click="handleReportReScan"
        >
          <img
            class="icon"
            src="@/assets/svg/re-scan.svg"
            alt=""
            style="width: 20px; height: 20px"
          />
          <span class="text" style="font-size: 16px">{{ t('scan.report.re-detect') }}</span>
        </LightLink>
        <LightLink
          class="btn"
          style="
            display: inline-flex;
            gap: 8px;
            align-items: center;
            margin-right: 20px;
            color: #333;
            text-decoration: none;
            cursor: pointer;
          "
          :style="exportingReport ? { pointerEvents: 'none', opacity: 0.6 } : {}"
          @click="handleExportReport"
        >
          <img
            class="icon"
            src="@/assets/svg/report-export.svg"
            alt=""
            style="width: 20px; height: 20px"
          />
          <span class="text" style="font-size: 16px">
            {{ exportingReport ? t('scan.report.exporting') : t('scan.report.export-report') }}
          </span>
        </LightLink>

        <LightButton
          class="btn"
          style="display: inline-flex; gap: 8px; align-items: center"
          @click="reportDialogRef?.close()"
        >
          <img class="icon" src="@/assets/svg/close.svg" alt="" style="width: 20px; height: 20px" />
          <span class="text" style="font-size: 16px">{{ t('common.close') }}</span>
        </LightButton>
      </div>
    </template>
    <div class="detection-report">
      <ScanReportDialog
        ref="scanReportRef"
        :task-id="reportTaskId"
        :project-id="reportProjectForLight?.id || ''"
        :project-name="reportProjectName"
        :project-description="reportProjectForLight?.description || ''"
        :file-info="reportFileInfo"
        :file-tree="reportProjectForLight?.fileTree || null"
        :show-adapt="false"
      />
    </div>
  </LightDialog>

  <!-- Detection progress dialog -->
  <ScanProgressDialog
    v-model:open="progressDialogOpen"
    :project-name="progressProjectName"
    :task-id="stepProject?.taskId || ''"
    :total="scanTotal"
    :completed="scanCompleted"
    :current-file="scanCurrentFile"
    :latest-done-file="scanLatestDoneFile"
    :scan-status="scanStatus"
    :files="scanFiles"
    :review-files="scanReviewFiles"
    :verify-completed="scanVerifyCompleted"
    :verify-total="scanVerifyTotal"
    :scan-phase="scanPhase"
    :scan-type="currentScanType"
    :unzip-percent="scanUnzipPercent"
    :issue-files="scanIssueFiles"
    :total-issues="scanTotalIssues"
    @stop="handleStopScan"
  />
</template>

<style lang="scss" scoped>
.sub-title {
  margin-left: 20px;
  font-family:
    Microsoft YaHei,
    sans-serif;
  font-size: 20px;
  font-weight: normal;
  line-height: 37px;
  color: #7182a8;
  letter-spacing: 0.05em;
}

.step-cards {
  box-sizing: border-box;
  display: flex;
  align-items: center;
  justify-content: center;
  height: 100%;
  padding-top: 10px;

  .card {
    box-sizing: border-box;
    display: flex;
    flex-direction: column;
    align-items: center;
    width: 300px;
    height: 400px;
    padding: 30px 25px;
    border-radius: 12px;

    &:not(:last-child) {
      margin-right: 40px;
    }

    &.disabled > * {
      opacity: 0.3;
    }

    .cover {
      width: 110px;
      height: 110px;
      margin-bottom: 20px;
      border-radius: 60px;
    }

    .step {
      font-family: 'Helvetica Neue LT Pro', sans-serif;
      font-size: 32px;
      font-weight: bold;
      color: #c6cdde;
      text-align: center;
      opacity: 0.3;
    }

    .scope-hint {
      display: flex;
      align-items: center;
      justify-content: center;
      margin-top: 8px;

      .scope-hint-inner {
        display: flex;
        gap: 4px;
        align-items: center;
        cursor: help;

        > span {
          font-size: 13px;
          color: #7182a8;
        }
      }

      .warn-icon {
        color: #3578ff;
      }
    }

    .title {
      font-family:
        Microsoft YaHei,
        sans-serif;
      font-size: 24px;
      font-weight: bold;
      color: #2e2e2e;
      text-align: center;
      letter-spacing: 0.05em;
    }

    .description {
      margin-top: 10px;
      font-family:
        Microsoft YaHei,
        sans-serif;
      font-size: 16px;
      font-weight: normal;
      line-height: 24px;
      color: #7182a8;
      text-align: center;
    }

    .footer {
      display: flex;
      flex-direction: row;
      gap: 4px;
      align-items: center;
      justify-content: center;
      width: 100%;
      padding: 0px 0px 0px 12px;
      font-family:
        Microsoft YaHei,
        sans-serif;
      font-size: 16px;
      font-weight: normal;
      line-height: 16px;
      color: #4ad48d;
      background: #e7f6f3;
      border-radius: 6px;

      // Truncate a long source package name
      .file-name {
        flex: 1;
        min-width: 0;
        overflow: hidden;
        text-overflow: ellipsis;
        white-space: nowrap;
      }

      .oper-btn {
        height: 38px;
        padding: 0 15px;
        margin-left: auto;
        color: #fff;
        background: #4ad48d;
        border: none;
        border-radius: 6px;

        &:hover {
          background: #3ec07a !important;
          border-color: transparent !important;
        }
      }
    }

    .eval-buttons {
      display: flex;
      flex-direction: row;
      gap: 12px;
      align-self: stretch;
    }

    .oper-btn {
      display: flex;
      flex-direction: row;
      gap: 4px;
      align-items: center;
      align-self: stretch;
      justify-content: center;
      height: 38px;
      padding: 0 24px;
      font-size: 16px;
      border-radius: 6px;

      &:not(.success) {
        color: #fff;
        background: #3578ff;
        border-color: #3578ff;

        &:hover {
          background: #4d8aff !important;
          border-color: #4d8aff !important;
        }
      }
    }
  }
}

.mt-auto {
  margin-top: auto;
}

.create-form {
  display: flex;
  flex: 1;
  flex-direction: column;
}

.projects-empty-card {
  /* ACard handles its own bg in dark via antdv-next theme */
}

.projects-empty-icon {
  color: #e2e8f0;
}

.projects-empty-title {
  color: #94a3b8;
}

.projects-empty-desc {
  color: #94a3b8;
}

.detection-report {
  display: flex;
  flex: 1;
  flex-direction: column;
  height: 100%;
}

.btns {
  display: flex;
  align-items: center;
}
</style>

<style lang="scss">
html[lang='en-US'] .step-cards .card .title {
  font-size: 20px;
}

html[lang='en-US'] .step-cards .card .description {
  font-size: 14px;
}

.dark {
  // --- Top bar ---
  .project-card-grid + div[style*='border'] {
    /* toggle group border, handled below */
  }

  // --- Project card ---
  .project-card {
    background: #1a1f2e !important;
    box-shadow: 0px 4px 32px 0px rgba(0, 0, 0, 0.3) !important;

    .project-card-name {
      color: #e8edf5 !important;
    }

    // Delete close button
    .ant-btn[style*='#7182a8'] {
      background: #3a4360 !important;
    }

    // Info lines (created at / scanned at)
    [style*='color: #7182a8'] {
      color: #a0adde !important;
    }

    // File name
    [style*='color: #94a3b8'] {
      color: #6b7a9e !important;
    }

    // View details button
    [style*='color: #3578ff'] {
      color: #60a5fa !important;
      background: rgba(53, 120, 255, 0.15) !important;
    }
  }

  // --- Top bar toggle group ---
  [style*='border: 1px solid #d9d9d9'],
  [style*='border:1px solid #d9d9d9'] {
    border-color: #2a3050 !important;
  }

  [style*='background: #d9d9d9'] {
    background: #2a3050 !important;
  }

  // --- Empty state ---
  .projects-empty-icon {
    color: #334155;
  }

  .projects-empty-title {
    color: #475569;
  }

  .projects-empty-desc {
    color: #475569;
  }

  .text-slate-400 {
    color: #a0adde !important;
  }

  // --- Sub-title ---
  .sub-title {
    color: #a0adde !important;
  }

  // --- Step cards ---
  .step-cards {
    .card {
      .step {
        color: #6b7a9e !important;
      }

      .title {
        color: #e8edf5 !important;
      }

      .description {
        color: #a0adde !important;
      }

      .footer {
        color: #4ad48d !important;
        background: rgba(74, 212, 141, 0.1) !important;
      }
    }
  }

  // --- Report dialog toolbar buttons ---
  .btns .light-link {
    color: #e8edf5 !important;
  }
}
</style>
