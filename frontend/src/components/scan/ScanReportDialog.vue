<script setup lang="ts">
import type { EChartsOption } from 'echarts'
import type { AdaptSchemeResult, BTQuestionInfo, DirectoryNodeDTO } from '@/types/llm'
import type {
  AsmFileInfo,
  FileInfoCategory,
  FrameworkPortraitData,
  LibListEntry,
  QuestionInfo,
  ReportInfo,
} from '@/types/scan'
import MarkdownIt from 'markdown-it'
import { computed, nextTick, onMounted, onUnmounted, ref, watch } from 'vue'
import { adaptFile, getFileAdaptResult, getProjectTree } from '@/api/llm'
import {
  getAsmList,
  getFileQuestions,
  getFileText,
  getFrameworkPortrait,
  getLibList,
  getMakeFileInfo,
  getQuestionList,
  getReportInfo,
} from '@/api/scan'
import { useAtlasData } from '@/composables/useAtlasData'
import { useTypewriter } from '@/composables/useTypewriter'
import AtlasPanel from './AtlasPanel.vue'

const props = withDefaults(
  defineProps<{
    taskId?: string
    /** Project id (used by the backend getReportInfo endpoint; when present the online report renders backend data) */
    projectId?: string
    projectName?: string
    projectDescription?: string
    fileInfo?: Record<string, FileInfoCategory> | null
    fileTree?: string | null
    showOverview?: boolean
    showAdapt?: boolean
  }>(),
  {
    taskId: '',
    projectId: '',
    projectName: '',
    projectDescription: '',
    fileInfo: null,
    fileTree: null,
    showOverview: true,
    showAdapt: true,
  },
)

const { t } = useI18n()

const tabs = computed(() => {
  return props.showOverview !== false
    ? [
        { label: t('scan.report.tabs-overview'), name: 'overview' },
        { label: t('scan.report.tabs-online-report'), name: 'report' },
        { label: t('scan.report.tabs-file-content'), name: 'fileContent' },
      ]
    : [
        { label: t('scan.report.tabs-online-report'), name: 'report' },
        { label: t('scan.report.tabs-file-content'), name: 'fileContent' },
      ]
})

// --- Dialog state ---
const questionLoading = ref(false)
/** Backend report data (getReportInfo), used to render the online report tab */
const reportInfo = ref<ReportInfo | null>(null)
const reportLoading = ref(false)
/** OS text shown in the report basics (the backend already merges OS name + version, e.g. "Ubuntu 20.04") */
const reportOsText = computed(() => reportInfo.value?.systemName || t('scan.report.none'))
/** Report request sequence, used to drop stale responses so switching projects never lets old data overwrite new */
let reportRequestSeq = 0
const questionList = ref<QuestionInfo[]>([])
const activeQuestionTab = ref(props.showOverview !== false ? 'overview' : 'report')

// --- Framework portrait data ---
const frameworkPortrait = ref<FrameworkPortraitData | null>(null)
const libList = ref<LibListEntry[]>([])
const asmList = ref<AsmFileInfo | null>(null)
const makeFileList = ref<BTQuestionInfo[]>([])

const {
  atlasSourceLanguageRows,
  atlasSourceTotalLineText,
  atlasSourceTotalLineStyle,
  atlasSourceTotalFileCount,
  atlasSourceLanguageText,
  atlasSourceBuildSystemsText,
  atlasAssemblyTotalCount,
  atlasAssemblyFileCount,
  atlasInlineAsmCount,
  atlasAssemblyNotice,
  atlasAssemblyArchitectureRows,
  atlasMainboardStats,
  atlasMainboardPieRows,
  atlasDepTotalCount,
  atlasDepSupportedCount,
  atlasDepUnsupportedCount,
  atlasDependencyNotice,
  atlasBuildAnalyzedFiles,
  atlasBuildPatternCount,
  atlasBuildArchCount,
  atlasBuildArchRows,
  atlasBuildNotice,
  depLibGroups,
} = useAtlasData({
  fileInfo: () => props.fileInfo,
  frameworkPortrait,
  libList,
  asmList,
  makeFileList,
})

const sourceLanguageSummary = computed(() => {
  const fileInfo = props.fileInfo
  if (!fileInfo) return []

  const categoryMeta: Record<string, string> = {
    C_SOURCE: 'C',
    C_HEADER: 'C',
    CPP_SOURCE: 'C++',
    CPP_HEADER: 'C++',
    ASSEMBLY: 'assembly',
    SCRIPT: 'script',
    DOCUMENT: 'document',
    CONFIG: 'config',
    BUILD: 'build',
    OTHER: 'other',
  }

  const merged = new Map<string, { fileCount: number; lineCount: number }>()
  for (const [key, cat] of Object.entries(fileInfo)) {
    const c = cat as FileInfoCategory
    const label = categoryMeta[key] ?? key
    const existing = merged.get(label)
    if (existing) {
      existing.fileCount += c.fileCount ?? 0
      existing.lineCount += c.lineCount ?? 0
    } else {
      merged.set(label, {
        fileCount: c.fileCount ?? 0,
        lineCount: c.lineCount ?? 0,
      })
    }
  }

  const categoryLabels: Record<string, string> = {
    c: t('scan.atlas.category-c'),
    cpp: t('scan.atlas.category-cpp'),
    assembly: t('scan.atlas.category-assembly'),
    script: t('scan.atlas.category-script'),
    document: t('scan.atlas.category-document'),
    config: t('scan.atlas.category-config'),
    build: t('scan.atlas.category-build'),
    other: t('scan.atlas.category-other'),
  }

  return Array.from(merged.entries(), ([label, data]) => ({
    label: categoryLabels[label] || label,
    key: label,
    fileCount: data.fileCount,
    lineCount: data.lineCount,
  }))
})

const atlasMainLanguageLabel = computed(() => {
  const summary = sourceLanguageSummary.value.filter(
    (item) => !['other', 'document', 'config', 'build'].includes(item.key),
  )
  const c = summary.find((item) => item.key === 'c')
  const cpp = summary.find((item) => item.key === 'cpp')
  const hasC = c && (c.fileCount > 0 || c.lineCount > 0)
  const hasCpp = cpp && (cpp.fileCount > 0 || cpp.lineCount > 0)
  if (hasC && hasCpp) return 'C/C++'
  if (hasC) return 'C'
  if (hasCpp) return 'C++'
  if (summary.length === 0) return '-'
  const main = summary.reduce((a, b) => (b.lineCount > a.lineCount ? b : a))
  return main.label
})

const atlasMainboardPieOption = computed<EChartsOption>(() => {
  const rows = atlasMainboardPieRows.value
  const hasData = rows.length > 0

  return {
    animation: false,
    tooltip: {
      trigger: 'item',
      formatter(params) {
        const item = Array.isArray(params) ? params[0] : params
        const rawValue = item?.value
        const rawPercent = item?.percent
        const value = typeof rawValue === 'number' ? rawValue : Number(rawValue ?? 0) || 0
        const percent =
          typeof rawPercent === 'number' ? Math.round(rawPercent) : Number(rawPercent ?? 0) || 0
        return t('scan.atlas.count-format', { value, percent })
      },
      backgroundColor: 'rgba(15, 23, 42, 0.92)',
      borderWidth: 0,
      textStyle: {
        color: '#f8fafc',
        fontSize: 12,
      },
    },
    series: [
      {
        type: 'pie',
        radius: ['58%', '82%'],
        center: ['50%', '50%'],
        startAngle: 90,
        clockwise: true,
        avoidLabelOverlap: true,
        itemStyle: {
          borderColor: 'rgba(255,255,255,0.96)',
          borderWidth: 2,
        },
        label: {
          show: false,
        },
        labelLine: {
          show: false,
        },
        emphasis: {
          scale: false,
        },
        data: hasData
          ? rows.map((item) => ({
              value: item.count,
              name: item.label,
              itemStyle: {
                color: item.color,
              },
            }))
          : [
              {
                value: 1,
                name: t('scan.atlas.no-data'),
                itemStyle: {
                  color: '#e2e8f0',
                },
              },
            ],
      },
    ],
  }
})

interface RuleInfo {
  libName?: string
  libCategory?: string
  supportRiscv?: string
  headerName?: string
  macroName?: string
  instructionName?: string
  keyword?: string
}

const TYPE_META: Record<string, { label: string; color: string }> = {
  INCLUDE: { label: t('scan.atlas.type-header'), color: '#ef4444' },
  MACRO: { label: t('scan.atlas.type-macro'), color: '#ef4444' },
  ASM: { label: t('scan.atlas.type-inline-asm'), color: '#ef4444' },
}

// --- File Tree ---
interface FileTreeNode {
  id: string
  label: string
  path: string
  isDir: boolean
  isLeaf: boolean
  children?: FileTreeNode[]
  errCount?: number
}

const fileTreeFilter = ref('')

const fileTreeLoading = ref(false)
const fileTreeData = ref<FileTreeNode[]>([])

function dtoToTreeNode(dto: DirectoryNodeDTO): FileTreeNode {
  const isDir = dto.type === 'DIR'
  return {
    id: dto.path,
    label: dto.name,
    path: dto.path,
    isDir,
    isLeaf: !isDir,
    // No children on directory nodes, which makes loadData lazy-load them
    ...(isDir ? {} : { children: [] }),
    errCount: dto.errCount,
  }
}

function sortNodes(nodes: FileTreeNode[]): FileTreeNode[] {
  return [...nodes].sort((a, b) => {
    if (a.isDir !== b.isDir) return a.isDir ? -1 : 1
    return a.label.localeCompare(b.label)
  })
}

async function loadFileTree() {
  if (!props.taskId) {
    fileTreeData.value = []
    return
  }
  fileTreeLoading.value = true
  try {
    const nodes = await getProjectTree(props.taskId, undefined, props.showAdapt ? '1' : '0')
    fileTreeData.value = sortNodes(nodes.map(dtoToTreeNode))
  } catch {
    window.$message.error(t('common.request.load-file-tree-failed'))
    fileTreeData.value = []
  } finally {
    fileTreeLoading.value = false
  }
}

function findNodeByPath(nodes: FileTreeNode[], targetPath: string): FileTreeNode | null {
  for (const node of nodes) {
    if (node.path === targetPath) return node
    if (node.children) {
      const found = findNodeByPath(node.children, targetPath)
      if (found) return found
    }
  }
  return null
}

async function onLoadData(treeNode: any) {
  // In antdv-next's @v-c/tree, loadData receives an EventDataNode (the original data spread directly); there is no dataRef
  const path: string | undefined = treeNode.path
  if (!path || !props.taskId) return
  try {
    const nodes = await getProjectTree(props.taskId, path, props.showAdapt ? '1' : '0')
    const target = findNodeByPath(fileTreeData.value, path)
    if (target) {
      target.children = sortNodes(nodes.map(dtoToTreeNode))
    }
    // Trigger a reactive update
    fileTreeData.value = [...fileTreeData.value]
  } catch {
    // Fail silently when expanding
  }
}

function filterFileTree(nodes: FileTreeNode[], keyword: string): FileTreeNode[] {
  if (!keyword) return nodes
  const lower = keyword.toLowerCase()
  return nodes.reduce<FileTreeNode[]>((acc, node) => {
    const labelMatch = node.label.toLowerCase().includes(lower)
    const filteredChildren = node.children ? filterFileTree(node.children, lower) : []
    if (labelMatch || filteredChildren.length > 0) {
      acc.push({
        ...node,
        children: filteredChildren.length > 0 ? filteredChildren : node.children,
      })
    }
    return acc
  }, [])
}

const fileTreeFiltered = computed(() => filterFileTree(fileTreeData.value, fileTreeFilter.value))

const fileTreeStats = computed(() => {
  let totalQuestions = 0
  function walk(nodes: FileTreeNode[]) {
    for (const node of nodes) {
      if (node.isDir) {
        if (node.children && node.children.length > 0) {
          // Children were loaded, so recurse into them (this avoids counting the directory's own errCount twice)
          walk(node.children)
        } else {
          // Not expanded yet, so use the directory's own errCount as the subtree total
          totalQuestions += node.errCount ?? 0
        }
      } else {
        totalQuestions += node.errCount ?? 0
      }
    }
  }
  walk(fileTreeData.value)
  return { totalQuestions }
})

const fileTreeExpandedKeys = computed(() => {
  const keys: string[] = []
  if (fileTreeFilter.value) {
    function walkAll(nodes: FileTreeNode[]) {
      for (const node of nodes) {
        if (node.children && node.children.length > 0) {
          keys.push(node.id)
          walkAll(node.children)
        }
      }
    }
    walkAll(fileTreeFiltered.value)
  } else {
    // Auto-expand when the first level holds a single folder
    if (fileTreeData.value.length === 1 && fileTreeData.value[0].isDir) {
      keys.push(fileTreeData.value[0].id)
    }
    // Expand down to the first level that contains files by default
    function walkToFirstFile(nodes: FileTreeNode[], found: boolean[]): boolean {
      for (const node of nodes) {
        if (found[0]) return true
        if (!node.isDir) {
          found[0] = true
          return true
        }
        if (node.children && node.children.some((c) => !c.isDir)) {
          keys.push(node.id)
          found[0] = true
          return true
        }
        if (node.children && walkToFirstFile(node.children, found)) {
          keys.push(node.id)
          return true
        }
      }
      return false
    }
    walkToFirstFile(fileTreeData.value, [false])
  }
  return keys.length > 0 ? keys : undefined
})

// --- File Content ---
const selectedFilePath = ref('')
const selectedFileId = ref('')

// --- Report scroll refs ---
const reportContentRef = ref<HTMLElement | null>(null)
const reportNavRef = ref<HTMLElement | null>(null)

// --- Report nav scroll-spy ---
const REPORT_NAV_ITEMS = computed(
  () =>
    [
      { id: 'report-intro', label: t('scan.report.report-intro') },
      { id: 'report-object', label: t('scan.report.report-object') },
      { id: 'report-situation', label: t('scan.report.report-situation') },
      { id: 'report-conclusion', label: t('scan.report.report-conclusion') },
      { id: 'report-declaration', label: t('scan.report.declaration-title') },
      { id: 'report-issues', label: t('scan.report.issue-detail') },
    ] as const,
)
type ReportSectionId =
  | 'report-intro'
  | 'report-object'
  | 'report-situation'
  | 'report-conclusion'
  | 'report-declaration'
  | 'report-issues'

const activeReportSection = ref<ReportSectionId>('report-intro')

function onReportScroll() {
  const container = reportContentRef.value
  if (!container) return
  const containerTop = container.getBoundingClientRect().top + 16 // Offset compensation
  let currentId: ReportSectionId = REPORT_NAV_ITEMS.value[0].id
  for (const item of REPORT_NAV_ITEMS.value) {
    const el = document.getElementById(item.id)
    if (el) {
      const top = el.getBoundingClientRect().top
      if (top <= containerTop) {
        currentId = item.id as ReportSectionId
      }
    }
  }
  activeReportSection.value = currentId
}
const fileContent = ref('')
const adaptContent = ref('')
const hasAdaptResult = computed(() => adaptContent.value !== '')
const fileQuestions = ref<QuestionInfo[]>([])
const hasFileIssues = computed(() => fileQuestions.value.length > 0)
const adaptSchemeResult = ref<AdaptSchemeResult | null>(null)
const hasAdaptScheme = computed(() => adaptSchemeResult.value !== null)
const rightTab = ref<'scheme' | 'code'>('scheme')
/** Scheme C + code tab → full-width diff mode */
const isDiffMode = computed(
  () =>
    props.showAdapt &&
    hasAdaptScheme.value &&
    rightTab.value === 'code' &&
    adaptSchemeResult.value?.selectedScheme === 'C',
)

// --- Typewriter streaming output ---
const reasoningTw = useTypewriter({ speed: 3, chunkSize: [1, 4] })
const summaryTw = useTypewriter({ speed: 3, chunkSize: [1, 4] })
const codeTw = useTypewriter({ speed: 1, chunkSize: [4, 10] })
/** Skip the typing animation when restoring an existing adaptation result */
const isRestoring = ref(false)

/** Stay out of diff mode while typing, so incomplete text does not trigger constant diffing */
const effectiveIsDiffMode = computed(() => isDiffMode.value && !codeTw.isTyping.value)
/** Scheme B + code tab → Markdown modification steps */
const isMarkdownMode = computed(
  () =>
    props.showAdapt &&
    hasAdaptScheme.value &&
    rightTab.value === 'code' &&
    adaptSchemeResult.value?.selectedScheme === 'B',
)
/** Markdown renderer */
const md = new MarkdownIt({ html: false, linkify: true, breaks: true })
const modStepsHtml = computed(() => {
  if (!adaptContent.value) return ''
  return md.render(adaptContent.value)
})
/** Loading text of the code tab, chosen by scheme type */
const codeTabLoadingText = computed(() => {
  const scheme = adaptSchemeResult.value?.selectedScheme
  if (scheme === 'A') return t('scan.report.adapting-code-new-file')
  if (scheme === 'B') return t('scan.report.adapting-code-steps')
  return t('scan.report.adapting-code-modify')
})
/** Whether the right panel has anything visible */
const showRightPanel = computed(
  () =>
    props.showAdapt &&
    (hasAdaptScheme.value ||
      hasAdaptResult.value ||
      (selectedFilePath.value && hasFileIssues.value)),
)
/** Whether the right adaptation editor is visible (needs showRightPanel plus the code tab or a legacy adaptation result) */
const showAdaptedEditor = computed(
  () =>
    showRightPanel.value &&
    ((hasAdaptScheme.value && rightTab.value === 'code') ||
      (!hasAdaptScheme.value && hasAdaptResult.value)),
)
const adaptingFile = ref(false)
const adaptProgress = reactive({ done: 0, total: 0 })
const adaptFileStatus = ref('')
const adaptPhase = computed(() => {
  if (adaptProgress.total === 0) return 1
  // Move to phase 2 once the finished issue count reaches the total
  if (adaptProgress.done === adaptProgress.total) return 2
  return 1
})
const adaptIsQueued = computed(() => adaptFileStatus.value === 'READY')
const adaptIsRunning = computed(
  () =>
    adaptFileStatus.value === 'DOING_QUESTION' ||
    adaptFileStatus.value === 'DOING_SUGGEST' ||
    adaptFileStatus.value === 'DOING_FILE',
)
const batchAdaptProgress = computed(() => {
  const questions = questionList.value
  const total = questions.length
  const done = questions.filter((q) => q.status === 'FINISH').length
  return { done, total }
})
const fileLoading = ref(false)

// --- Monaco Editors ---
const editorContainer = ref<HTMLDivElement | null>(null)
const adaptedEditorContainer = ref<HTMLDivElement | null>(null)
const diffEditorContainer = ref<HTMLDivElement | null>(null)
let editor: any = null
let adaptedEditor: any = null

// --- Adaptation scheme typewriter watcher ---
watch(
  () => adaptSchemeResult.value,
  async (scheme, prev) => {
    if (scheme) {
      if (isRestoring.value) {
        reasoningTw.complete(scheme.reasoning || '')
        summaryTw.complete(scheme.modificationSummary || '')
        isRestoring.value = false
      } else if (
        !prev ||
        scheme.reasoning !== prev.reasoning ||
        scheme.modificationSummary !== prev.modificationSummary
      ) {
        // New scheme content: stream it from the beginning
        await reasoningTw.start(scheme.reasoning || '')
        await summaryTw.start(scheme.modificationSummary || '')
      } else {
        // The same scheme arrived again (e.g. polling's "show early" and the FINISH confirmation return the same result):
        // do not type it twice, keep showing the full text
        reasoningTw.complete(scheme.reasoning || '')
        summaryTw.complete(scheme.modificationSummary || '')
      }
    } else {
      reasoningTw.reset()
      summaryTw.reset()
    }
  },
)

// Sync the code typewriter output into the Monaco editor
watch(
  () => codeTw.displayed.value,
  (text) => {
    adaptContent.value = text
    // Sync into the right editor. Adapted code is shown at once (not streamed), so it no longer scrolls line by line
    updateAdaptedEditorContent(text)
  },
)
let diffEditor: any = null

// Enter diff mode and refresh once typing finishes
watch(
  () => codeTw.isTyping.value,
  (typing) => {
    if (!typing && isDiffMode.value && diffEditor && adaptContent.value) {
      updateDiffModels(fileContent.value || '', adaptContent.value)
      nextTick(() => {
        diffEditor?.getOriginalEditor()?.render()
        diffEditor?.layout()
      })
    }
  },
)
let monaco: any = null
let lensDisposable: any = null
let cmdDisposable: any = null
let themeWatcher: ReturnType<typeof watch> | null = null
let resizeObserver: ResizeObserver | null = null
let resizeTimer: ReturnType<typeof setTimeout> | null = null
const appStore = useAppStore()

// --- File Question Annotations ---
let annotationDecorations: string[] = []
let annotationZoneIds: string[] = []

// Monaco needs a relayout when v-show toggles
watch(hasFileIssues, () => {
  nextTick(() => {
    editor?.layout()
    adaptedEditor?.layout()
    editor?.layout()
  })
})

async function onFileTreeSelect(_keys: any[], info: any) {
  const node = info.node as FileTreeNode
  if (node.isDir) return
  selectedFilePath.value = node.path
  selectedFileId.value = ''
  adaptProgress.done = 0
  adaptProgress.total = 0
  adaptFileStatus.value = ''
  adaptSchemeResult.value = null
  rightTab.value = 'scheme'
  adaptingFile.value = false
  fileLoading.value = true
  clearAnnotations()
  fileQuestions.value = []
  adaptContent.value = ''
  codeTw.reset()
  try {
    const [content, rawQuestions] = await Promise.all([
      getFileText(node.path, props.taskId),
      getFileQuestions(node.path, props.taskId, props.showAdapt ? '1' : '0').catch(() => null),
    ])
    const questions = rawQuestions ?? questionList.value.filter((q) => q.filePath === node.path)
    fileContent.value = content

    // Fetch the file-level adaptation result (v3: by fileId)
    let adapted: string | null | undefined = null
    if (props.showAdapt) {
      // Resolve fileId: the current request result first, falling back to the loaded questionList
      const fileId =
        (questions.length > 0 && questions[0].fileId) ||
        questionList.value.find((q) => q.filePath === node.path)?.fileId ||
        ''
      selectedFileId.value = fileId
      if (fileId) {
        try {
          const adaptResult = await getFileAdaptResult(fileId)
          adapted = adaptResult?.adaptText
          // Runtime type guard: make sure adaptResult is a structured object
          const rawScheme = adaptResult?.adaptResult
          if (rawScheme && typeof rawScheme === 'object' && 'selectedScheme' in rawScheme) {
            isRestoring.value = true
            adaptSchemeResult.value = rawScheme as AdaptSchemeResult
          } else if (typeof rawScheme === 'string') {
            try {
              const parsed = JSON.parse(rawScheme)
              if (parsed && typeof parsed === 'object' && 'selectedScheme' in parsed) {
                isRestoring.value = true
                adaptSchemeResult.value = parsed as AdaptSchemeResult
              }
            } catch {
              /* not JSON, old format string */
            }
          }
          adaptProgress.done = adaptResult?.adaptQuestionCount ?? 0
          adaptProgress.total = adaptResult?.totalQuestionCount ?? 0
          adaptFileStatus.value = adaptResult?.status ?? ''
          // Merge the issue-level adaptation state into fileQuestions
          if (adaptResult?.btQuestionInfos) {
            for (const qi of questions) {
              const match = adaptResult.btQuestionInfos.find((a) => a.id === qi.id)
              if (match) {
                qi.status = match.status
                qi.adaptResult = match.adaptResult
              }
            }
          }
        } catch {
          // No adaptation result yet, ignore
        }
        // Start polling automatically when the file is queued or being adapted
        if (adaptFileStatus.value === 'READY') {
          // Queued: poll in the background without showing a progress ring
          pollAdaptResult()
        } else if (
          adaptFileStatus.value === 'DOING_QUESTION' ||
          adaptFileStatus.value === 'DOING_SUGGEST' ||
          adaptFileStatus.value === 'DOING_FILE'
        ) {
          adaptingFile.value = true
          pollAdaptResult().finally(() => {
            adaptingFile.value = false
          })
        }
      }
    }

    if (adapted != null && adapted !== '') {
      codeTw.complete(adapted)
    } else {
      adaptContent.value = ''
      codeTw.reset()
    }
    // Backend line numbers are 0-based → Monaco is 1-based
    const normalized = questions.map((q) => ({
      ...q,
      startLine: q.startLine === q.endLine ? q.startLine : q.startLine + 1,
      endLine: q.startLine === q.endLine ? q.endLine : q.endLine + 1,
    }))
    fileQuestions.value = normalized
    if (!monaco) return
    // Update the left editor with the original file content
    const language = detectLanguage(node.path)
    if (editor) {
      const leftModel = editor.getModel()
      if (leftModel) {
        leftModel.setValue(content)
        monaco.editor.setModelLanguage(leftModel, language)
      }
    }
    if (props.showAdapt) {
      // Adaptation mode: left shows the original file + annotations, right shows the adapted code
      if (normalized.length > 0) {
        clearAnnotations()
        applyFileAnnotations(normalized)
        setTimeout(() => editor?.revealLineNearTop(normalized[0].startLine), 80)
      }
      // Right side: update the adapted code (lazily create the editor when missing)
      updateAdaptedEditorContent(adapted)
      if (isDiffMode.value && diffEditor) {
        updateDiffModels(content, adapted)
        nextTick(() => {
          diffEditor?.getOriginalEditor()?.render()
          diffEditor?.layout()
        })
      }
    } else if (normalized.length > 0) {
      // Files with issues but no adaptation → plain editor + annotations
      clearAnnotations()
      if (editor) {
        applyFileAnnotations(normalized)
        setTimeout(() => editor?.revealLineNearTop(normalized[0].startLine), 80)
      }
    } else {
      clearAnnotations()
    }
  } catch (error) {
    fileContent.value = `// ${t('scan.report.file-load-failed')}: ${error instanceof Error ? error.message : t('scan.report.unknown-error')}`
  } finally {
    fileLoading.value = false
  }
}

async function handleAdaptFile() {
  if (!selectedFileId.value) return
  const fileId = selectedFileId.value
  adaptingFile.value = true
  try {
    // v3: the backend picks the LLM config, the frontend only sends fileId
    await adaptFile(fileId)
    // The user may have switched files while the request was in flight
    if (selectedFileId.value !== fileId) return
    // A successful submit means the file is queued
    adaptFileStatus.value = 'READY'
    // Poll the adaptation result (returns early once the file changes)
    await pollAdaptResult()
    // Re-check after polling ends (finished/switched): no completion toast when the file changed
    if (selectedFileId.value !== fileId) return
    window.$message.success(t('llm.adapt.execute-success'))
  } catch (error) {
    window.$message.error(error instanceof Error ? error.message : t('llm.adapt.execute-failed'))
  } finally {
    // Reset the button state only while this file is still current, so a new file's state is left alone
    if (selectedFileId.value === fileId) {
      adaptingFile.value = false
    }
  }
}

async function pollAdaptResult() {
  const fileId = selectedFileId.value
  if (!fileId) return
  const maxRetries = 999
  for (let i = 0; i < maxRetries; i++) {
    // Do not wait for the first round, fetch the latest state immediately
    if (i > 0) {
      await new Promise((resolve) => setTimeout(resolve, 5000))
    }
    // Stop polling when the user switched files
    if (selectedFileId.value !== fileId) return
    try {
      const result = await getFileAdaptResult(fileId)
      if (!result) continue
      // v3: decide completion from the file-level status
      if (result.status === 'FINISH') {
        // Adaptation done: write the full text into the diff view
        adaptProgress.done = result.adaptQuestionCount ?? 0
        adaptProgress.total = result.totalQuestionCount ?? 0
        adaptFileStatus.value = result.status ?? ''
        const adapted = result.adaptText
        const rawScheme = result.adaptResult
        if (rawScheme && typeof rawScheme === 'object' && 'selectedScheme' in rawScheme) {
          adaptSchemeResult.value = rawScheme as AdaptSchemeResult
        } else if (typeof rawScheme === 'string') {
          try {
            const parsed = JSON.parse(rawScheme)
            if (parsed && typeof parsed === 'object' && 'selectedScheme' in parsed) {
              adaptSchemeResult.value = parsed as AdaptSchemeResult
            }
          } catch {
            /* not JSON */
          }
        }
        if (adapted != null && adapted !== '') {
          if (!codeTw.isTyping.value && adaptContent.value !== adapted) {
            // Adapted code is shown at once (streaming typing was disabled on request, so long code appears faster)
            codeTw.complete(adapted)
          }
          if (isDiffMode.value && diffEditor) {
            nextTick(() => {
              diffEditor?.getOriginalEditor()?.render()
              diffEditor?.layout()
            })
          }
        }
        // Sync the issue-level adaptation state
        if (result.btQuestionInfos) {
          const prevStatusMap = new Map(fileQuestions.value.map((q) => [q.id, q.status]))
          for (const qi of fileQuestions.value) {
            const match = result.btQuestionInfos.find((a) => a.id === qi.id)
            if (match) {
              qi.status = match.status
              qi.adaptResult = match.adaptResult
            }
          }
          applyFileAnnotations([...fileQuestions.value])
          // Auto-scroll to the latest finished issue
          const newlyFinished = fileQuestions.value.filter(
            (q) => q.status === 'FINISH' && prevStatusMap.get(q.id) !== 'FINISH',
          )
          if (newlyFinished.length > 0 && editor) {
            const lastLine = Math.min(...newlyFinished.map((q) => (q.endLine || q.startLine) + 1))
            editor.revealLineInCenter(lastLine)
          }
        }
        return
      }
      // DOING_QUESTION / DOING_SUGGEST / DOING_FILE / TODO: keep polling
      adaptProgress.done = result.adaptQuestionCount ?? 0
      adaptProgress.total = result.totalQuestionCount ?? 0
      if (result.status && result.status !== 'READY' && result.status !== 'TODO') {
        adaptFileStatus.value = result.status
      }
      // The file-level scheme/text may arrive before FINISH, show it early
      if (!adaptSchemeResult.value && result.adaptResult) {
        const raw = result.adaptResult
        if (typeof raw === 'object' && 'selectedScheme' in raw) {
          adaptSchemeResult.value = raw as AdaptSchemeResult
        } else if (typeof raw === 'string') {
          try {
            const parsed = JSON.parse(raw)
            if (parsed && typeof parsed === 'object' && 'selectedScheme' in parsed) {
              adaptSchemeResult.value = parsed as AdaptSchemeResult
            }
          } catch {
            /* not JSON */
          }
        }
      }
      if (!adaptContent.value && result.adaptText != null && result.adaptText !== '') {
        // Adapted code is shown at once (not streamed)
        codeTw.complete(result.adaptText)
      }
      // Sync the issue-level adaptation state while polling and update the ViewZone live
      if (result.btQuestionInfos) {
        const prevStatusMap = new Map(fileQuestions.value.map((q) => [q.id, q.status]))
        for (const qi of fileQuestions.value) {
          const match = result.btQuestionInfos.find((a) => a.id === qi.id)
          if (match) {
            qi.status = match.status
            qi.adaptResult = match.adaptResult
          }
        }
        applyFileAnnotations([...fileQuestions.value])
        // Auto-scroll to the latest finished issue
        const newlyFinished = fileQuestions.value.filter(
          (q) => q.status === 'FINISH' && prevStatusMap.get(q.id) !== 'FINISH',
        )
        if (newlyFinished.length > 0 && editor) {
          const lastLine = Math.min(...newlyFinished.map((q) => (q.endLine || q.startLine) + 1))
          editor.revealLineInCenter(lastLine)
        }
      }
    } catch {
      // Keep retrying after a polling failure
    }
  }
  throw new Error(t('llm.adapt.execute-failed'))
}

function detectLanguage(filePath: string): string {
  const ext = filePath.split('.').pop()?.toLowerCase() || ''
  const langMap: Record<string, string> = {
    c: 'c',
    h: 'c',
    cpp: 'cpp',
    hpp: 'cpp',
    cc: 'cpp',
    cxx: 'cpp',
    hh: 'cpp',
    java: 'java',
    py: 'python',
    js: 'javascript',
    ts: 'typescript',
    jsx: 'javascript',
    tsx: 'typescript',
    vue: 'html',
    html: 'html',
    css: 'css',
    scss: 'scss',
    less: 'less',
    json: 'json',
    xml: 'xml',
    yml: 'yaml',
    yaml: 'yaml',
    md: 'markdown',
    sh: 'shell',
    bash: 'shell',
    makefile: 'plaintext',
    cmake: 'plaintext',
    txt: 'plaintext',
  }
  return langMap[ext] || 'plaintext'
}

function updateAdaptedEditorContent(content: string | null | undefined) {
  if (!monaco) return
  if (!adaptedEditor && adaptedEditorContainer.value) {
    adaptedEditor = monaco.editor.create(adaptedEditorContainer.value, {
      value: content || '',
      language: 'plaintext',
      theme: appStore.isDark ? 'vs-dark' : 'vs',
      minimap: { enabled: false },
      fontSize: 13,
      lineNumbers: 'on',
      readOnly: true,
      scrollBeyondLastLine: false,
      padding: { top: 8 },
      wordWrap: 'on',
      automaticLayout: true,
    })
  }
  if (!adaptedEditor) return
  const language = selectedFilePath.value ? detectLanguage(selectedFilePath.value) : 'plaintext'
  const model = adaptedEditor.getModel()
  if (model) {
    model.setValue(content || '')
    monaco.editor.setModelLanguage(model, language)
  } else {
    const newModel = monaco.editor.createModel(content || '', language)
    adaptedEditor.setModel(newModel)
  }
}

// A persistent diff model, avoiding frequent create/destroy
let diffOriginalModel: any = null
let diffModifiedModel: any = null

function updateDiffModels(original: string, modified: string | null | undefined) {
  if (!monaco || !diffEditor) return
  const language = selectedFilePath.value ? detectLanguage(selectedFilePath.value) : 'plaintext'

  // Create the model on first use
  if (!diffOriginalModel) {
    diffOriginalModel = monaco.editor.createModel(original, language)
  } else {
    diffOriginalModel.setValue(original)
    monaco.editor.setModelLanguage(diffOriginalModel, language)
  }

  if (!diffModifiedModel) {
    diffModifiedModel = monaco.editor.createModel(modified || '', language)
  } else {
    diffModifiedModel.setValue(modified || '')
    monaco.editor.setModelLanguage(diffModifiedModel, language)
  }

  const currentModel = diffEditor.getModel()
  if (!currentModel || currentModel.original !== diffOriginalModel) {
    diffEditor.setModel({ original: diffOriginalModel, modified: diffModifiedModel })
  }
}

/** Current Monaco editor used to attach annotations (the left original-file editor) */
function _annotationEditor(): any {
  return editor
}

function clearAnnotations() {
  const target = _annotationEditor()
  if (!target) return
  if (annotationDecorations.length > 0) {
    target.deltaDecorations(annotationDecorations, [])
    annotationDecorations = []
  }
  if (annotationZoneIds.length > 0) {
    target.changeViewZones((accessor: any) => {
      for (const id of annotationZoneIds) {
        accessor.removeZone(id)
      }
    })
    annotationZoneIds = []
  }
}

function applyFileAnnotations(questions: QuestionInfo[]) {
  // Clear the old annotations first
  clearAnnotations()

  const target = _annotationEditor()
  if (!target || !monaco || questions.length === 0) return

  const model = target.getModel()
  if (!model) return
  const lineCount = model.getLineCount()

  // 1. Line highlight decorations + squiggly underlines
  const decorations = questions.flatMap((q) => {
    const qType = (q as any).questionType || q.type
    const sev = 'error'
    const hoverLines = [
      `**${TYPE_META[qType]?.label || qType}**`,
      q.ruleName || q.ruleId ? `${t('scan.report.rule-label')}: ${q.ruleName || q.ruleId}` : '',
      '',
      `\`\`\`\n${q.text}\n\`\`\``,
    ]
      .filter(Boolean)
      .join('\n')

    return [
      {
        range: new monaco.Range(q.startLine, 1, q.endLine || q.startLine, 1).setEndPosition(
          q.endLine || q.startLine,
          Number.MAX_SAFE_INTEGER,
        ),
        options: {
          isWholeLine: true,
          className: `fq-${sev}-line`,
          hoverMessage: { value: hoverLines },
        },
      },
      {
        range: new monaco.Range(
          q.startLine,
          q.startCol > 0 ? q.startCol : 1,
          q.endLine || q.startLine,
          q.endCol > 0 ? q.endCol : Number.MAX_SAFE_INTEGER,
        ),
        options: { inlineClassName: `fq-${sev}-squiggle` },
      },
    ]
  })
  annotationDecorations = target.deltaDecorations([], decorations as any)

  // 2. View Zones — single changeViewZones call for ALL zones
  const sorted = [...questions].sort(
    (a, b) => (b.endLine || b.startLine) - (a.endLine || a.startLine),
  )
  target.changeViewZones((accessor: any) => {
    // Per-question zones — at each question's endLine (bottom-to-top to avoid line shift)

    // --- Content width (shared by all zones, computed once) ---
    // Make sure the editor has been laid out (getLayoutInfo returns 0 right after creation)
    target.layout()
    const layoutInfo = target.getLayoutInfo()
    const zoneWidth = Math.max(200, layoutInfo.contentWidth || layoutInfo.width || 400)
    const lineH = target.getOption(monaco!.editor.EditorOption.lineHeight) || 18

    /** Clone into body and measure the real height (line count) at a given width */
    const measureLines = (dom: HTMLElement, overrideCss = ''): number => {
      const clone = dom.cloneNode(true) as HTMLElement
      const baseCss = dom.style.cssText
      clone.style.cssText = `${
        baseCss
      };position:fixed;left:-9999px;top:0;width:${zoneWidth}px;box-sizing:border-box${
        overrideCss ? `;${overrideCss}` : ''
      }`
      document.body.appendChild(clone)
      const px = clone.offsetHeight
      document.body.removeChild(clone)
      return Math.max(1, Math.ceil(px / lineH))
    }

    // Base style template of an adapt zone
    const adaptBaseCss = `margin:4px 0 8px;padding:6px 10px;background:rgba(34,197,94,0.06);border-left:3px solid #22c55e;border-radius:0 4px 4px 0;font-size:12px;line-height:1.6;white-space:pre-wrap;word-break:break-word;max-height:none;overflow:visible;width:${zoneWidth}px;box-sizing:border-box`

    for (let i = 0; i < sorted.length; i++) {
      const q = sorted[i]
      const afterLine = Math.min(q.endLine || q.startLine, lineCount)
      const qType = (q as any).questionType || q.type
      const typeMeta = TYPE_META[qType] || { label: qType || '-', color: '#94a3b8' }

      const zone = document.createElement('div')
      zone.className = 'fq-zone'

      const header = document.createElement('div')
      header.className = 'fq-zone-header'

      const badge = document.createElement('span')
      badge.className = 'fq-zone-badge'
      badge.style.background = typeMeta.color
      badge.textContent = typeMeta.label

      const lines = document.createElement('span')
      lines.className = 'fq-zone-lines'
      lines.textContent = `L${q.startLine}${q.endLine && q.endLine !== q.startLine ? `-${q.endLine}` : ''}`

      header.appendChild(badge)
      header.appendChild(lines)

      // Template description
      const desc = document.createElement('div')
      desc.className = 'fq-zone-rule'
      desc.textContent = q.description || t('scan.report.detected-usage', { label: typeMeta.label })

      zone.appendChild(header)
      zone.appendChild(desc)

      // Override CSS max-height/overflow so the zone fits its natural height
      zone.style.maxHeight = 'none'
      zone.style.overflow = 'visible'

      const descLines = measureLines(zone, 'max-height:none;overflow:visible')

      const zoneId = accessor.addZone({
        afterLineNumber: afterLine,
        heightInLines: descLines,
        domNode: zone,
      })
      annotationZoneIds.push(zoneId)

      // Adaptation suggestion zone (v3): shown in adaptation mode only
      if (props.showAdapt) {
        const isFinish = q.status === 'FINISH'
        const adaptText = isFinish && q.adaptResult ? q.adaptResult.trim() : ''

        // Create the real zone DOM
        const adaptDom = document.createElement('div')
        adaptDom.className = 'fq-zone fq-zone--adapt'
        adaptDom.style.cssText = adaptBaseCss

        // header: AI adaptation suggestion tag | status tag
        const createHeader = () => {
          const h = document.createElement('div')
          h.style.cssText =
            'display:flex;align-items:center;justify-content:space-between;margin-bottom:4px'
          const ai = document.createElement('span')
          ai.className = 'fq-zone-badge'
          ai.style.cssText = 'background:#22c55e'
          ai.textContent = t('scan.report.ai-adapt-suggestion')
          h.appendChild(ai)
          const st = document.createElement('span')
          st.className = 'fq-zone-badge'
          st.style.cssText = isFinish ? 'background:#22c55e' : 'background:#94a3b8'
          st.textContent = isFinish
            ? t('scan.report.question-status-finish')
            : t('scan.report.question-status-todo')
          h.appendChild(st)
          return h
        }
        const createContent = () => {
          const c = document.createElement('div')
          c.style.cssText = isFinish ? 'color:#16a34a' : 'color:#94a3b8;font-style:italic'
          c.textContent = adaptText
          return c
        }

        adaptDom.appendChild(createHeader())
        adaptDom.appendChild(createContent())

        // --- Pre-measurement ---
        const adaptLines = measureLines(adaptDom)

        const zoneCfg: any = {
          afterLineNumber: afterLine,
          heightInLines: adaptLines,
          domNode: adaptDom,
        }
        const adaptZoneId = accessor.addZone(zoneCfg)
        annotationZoneIds.push(adaptZoneId)
      }
    }
  })
}

async function initEditor() {
  if (!editorContainer.value) return

  // Core editor API without bundled languages (splits large chunk into smaller pieces)
  const [monacoModule] = await Promise.all([
    import('monaco-editor/esm/vs/editor/editor.api.js'),
    // Pre-load languages matching detectLanguage() — each becomes a separate chunk
    import('monaco-editor/esm/vs/basic-languages/cpp/cpp.contribution.js'),
    import('monaco-editor/esm/vs/basic-languages/java/java.contribution.js'),
    import('monaco-editor/esm/vs/basic-languages/python/python.contribution.js'),
    import('monaco-editor/esm/vs/basic-languages/javascript/javascript.contribution.js'),
    import('monaco-editor/esm/vs/basic-languages/typescript/typescript.contribution.js'),
    import('monaco-editor/esm/vs/basic-languages/html/html.contribution.js'),
    import('monaco-editor/esm/vs/basic-languages/css/css.contribution.js'),
    import('monaco-editor/esm/vs/basic-languages/scss/scss.contribution.js'),
    import('monaco-editor/esm/vs/basic-languages/less/less.contribution.js'),
    import('monaco-editor/esm/vs/language/json/monaco.contribution.js'),
    import('monaco-editor/esm/vs/basic-languages/xml/xml.contribution.js'),
    import('monaco-editor/esm/vs/basic-languages/yaml/yaml.contribution.js'),
    import('monaco-editor/esm/vs/basic-languages/markdown/markdown.contribution.js'),
    import('monaco-editor/esm/vs/basic-languages/shell/shell.contribution.js'),
  ])
  monaco = monacoModule
  globalThis.MonacoEnvironment = {
    getWorker(_: string, label: string) {
      if (label === 'typescript' || label === 'javascript') {
        return new Worker(
          new URL('monaco-editor/esm/vs/language/typescript/ts.worker.js', import.meta.url),
          { type: 'module' },
        )
      }
      return new Worker(new URL('monaco-editor/esm/vs/editor/editor.worker.js', import.meta.url), {
        type: 'module',
      })
    },
  }

  // Left editor: original file (supports annotations)
  editor = monaco.editor.create(editorContainer.value, {
    value: '',
    language: 'plaintext',
    theme: appStore.isDark ? 'vs-dark' : 'vs',
    minimap: { enabled: false },
    fontSize: 13,
    lineNumbers: 'on',
    readOnly: true,
    scrollBeyondLastLine: false,
    padding: { top: 8 },
    wordWrap: 'on',
    automaticLayout: true,
    codeLens: false,
  })

  // Right editor: adapted code (read-only, no annotations)
  if (props.showAdapt && adaptedEditorContainer.value && !adaptedEditor) {
    adaptedEditor = monaco.editor.create(adaptedEditorContainer.value, {
      value: '',
      language: 'plaintext',
      theme: appStore.isDark ? 'vs-dark' : 'vs',
      minimap: { enabled: false },
      fontSize: 13,
      lineNumbers: 'on',
      readOnly: true,
      scrollBeyondLastLine: false,
      padding: { top: 8 },
      wordWrap: 'on',
      automaticLayout: true,
    })
  }
}

function disposeEditor() {
  if (resizeTimer) clearTimeout(resizeTimer)
  resizeTimer = null
  resizeObserver?.disconnect()
  resizeObserver = null
  lensDisposable?.dispose()
  lensDisposable = null
  cmdDisposable?.dispose()
  cmdDisposable = null
  adaptedEditor?.dispose()
  adaptedEditor = null
  diffEditor?.dispose()
  diffEditor = null
  diffOriginalModel?.dispose()
  diffOriginalModel = null
  diffModifiedModel?.dispose()
  diffModifiedModel = null
  editor?.dispose()
  editor = null
  themeWatcher?.()
  themeWatcher = null
}

onMounted(() => {
  nextTick(() => {
    initEditor().catch(() => {
      // A failed Monaco init must not block the rest
    })
    // Watch dark-mode switches and sync the Monaco theme
    themeWatcher = watch(
      () => appStore.isDark,
      (dark) => {
        monaco?.editor.setTheme(dark ? 'vs-dark' : 'vs')
        editor?.updateOptions({ theme: dark ? 'vs-dark' : 'vs' })
        adaptedEditor?.updateOptions({ theme: dark ? 'vs-dark' : 'vs' })
        diffEditor?.getOriginalEditor()?.updateOptions({ theme: dark ? 'vs-dark' : 'vs' })
        diffEditor?.getModifiedEditor()?.updateOptions({ theme: dark ? 'vs-dark' : 'vs' })
      },
    )
    // Re-measure the adapt zone height when the window resizes
    const container = editorContainer.value
    if (container) {
      resizeObserver = new ResizeObserver(() => {
        if (resizeTimer) clearTimeout(resizeTimer)
        resizeTimer = setTimeout(() => {
          resizeTimer = null
          if (!editor) return // Component destroyed
          if (fileQuestions.value.length > 0) {
            applyFileAnnotations([...fileQuestions.value])
            editor?.layout()
          }
        }, 150)
      })
      resizeObserver.observe(container)
    }
  })
})

// Scheme C + code tab: refresh content and layout when entering diff mode
watch(isDiffMode, (enterDiff) => {
  if (enterDiff && monaco) {
    nextTick(() => {
      if (!diffEditorContainer.value) return
      // Create the diff editor lazily once its container is visible with the right size
      if (!diffEditor) {
        diffEditor = monaco.editor.createDiffEditor(diffEditorContainer.value, {
          readOnly: true,
          theme: appStore.isDark ? 'vs-dark' : 'vs',
          minimap: { enabled: false },
          fontSize: 13,
          scrollBeyondLastLine: false,
          padding: { top: 8 },
          wordWrap: 'on',
          automaticLayout: true,
          renderSideBySide: true,
          renderSideBySideInlineBreakpoint: 0,
          originalEditable: false,
        } as any)
      }
      const originalContent = fileContent.value
      const modifiedContent = adaptContent.value || originalContent
      updateDiffModels(originalContent, modifiedContent)
      diffEditor.layout()
      requestAnimationFrame(() => {
        diffEditor?.layout()
        diffEditor?.getOriginalEditor()?.render()
      })
    })
  } else if (!enterDiff) {
    // Leaving diff mode: restore the split editor layout and recompute the annotations
    nextTick(() => {
      editor?.layout()
      adaptedEditor?.layout()
      requestAnimationFrame(() => {
        editor?.layout()
        if (fileQuestions.value.length > 0) {
          applyFileAnnotations([...fileQuestions.value])
        }
      })
    })
  }
})

// Scheme B: restore the split editor layout and recompute the annotations when leaving Markdown mode
watch(isMarkdownMode, (enterMarkdown) => {
  if (!enterMarkdown) {
    nextTick(() => {
      editor?.layout()
      adaptedEditor?.layout()
      requestAnimationFrame(() => {
        editor?.layout()
        if (fileQuestions.value.length > 0) {
          applyFileAnnotations([...fileQuestions.value])
        }
      })
    })
  }
})

// Create/destroy adaptedEditor when its container appears or disappears
// (showRightPanel uses v-if, which destroys and rebuilds the DOM, so the editor must be rebuilt too)
watch(showRightPanel, (show) => {
  if (!show) {
    // Right panel hidden → DOM removed → dispose the old editor and rebuild it on the next show
    adaptedEditor?.dispose()
    adaptedEditor = null
    return
  }
  if (show && monaco) {
    nextTick(() => {
      if (adaptedEditor || !adaptedEditorContainer.value) return
      adaptedEditor = monaco.editor.create(adaptedEditorContainer.value, {
        value: '',
        language: 'plaintext',
        theme: appStore.isDark ? 'vs-dark' : 'vs',
        minimap: { enabled: false },
        fontSize: 13,
        lineNumbers: 'on',
        readOnly: true,
        scrollBeyondLastLine: false,
        padding: { top: 8 },
        wordWrap: 'on',
        automaticLayout: true,
      })
      // Write the adapted content right away when it exists
      if (adaptContent.value) {
        updateAdaptedEditorContent(adaptContent.value)
      }
    })
  }
})

// Trigger a layout when the adapted editor's visibility changes so Monaco renders correctly
watch(showAdaptedEditor, (visible) => {
  if (visible && adaptedEditor) {
    nextTick(() => {
      adaptedEditor?.layout()
    })
  }
})

onUnmounted(() => {
  if (resizeTimer) clearTimeout(resizeTimer)
  resizeTimer = null
  resizeObserver?.disconnect()
  resizeObserver = null
  disposeEditor()
})

// Scroll back to the top when switching to the "online report" tab (scrollTo is a no-op while the panel is hidden, so wait for it to show)
watch(activeQuestionTab, (tab) => {
  if (tab === 'report') {
    nextTick(() => {
      activeReportSection.value = REPORT_NAV_ITEMS.value[0].id
      reportContentRef.value?.scrollTo(0, 0)
      reportNavRef.value?.scrollTo(0, 0)
    })
  }
})

function scrollToSection(id: string) {
  const el = document.getElementById(id)
  if (el) el.scrollIntoView({ behavior: 'smooth' })
}

async function loadReportData() {
  if (!props.taskId) return

  const requestSeq = ++reportRequestSeq
  reportInfo.value = null

  // --- Reset the file content state (clear the previous editor content when the report reopens) ---
  selectedFilePath.value = ''
  selectedFileId.value = ''
  fileContent.value = ''
  adaptContent.value = ''
  codeTw.reset()
  adaptSchemeResult.value = null
  rightTab.value = 'scheme'
  adaptProgress.done = 0
  adaptProgress.total = 0
  adaptFileStatus.value = ''
  adaptingFile.value = false
  fileLoading.value = false
  fileQuestions.value = []
  fileTreeFilter.value = ''
  fileTreeData.value = []
  loadFileTree()
  clearAnnotations()
  if (props.showAdapt && adaptedEditor) {
    updateAdaptedEditorContent('')
  }
  if (diffOriginalModel) {
    diffOriginalModel.setValue('')
  }
  if (diffModifiedModel) {
    diffModifiedModel.setValue('')
  }
  if (diffEditor) {
    updateDiffModels('', '')
  }
  if (editor) {
    const model = editor.getModel()
    if (model) model.setValue('')
  }

  questionList.value = []
  activeQuestionTab.value = props.showOverview !== false ? 'overview' : 'report'
  frameworkPortrait.value = null
  libList.value = []
  asmList.value = null
  makeFileList.value = []
  questionLoading.value = true
  if (props.projectId) {
    reportLoading.value = true
  }
  try {
    const [list, portrait, libs, asms, makeFiles] = await Promise.all([
      getQuestionList(props.taskId, props.showAdapt ? '1' : '0'),
      getFrameworkPortrait(props.taskId).catch(() => null),
      getLibList(props.taskId).catch(() => []),
      getAsmList(props.taskId).catch(() => null),
      getMakeFileInfo(props.taskId).catch(() => []),
    ])
    frameworkPortrait.value = portrait
    libList.value = Array.isArray(libs) ? libs : []
    asmList.value = asms
    makeFileList.value = makeFiles
    if (props.projectId) {
      reportInfo.value = await getReportInfo(props.projectId).catch(() => null)
    }
    // Drop the stale response: a newer loadReportData started meanwhile
    if (requestSeq !== reportRequestSeq) return
    questionList.value = list.map((item: QuestionInfo) => {
      if (item.ruleId) {
        try {
          const info = JSON.parse(item.ruleId) as RuleInfo
          return {
            ...item,
            ruleName:
              info.libName ||
              info.headerName ||
              info.macroName ||
              info.instructionName ||
              info.keyword,
          }
        } catch {
          return { ...item, ruleName: undefined }
        }
      }
      return item
    })
  } catch {
    window.$message.error(t('scan.messages.detail-failed'))
  } finally {
    questionLoading.value = false
    if (requestSeq === reportRequestSeq) {
      reportLoading.value = false
    }
  }
}

defineExpose({
  loadReportData,
  batchAdaptProgress,
  setAdaptContent: (content: string) => {
    if (!props.showAdapt) return
    adaptContent.value = content
    updateAdaptedEditorContent(content)
  },
})

// --- Row index helper ---
// --- Report issue table columns (getReportInfo issueCategories) ---
/**
 * Issue detail columns, which depend on the entry point:
 * - Porting check entry (showAdapt=false): index / file name / code line / issue description (same as the offline report)
 * - AI adaptation entry (showAdapt=true): adds the "modification suggestion" column (same as the offline adapted report)
 */
const reportIssueColumns = computed<any[]>(() => {
  const cols: any[] = [
    { title: t('scan.report.col-index'), key: 'index', width: 60 },
    {
      title: t('scan.report.col-file-path'),
      dataIndex: 'fileName',
      key: 'fileName',
      minWidth: 200,
      ellipsis: true,
    },
    {
      title: t('scan.report.col-line-num'),
      dataIndex: 'lineNumber',
      key: 'lineNumber',
      width: 90,
    },
    {
      title: t('scan.report.issue-description'),
      dataIndex: 'description',
      key: 'description',
      minWidth: 300,
      ellipsis: true,
    },
  ]
  if (props.showAdapt) {
    cols.push({
      title: t('scan.report.issue-advice'),
      dataIndex: 'suggestion',
      key: 'suggestion',
      minWidth: 260,
      ellipsis: true,
    })
  }
  return cols
})

// --- Report file-type table columns (architecture-related file detection) ---
const reportFileTypeColumns: any[] = [
  { title: t('scan.report.col-index'), key: 'index', width: 60 },
  { title: t('scan.report.col-file-type'), dataIndex: 'label', key: 'label', minWidth: 160 },
  {
    title: t('scan.report.col-file-count'),
    dataIndex: 'fileCount',
    key: 'fileCount',
    width: 110,
  },
  {
    title: t('scan.report.col-code-lines'),
    dataIndex: 'lineCount',
    key: 'lineCount',
    width: 130,
  },
]

// --- Report: derived data needed to mirror the offline report (docx export) layout ---

/** Architecture-related file detection: four file kinds × file count / code lines (from the getReportInfo backend fields) */
const reportFileTypeRows = computed(() => {
  const info = reportInfo.value
  if (!info) return []
  const labels: Record<string, string> = {
    src: t('scan.report.file-type-src'),
    asm: t('scan.report.file-type-asm'),
    build: t('scan.report.file-type-build'),
    other: t('scan.report.file-type-other'),
  }
  const groups = [
    { key: 'src', fileCount: info.srcFileCount, lineCount: info.srcFileLines },
    { key: 'asm', fileCount: info.asmFileCount, lineCount: info.asmFileLines },
    { key: 'build', fileCount: info.buildFileCount, lineCount: info.buildFileLines },
    { key: 'other', fileCount: info.otherFileCount, lineCount: info.otherFileLines },
  ] as const
  return groups.map((group) => ({
    key: group.key,
    label: labels[group.key],
    fileCount: group.fileCount ?? 0,
    lineCount: group.lineCount ?? 0,
  }))
})

/** Build system (from getReportInfo.buildSystem) */
const reportBuildSystemText = computed(() => {
  const text = reportInfo.value?.buildSystem
  return text ? String(text) : t('scan.report.none')
})

/** Custom row rendering of the detection table (by column name) */
function reportFileTypeRowData(col: 'fileCount' | 'lineCount', row: any): string | number {
  const val = row[col] ?? 0
  return col === 'lineCount' ? val.toLocaleString() : val
}

/** Detection conclusion: text block (name row + two levels of detail) */
const reportConclusionText = computed(() => {
  const info = reportInfo.value
  if (!info) return ''
  const nameLine = t('scan.report.conclusion-name-line', {
    name: info.subjectName || t('scan.report.unnamed-project'),
    totalFiles: String(info.totalFiles ?? '').replace(/[个\s]+$/, ''),
    codeLines: info.codeLines,
  })
  const fileSituations = t('scan.report.conclusion-file-situations', {
    archIssues: info.conclusionArchIssues ?? 0,
    asmIssues: info.conclusionAsmIssues ?? 0,
    buildIssues: info.conclusionBuildIssues ?? 0,
    otherIssues: info.conclusionOtherIssues ?? 0,
  })
  const codeSituations = t('scan.report.conclusion-code-situations', {
    scannedFiles: info.scannedFiles,
    codeLines: info.codeLines,
    modifyFiles: info.modifyFiles,
    modifyLocations: info.modifyLocations,
    coveredRules: info.coveredRules,
  })
  return `${nameLine}${fileSituations}${codeSituations}`
})

/** Detection conclusion: distribution of code to modify */
const reportConclusionDistribution = computed(() => {
  const info = reportInfo.value
  if (!info) return []
  const items = [
    {
      key: 'header',
      tpl: 'conclusion-dist-header',
      count: info.headerIssues ?? 0,
      percent: info.headerPercent ?? '0%',
    },
    {
      key: 'macro',
      tpl: 'conclusion-dist-macro',
      count: info.macroIssues ?? 0,
      percent: info.macroPercent ?? '0%',
    },
    {
      key: 'asm',
      tpl: 'conclusion-dist-asm',
      count: info.asmIssues ?? 0,
      percent: info.asmPercent ?? '0%',
    },
    {
      key: 'other',
      tpl: 'conclusion-dist-other',
      count: info.otherIssues ?? 0,
      percent: info.otherPercent ?? '0.00%',
    },
  ]
  return items.map((item) =>
    t(`scan.report.${item.tpl}` as string, { count: item.count, percent: item.percent }),
  )
})
</script>

<template>
  <div
    class="scan-report-content"
    style="display: flex; flex: 1; flex-direction: column; overflow: hidden"
  >
    <ASpin :spinning="questionLoading" style="display: flex; flex: 1; flex-direction: column">
      <LightTabs
        v-model="activeQuestionTab"
        :list="tabs"
        style="display: flex; flex: 1; flex-direction: column"
      >
        <template #overview>
          <LightCard
            style="flex: 1; padding: 0; margin-top: 24px; overflow-y: auto; border-radius: 30px"
          >
            <AtlasPanel
              :atlas-source-language-rows="atlasSourceLanguageRows"
              :atlas-source-hidden-type-count="0"
              :atlas-source-total-line-text="atlasSourceTotalLineText"
              :atlas-source-total-line-style="atlasSourceTotalLineStyle"
              :atlas-source-total-file-count="atlasSourceTotalFileCount"
              :atlas-main-language-label="atlasMainLanguageLabel"
              :atlas-source-language-text="atlasSourceLanguageText"
              :atlas-source-build-systems-text="atlasSourceBuildSystemsText"
              :atlas-assembly-total-count="atlasAssemblyTotalCount"
              :atlas-assembly-file-count="atlasAssemblyFileCount"
              :atlas-inline-asm-count="atlasInlineAsmCount"
              :atlas-assembly-notice="atlasAssemblyNotice"
              :atlas-assembly-architecture-rows="atlasAssemblyArchitectureRows"
              :atlas-mainboard-stats="atlasMainboardStats"
              :atlas-mainboard-pie-rows="atlasMainboardPieRows"
              :atlas-mainboard-pie-option="atlasMainboardPieOption"
              :atlas-dep-total-count="atlasDepTotalCount"
              :atlas-dep-supported-count="atlasDepSupportedCount"
              :atlas-dep-unsupported-count="atlasDepUnsupportedCount"
              :atlas-dependency-notice="atlasDependencyNotice"
              :atlas-build-analyzed-files="atlasBuildAnalyzedFiles"
              :atlas-build-pattern-count="atlasBuildPatternCount"
              :atlas-build-arch-count="atlasBuildArchCount"
              :atlas-build-arch-rows="atlasBuildArchRows"
              :atlas-build-notice="atlasBuildNotice"
              :dep-lib-groups="depLibGroups"
            />
          </LightCard>
        </template>
        <template #report>
          <LightCard
            style="flex: 1; padding: 0; margin-top: 24px; overflow-y: auto; border-radius: 30px"
          >
            <div style="display: flex; flex: 1; overflow: hidden">
              <nav
                ref="reportNavRef"
                class="report-nav-left"
                style="
                  flex-shrink: 0;
                  width: 160px;
                  padding: 20px 16px;
                  overflow-y: auto;
                  border-right: 1px solid #e8edf5;
                "
              >
                <div
                  style="
                    padding-bottom: 12px;
                    margin-bottom: 16px;
                    font-size: 15px;
                    font-weight: 700;
                    color: #1a1a2e;
                    border-bottom: 2px solid #e8edf5;
                  "
                >
                  {{ t('scan.report.nav-toc') }}
                </div>
                <a
                  v-for="item in REPORT_NAV_ITEMS"
                  :key="item.id"
                  class="report-nav-left-item"
                  :class="{ 'report-nav-left-item--active': activeReportSection === item.id }"
                  href="#"
                  @click.prevent="scrollToSection(item.id)"
                  >{{ item.label }}</a
                >
              </nav>
              <!-- Right content (scrolls on its own) -->
              <div
                ref="reportContentRef"
                class="report-content"
                style="height: calc(100vh - 230px); padding: 24px; overflow-y: auto"
                @scroll="onReportScroll"
              >
                <!-- Report title -->
                <h2 style="margin: 0 0 16px; font-size: 22px; font-weight: 600; color: #1a1a2e">
                  {{ reportInfo?.subjectName ? `${reportInfo.subjectName} ` : ''
                  }}{{ t('scan.report.title-full') }}
                </h2>

                <!-- Report loading -->
                <div v-if="reportLoading" class="report-loading">
                  <RenderIcon
                    icon="i-ant-design:loading-outlined"
                    class="text-blue-500 size-6 animate-spin"
                  />
                  <span>{{ t('scan.report.loading-report') }}</span>
                </div>

                <!-- No report data -->
                <div v-else-if="!reportInfo" class="report-loading">
                  <span>{{ t('scan.report.no-report-data') }}</span>
                </div>

                <template v-else>
                  <!-- 1. Report summary -->
                  <div
                    id="report-intro"
                    class="report-section"
                    style="padding-bottom: 16px; border-bottom: 1px solid #f0f2f5"
                  >
                    <h3 class="report-section-title">{{ t('scan.report.report-intro') }}</h3>
                    <p class="report-text">{{ t('scan.report.intro-desc-1') }}</p>
                    <p class="report-text">{{ t('scan.report.intro-desc-2') }}</p>
                  </div>

                  <!-- 2. Detection target -->
                  <div id="report-object" class="report-section">
                    <h3 class="report-section-title">{{ t('scan.report.report-object') }}</h3>
                    <p class="report-text">{{ t('scan.report.intro-basic-desc') }}</p>
                    <h4 class="report-sub-title">{{ t('scan.report.basic-info') }}</h4>
                    <LightDescriptions
                      class="report-project-info"
                      :column="2"
                      :rows="[
                        {
                          cols: [
                            {
                              label: t('scan.report.subject-name'),
                              content: reportInfo.subjectName || t('scan.report.unnamed-project'),
                            },
                            {
                              label: t('scan.report.language-label'),
                              content: reportInfo.language,
                            },
                          ],
                        },
                        {
                          cols: [
                            {
                              label: t('scan.report.os-label'),
                              content: reportOsText,
                            },
                            {
                              label: t('scan.report.build-system-label'),
                              content: reportBuildSystemText,
                            },
                          ],
                        },
                        {
                          cols: [
                            {
                              label: t('scan.report.total-files-label'),
                              content: reportInfo.totalFiles,
                            },
                            {
                              label: t('scan.report.total-lines-label'),
                              content: reportInfo.totalLines,
                            },
                          ],
                        },
                      ]"
                    />
                  </div>

                  <!-- 3. Detection details -->
                  <div id="report-situation" class="report-section">
                    <h3 class="report-section-title">{{ t('scan.report.report-situation') }}</h3>
                    <p class="report-text">{{ t('scan.report.intro-situation-desc') }}</p>
                    <h4 class="report-sub-title">{{ t('scan.report.arch-file-check') }}</h4>
                    <p class="report-text">{{ t('scan.report.intro-arch-file-desc') }}</p>
                    <ATable
                      :data-source="reportFileTypeRows"
                      :columns="reportFileTypeColumns"
                      size="small"
                      bordered
                      :pagination="false"
                      row-key="key"
                    >
                      <template #bodyCell="{ column, record, index }">
                        <template v-if="column.key === 'index'">{{ index + 1 }}</template>
                        <template v-else-if="column.key === 'fileCount'">
                          {{ reportFileTypeRowData('fileCount', record) }}
                        </template>
                        <template v-else-if="column.key === 'lineCount'">
                          {{ reportFileTypeRowData('lineCount', record) }}
                        </template>
                      </template>
                    </ATable>
                  </div>

                  <!-- 4. Detection conclusion -->
                  <div id="report-conclusion" class="report-section">
                    <h3 class="report-section-title">{{ t('scan.report.report-conclusion') }}</h3>
                    <p class="report-text">{{ reportConclusionText }}</p>
                    <h4 class="report-sub-title">
                      {{ t('scan.report.conclusion-distribution-title') }}
                    </h4>
                    <ul class="report-distribution-list">
                      <li
                        v-for="(line, idx) in reportConclusionDistribution"
                        :key="idx"
                        class="report-distribution-item"
                      >
                        {{ line }}
                      </li>
                    </ul>
                  </div>

                  <!-- Report statement -->
                  <div id="report-declaration" class="report-section">
                    <h3 class="report-section-title">{{ t('scan.report.declaration-title') }}</h3>
                    <ol class="report-declaration-list">
                      <li class="report-declaration-item">
                        {{ t('scan.report.declaration-1') }}
                      </li>
                      <li class="report-declaration-item">
                        {{ t('scan.report.declaration-2') }}
                      </li>
                      <li class="report-declaration-item">
                        {{ t('scan.report.declaration-3') }}
                      </li>
                    </ol>
                  </div>

                  <!-- 5. Issue details -->
                  <div id="report-issues" class="report-section">
                    <h3 class="report-section-title">{{ t('scan.report.issue-detail') }}</h3>
                    <ATable
                      :data-source="reportInfo.issues || []"
                      :columns="reportIssueColumns"
                      size="small"
                      bordered
                      :pagination="false"
                      row-key="index"
                    >
                      <template #bodyCell="{ column, index }">
                        <template v-if="column.key === 'index'">{{ index + 1 }}</template>
                      </template>
                    </ATable>
                  </div>
                </template>
              </div>
            </div>
          </LightCard>
        </template>
        <template #fileContent>
          <LightCard
            style="
              flex: 1;
              height: calc(100vh - 230px);
              padding: 0;
              margin-top: 24px;
              overflow: hidden;
              border-radius: 20px;
            "
          >
            <div class="flex gap-0 h-full" style="min-height: 600px">
              <!-- Left: File Tree -->
              <div
                class="fc-left-panel flex flex-shrink-0 flex-col"
                style="width: 360px; border-right: 1px solid #e8edf5"
              >
                <div
                  class="fc-header px-3 py-2 flex items-center justify-between"
                  style="border-bottom: 1px solid #e8edf5"
                >
                  <span class="text-slate-600 font-medium text-sm">{{
                    t('scan.report.project-files')
                  }}</span>
                  <AInput
                    v-model:value="fileTreeFilter"
                    :placeholder="t('scan.report.search-file')"
                    allow-clear
                    size="small"
                    style="width: 140px"
                  />
                </div>
                <div style="flex: 1; padding: 8px; overflow-y: auto">
                  <ATree
                    v-if="fileTreeFiltered.length > 0"
                    :tree-data="fileTreeFiltered"
                    :field-names="{
                      key: 'id',
                      title: 'label',
                      children: 'children',
                    }"
                    :load-data="onLoadData"
                    :default-expanded-keys="fileTreeExpandedKeys"
                    :selected-keys="selectedFilePath ? [selectedFilePath] : []"
                    @select="onFileTreeSelect"
                  >
                    <template #titleRender="nodeData">
                      <span class="flex gap-1 w-full items-center" style="line-height: 1.5">
                        <span class="min-w-0 truncate">
                          <svg
                            v-if="nodeData.isDir"
                            class="tree-icon mr-1 align-middle h-3.5 w-3.5 inline"
                            :class="(nodeData.errCount ?? 0) > 0 ? 'tree-icon--warn' : ''"
                            viewBox="0 0 24 24"
                            fill="none"
                            stroke="currentColor"
                            stroke-width="2"
                            stroke-linecap="round"
                            stroke-linejoin="round"
                          >
                            <path
                              d="M22 19a2 2 0 0 1-2 2H4a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h5l2 3h9a2 2 0 0 1 2 2z"
                            />
                          </svg>
                          <svg
                            v-else
                            class="tree-icon mr-1 align-middle h-3.5 w-3.5 inline"
                            :class="(nodeData.errCount ?? 0) > 0 ? 'tree-icon--warn' : ''"
                            viewBox="0 0 24 24"
                            fill="none"
                            stroke="currentColor"
                            stroke-width="2"
                            stroke-linecap="round"
                            stroke-linejoin="round"
                          >
                            <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z" />
                            <polyline points="14 2 14 8 20 8" />
                          </svg>
                          {{ nodeData.label }}
                        </span>
                        <ATag
                          v-if="(nodeData.errCount ?? 0) > 0"
                          color="error"
                          style="padding: 0 6px; font-size: 11px; line-height: 18px; border: none"
                          >{{ nodeData.errCount }}</ATag
                        >
                      </span>
                    </template>
                  </ATree>
                  <div v-else class="text-slate-400 py-8 text-center text-sm">
                    {{ t('scan.report.no-files') }}
                  </div>
                </div>
                <!-- Stats footer -->
                <div
                  v-if="fileTreeData.length > 0"
                  class="fc-stats text-xs text-slate-500 px-3 py-2 flex-shrink-0"
                  style="border-top: 1px solid #e8edf5"
                >
                  <span class="text-red-500 font-medium">{{ fileTreeStats.totalQuestions }}</span>
                  {{ t('scan.report.issue-count') }}
                </div>
              </div>
              <!-- Editor area -->
              <div class="flex flex-1 flex-col min-w-0">
                <!-- Right tab bar (only with a scheme object, always visible) -->
                <div
                  v-if="showAdapt && hasFileIssues && selectedFilePath && hasAdaptScheme"
                  class="px-3 py-1 border-b flex shrink-0 gap-1 items-center justify-end"
                  style="background: #f8fafc; border-color: #e8edf5"
                >
                  <button
                    class="text-xs px-3 py-1 transition-colors rounded-t"
                    :class="
                      rightTab === 'scheme'
                        ? 'bg-white text-slate-800 font-semibold border border-b-white'
                        : 'text-slate-400 hover:text-slate-600'
                    "
                    style="margin-bottom: -1px; border-color: #e8edf5"
                    @click="rightTab = 'scheme'"
                  >
                    {{ t('scan.report.adapt-scheme') }}
                  </button>
                  <button
                    class="text-xs px-3 py-1 transition-colors rounded-t"
                    :class="
                      rightTab === 'code'
                        ? 'bg-white text-slate-800 font-semibold border border-b-white'
                        : 'text-slate-400 hover:text-slate-600'
                    "
                    style="margin-bottom: -1px; border-color: #e8edf5"
                    @click="rightTab = 'code'"
                  >
                    {{
                      adaptSchemeResult?.selectedScheme === 'A'
                        ? t('scan.report.view-new-file')
                        : adaptSchemeResult?.selectedScheme === 'C'
                          ? t('scan.report.code-diff')
                          : t('scan.report.mod-steps')
                    }}
                  </button>
                </div>

                <!-- Content -->
                <div class="flex flex-1 flex-col relative" style="min-height: 550px">
                  <!-- Scheme C + code tab: full-width diff editor -->
                  <div v-show="effectiveIsDiffMode" class="flex flex-col h-full">
                    <!-- Diff labels: before on the left, after on the right -->
                    <div
                      class="border-b flex shrink-0"
                      style="background: #f8fafc; border-color: #e8edf5"
                    >
                      <div class="text-xs text-slate-500 font-medium px-3 py-1.5 flex-1">
                        {{ t('scan.report.before-adapt') }}
                      </div>
                      <div
                        class="text-xs text-slate-500 font-medium px-3 py-1.5 flex-1"
                        style="border-left: 1px solid #e8edf5"
                      >
                        {{ t('scan.report.after-adapt') }}
                      </div>
                    </div>
                    <div class="flex-1 relative">
                      <div ref="diffEditorContainer" class="inset-0 absolute" style="z-index: 5" />
                      <!-- Loading shown in scheme C diff mode while adaptText is still missing -->
                      <div
                        v-if="!hasAdaptResult && adaptIsRunning"
                        class="flex flex-col gap-3 items-center inset-0 justify-center absolute z-10"
                        style="background: #f9fbff"
                      >
                        <RenderIcon
                          icon="i-ant-design:loading-outlined"
                          class="text-blue-500 size-6 animate-spin"
                        />
                        <span class="text-slate-500 text-sm">{{ codeTabLoadingText }}</span>
                      </div>
                    </div>
                  </div>

                  <!-- Scheme B + code tab: Markdown modification steps -->
                  <div
                    v-show="isMarkdownMode"
                    class="p-4 inset-0 absolute overflow-y-auto"
                    style="background: #ffffff"
                  >
                    <template v-if="adaptContent">
                      <div class="max-w-3xl prose prose-slate prose-sm" v-html="modStepsHtml" />
                    </template>
                    <div
                      v-else-if="adaptIsRunning"
                      class="flex flex-col gap-3 h-full items-center justify-center"
                    >
                      <RenderIcon
                        icon="i-ant-design:loading-outlined"
                        class="text-blue-500 size-6 animate-spin"
                      />
                      <span class="text-slate-500 text-sm">{{ codeTabLoadingText }}</span>
                    </div>
                  </div>

                  <!-- Normal mode: split left/right -->
                  <div
                    v-show="!effectiveIsDiffMode && !isMarkdownMode"
                    class="flex inset-0 absolute"
                  >
                    <!-- ====== Left: original file editor ====== -->
                    <div
                      class="flex flex-1 flex-col relative"
                      :style="showRightPanel ? 'width: 50%' : 'width: 100%'"
                    >
                      <!-- File path bar -->
                      <div
                        class="fc-toolbar text-xs text-slate-400 px-3 py-2 flex gap-2 items-center"
                        style="border-bottom: 1px solid #e8edf5"
                      >
                        <span class="text-slate-500 font-medium truncate">{{
                          selectedFilePath || t('scan.report.select-file')
                        }}</span>
                        <span v-if="fileLoading" class="text-blue-500 ml-auto">{{
                          t('scan.report.loading')
                        }}</span>
                        <span
                          v-else-if="fileQuestions.length > 0"
                          class="text-amber-500 font-medium ml-auto"
                        >
                          {{ fileQuestions.length }} {{ t('scan.report.issue-unit') }}
                        </span>
                        <span v-else-if="selectedFilePath" class="text-slate-300 ml-auto">{{
                          t('scan.report.no-issues-short')
                        }}</span>
                      </div>
                      <!-- Editor container -->
                      <div class="flex-1 relative">
                        <div ref="editorContainer" class="inset-0 absolute" />
                        <!-- Left empty state -->
                        <div
                          v-if="!selectedFilePath && !fileLoading"
                          class="file-empty-state text-slate-300 flex flex-col select-none items-center inset-0 justify-center absolute"
                          style="background: #f9fbff"
                        >
                          <svg
                            class="mb-4"
                            width="64"
                            height="64"
                            viewBox="0 0 24 24"
                            fill="none"
                            stroke="currentColor"
                            stroke-width="1.2"
                            stroke-linecap="round"
                            stroke-linejoin="round"
                          >
                            <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z" />
                            <polyline points="14 2 14 8 20 8" />
                            <line x1="16" y1="13" x2="8" y2="13" />
                            <line x1="16" y1="17" x2="8" y2="17" />
                            <polyline points="10 9 9 9 8 9" />
                          </svg>
                          <span class="text-base">{{ t('scan.report.select-file-hint') }}</span>
                          <span class="mt-1 text-sm">{{ t('scan.report.select-file-desc') }}</span>
                        </div>
                      </div>
                    </div>

                    <!-- ====== Right: adaptation scheme / adapted code ====== -->
                    <div
                      v-if="showRightPanel"
                      class="flex flex-1 flex-col relative"
                      style="width: 50%; border-left: 1px solid #e8edf5"
                    >
                      <!-- Right content area -->
                      <div class="flex flex-1 flex-col relative">
                        <!-- Added file path bar (shown for scheme A) -->
                        <div
                          v-if="
                            hasAdaptScheme &&
                            rightTab === 'code' &&
                            adaptSchemeResult?.selectedScheme === 'A' &&
                            adaptSchemeResult.suggestedRiscvFilePath
                          "
                          class="fc-toolbar text-xs text-slate-400 px-3 py-2 flex shrink-0 gap-2 items-center"
                          style="border-bottom: 1px solid #e8edf5"
                        >
                          <span class="text-green-500 font-medium truncate">{{
                            adaptSchemeResult.suggestedRiscvFilePath.split('/').pop()
                          }}</span>
                        </div>
                        <!-- Adapted code editor container -->
                        <div class="flex-1 relative">
                          <div
                            v-show="
                              (hasAdaptScheme && rightTab === 'code') ||
                              (!hasAdaptScheme && hasAdaptResult)
                            "
                            ref="adaptedEditorContainer"
                            class="inset-0 absolute"
                          />
                          <!-- Loading shown on the code tab while adaptText is still missing -->
                          <div
                            v-if="
                              hasAdaptScheme &&
                              rightTab === 'code' &&
                              !hasAdaptResult &&
                              adaptIsRunning
                            "
                            class="flex flex-col gap-3 items-center inset-0 justify-center absolute z-10"
                            style="background: #f9fbff"
                          >
                            <RenderIcon
                              icon="i-ant-design:loading-outlined"
                              class="text-blue-500 size-6 animate-spin"
                            />
                            <span class="text-slate-500 text-sm">{{ codeTabLoadingText }}</span>
                          </div>
                        </div>
                        <!-- Adaptation scheme panel -->
                        <div
                          v-if="hasAdaptScheme && rightTab === 'scheme'"
                          class="p-4 inset-0 absolute overflow-y-auto"
                          style="background: #ffffff"
                        >
                          <template v-if="adaptSchemeResult">
                            <!-- Scheme arrived but is still being generated (the scheme A loading hint moved down to the added file list) -->
                            <div
                              v-if="adaptIsRunning && adaptSchemeResult?.selectedScheme !== 'A'"
                              class="text-xs mb-3 px-3 py-2 flex gap-2 items-center rounded-lg"
                              style="color: #3b82f6; background: #eff6ff; border: 1px solid #bfdbfe"
                            >
                              <RenderIcon
                                icon="i-ant-design:loading-outlined"
                                class="shrink-0 size-3 animate-spin"
                              />
                              <span>{{ codeTabLoadingText }}</span>
                            </div>
                            <div class="max-w-2xl text-sm">
                              <div class="mb-4 flex items-center justify-between">
                                <span class="text-[15px] text-slate-800 font-semibold">{{
                                  t('scan.report.adapt-scheme')
                                }}</span>
                                <span
                                  class="text-xs text-white font-semibold px-2 py-0.5 inline-flex items-center rounded"
                                  :style="{
                                    background:
                                      adaptSchemeResult.selectedScheme === 'A'
                                        ? '#22c55e'
                                        : adaptSchemeResult.selectedScheme === 'B'
                                          ? '#3b82f6'
                                          : '#f59e0b',
                                  }"
                                >
                                  {{
                                    adaptSchemeResult.selectedScheme === 'A'
                                      ? t('scan.report.scheme-a-label')
                                      : adaptSchemeResult.selectedScheme === 'B'
                                        ? t('scan.report.scheme-b-label')
                                        : t('scan.report.scheme-c-label')
                                  }}
                                </span>
                              </div>
                              <div class="mb-4">
                                <div class="text-slate-500 font-medium mb-1">
                                  {{ t('scan.report.adapt-reasoning') }}
                                </div>
                                <div class="text-slate-700 leading-relaxed">
                                  {{ reasoningTw.displayed.value
                                  }}<span
                                    v-if="reasoningTw.isTyping.value"
                                    class="typewriter-cursor"
                                    >|</span
                                  >
                                </div>
                              </div>
                              <div class="mb-3">
                                <div
                                  v-if="summaryTw.displayed.value.length > 0"
                                  class="text-slate-500 font-medium mb-1"
                                >
                                  {{ t('scan.report.adapt-modification-summary') }}
                                </div>
                                <div class="text-slate-700 leading-relaxed">
                                  {{ summaryTw.displayed.value
                                  }}<span v-if="summaryTw.isTyping.value" class="typewriter-cursor"
                                    >|</span
                                  >
                                </div>
                              </div>
                              <!-- Scheme A: added file list -->
                              <div
                                v-if="
                                  adaptSchemeResult.selectedScheme === 'A' &&
                                  !reasoningTw.isTyping.value &&
                                  !summaryTw.isTyping.value
                                "
                                class="mb-3"
                              >
                                <div class="text-slate-500 font-medium mb-2">
                                  {{ t('scan.report.new-file-list') }}
                                </div>
                                <div class="flex flex-col gap-1">
                                  <button
                                    v-if="adaptSchemeResult.suggestedRiscvFilePath"
                                    class="group hover:border-blue-200 hover:bg-blue-50 active:bg-blue-100 px-3 py-2 text-left border-transparent flex gap-2 cursor-pointer transition-all duration-200 items-center border text-sm rounded hover:shadow-sm"
                                    @click="rightTab = 'code'"
                                  >
                                    <svg
                                      class="text-green-500 transition-transform duration-200 group-hover:scale-110"
                                      width="16"
                                      height="16"
                                      viewBox="0 0 24 24"
                                      fill="none"
                                      stroke="currentColor"
                                      stroke-width="2"
                                      stroke-linecap="round"
                                      stroke-linejoin="round"
                                    >
                                      <path
                                        d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"
                                      />
                                      <polyline points="14 2 14 8 20 8" />
                                      <line x1="12" y1="18" x2="12" y2="12" />
                                      <line x1="9" y1="15" x2="15" y2="15" />
                                    </svg>
                                    <span
                                      class="group-hover:text-blue-700 truncate transition-colors duration-200"
                                      >{{
                                        adaptSchemeResult.suggestedRiscvFilePath.split('/').pop()
                                      }}</span
                                    >
                                    <span
                                      class="group-hover:text-blue-500 text-xs text-slate-400 ml-auto shrink-0 transition-colors duration-200"
                                    >
                                      <template v-if="adaptIsRunning">
                                        <RenderIcon
                                          icon="i-ant-design:loading-outlined"
                                          class="size-3 inline animate-spin"
                                        />
                                        {{ t('scan.report.adapting-code-new-file') }}
                                      </template>
                                      <template v-else>{{
                                        t('scan.report.view-content')
                                      }}</template>
                                    </span>
                                    <svg
                                      class="group-hover:text-blue-500 text-slate-300 ml-1 transition-all duration-200 group-hover:translate-x-0.5"
                                      width="14"
                                      height="14"
                                      viewBox="0 0 24 24"
                                      fill="none"
                                      stroke="currentColor"
                                      stroke-width="2"
                                      stroke-linecap="round"
                                      stroke-linejoin="round"
                                    >
                                      <polyline points="9 18 15 12 9 6" />
                                    </svg>
                                  </button>
                                </div>
                              </div>
                            </div>
                          </template>
                        </div>
                        <!-- Right overlay for adaptation progress / empty state (only without an adaptation result) -->
                        <div
                          v-if="
                            selectedFilePath &&
                            hasFileIssues &&
                            !hasAdaptResult &&
                            !fileLoading &&
                            !hasAdaptScheme
                          "
                          class="adapt-empty-state select-none inset-0 absolute z-10"
                          style="background: #f9fbff"
                        >
                          <template v-if="adaptIsRunning">
                            <div
                              class="px-10 flex flex-col gap-2 h-full items-center justify-center"
                            >
                              <p
                                class="text-[11px] text-slate-400 tracking-wider font-medium mb-3 uppercase"
                              >
                                {{ t('scan.report.adapt-progress-title') }}
                              </p>

                              <!-- Phase 1: issue-level adaptation -->
                              <div
                                class="px-4 py-3.5 rounded-xl max-w-[280px] w-full transition-all duration-500 border"
                                :class="
                                  adaptPhase === 1
                                    ? 'border-amber-300 bg-amber-50 shadow-sm shadow-amber-100'
                                    : 'border-emerald-200 bg-emerald-50'
                                "
                              >
                                <div class="flex gap-3 items-start">
                                  <div
                                    class="mt-0.5 rounded-full flex shrink-0 h-6 w-6 items-center justify-center"
                                    :class="adaptPhase === 1 ? 'bg-amber-200' : 'bg-emerald-200'"
                                  >
                                    <RenderIcon
                                      v-if="adaptPhase === 1"
                                      icon="i-ant-design:loading-outlined"
                                      class="text-amber-600 size-3 animate-spin"
                                    />
                                    <RenderIcon
                                      v-else
                                      icon="i-ant-design:check-circle-outlined"
                                      class="text-emerald-600 size-3.5"
                                    />
                                  </div>
                                  <div class="flex-1 min-w-0">
                                    <div
                                      class="text-[13px] font-semibold"
                                      :class="
                                        adaptPhase === 1 ? 'text-amber-700' : 'text-emerald-700'
                                      "
                                    >
                                      {{
                                        adaptPhase === 1
                                          ? t('scan.report.adapt-phase-question')
                                          : t('scan.report.adapt-phase-question-done')
                                      }}
                                    </div>
                                    <template v-if="adaptPhase === 1 && adaptProgress.total > 0">
                                      <div class="mt-2 space-y-1.5">
                                        <div
                                          class="text-[11px] text-amber-600 flex justify-between"
                                        >
                                          <span>{{ t('scan.report.adapting-issues') }}</span>
                                          <span class="font-semibold tabular-nums"
                                            >{{ adaptProgress.done }} /
                                            {{ adaptProgress.total }}</span
                                          >
                                        </div>
                                        <div
                                          class="rounded-full bg-amber-200 h-1.5 overflow-hidden"
                                        >
                                          <div
                                            class="rounded-full bg-amber-500 h-full transition-all duration-300"
                                            :style="{
                                              width: `${(adaptProgress.done / adaptProgress.total) * 100}%`,
                                            }"
                                          />
                                        </div>
                                      </div>
                                    </template>
                                    <template v-else>
                                      <div class="text-[11px] text-emerald-600 font-medium mt-0.5">
                                        {{
                                          t('scan.report.adapt-issues-done', {
                                            count: adaptProgress.done,
                                          })
                                        }}
                                      </div>
                                    </template>
                                  </div>
                                </div>
                              </div>

                              <!-- Connector -->
                              <div class="py-0.5 flex flex-col gap-0.5 items-center">
                                <div
                                  v-for="i in 3"
                                  :key="i"
                                  class="rounded-full h-1 w-px transition-colors duration-700"
                                  :class="adaptPhase > 1 ? 'bg-blue-300' : 'bg-slate-200'"
                                />
                              </div>

                              <!-- Phase 2: file-level adaptation -->
                              <div
                                class="px-4 py-3.5 rounded-xl max-w-[280px] w-full transition-all duration-500 border"
                                :class="
                                  adaptPhase === 2
                                    ? 'border-blue-300 bg-blue-50 shadow-sm shadow-blue-100'
                                    : 'border-slate-200 bg-slate-50 opacity-50'
                                "
                              >
                                <div class="flex gap-3 items-start">
                                  <div
                                    class="mt-0.5 rounded-full flex shrink-0 h-6 w-6 items-center justify-center"
                                    :class="adaptPhase === 2 ? 'bg-blue-200' : 'bg-slate-200'"
                                  >
                                    <RenderIcon
                                      v-if="adaptPhase === 2"
                                      icon="i-ant-design:loading-outlined"
                                      class="text-blue-600 size-3 animate-spin"
                                    />
                                    <div v-else class="rounded-full bg-slate-400 h-2 w-2" />
                                  </div>
                                  <div class="flex-1">
                                    <div
                                      class="text-[13px] font-semibold"
                                      :class="adaptPhase === 2 ? 'text-blue-700' : 'text-slate-500'"
                                    >
                                      {{
                                        adaptPhase === 2
                                          ? t('scan.report.adapt-phase-file')
                                          : t('scan.report.adapt-phase-file-short')
                                      }}
                                    </div>
                                    <div
                                      class="text-[11px] mt-0.5"
                                      :class="adaptPhase === 2 ? 'text-blue-500' : 'text-slate-400'"
                                    >
                                      <template v-if="adaptPhase === 2">{{
                                        t('scan.report.adapting-file')
                                      }}</template>
                                      <template v-else>{{
                                        t('scan.report.waiting-phase1')
                                      }}</template>
                                    </div>
                                  </div>
                                </div>
                              </div>
                            </div>
                          </template>
                          <template v-else-if="adaptIsQueued">
                            <div
                              class="px-8 flex flex-col gap-5 h-full items-center justify-center"
                            >
                              <div
                                class="text-amber-400 border-slate-200 rounded-2xl bg-slate-100 flex h-12 w-12 items-center justify-center border"
                              >
                                <RenderIcon
                                  icon="i-ant-design:clock-circle-outlined"
                                  class="size-6 animate-pulse"
                                />
                              </div>
                              <div class="text-center space-y-1">
                                <p class="text-[14px] text-slate-700 font-semibold">
                                  {{ t('scan.report.adapt-status-ready') }}
                                </p>
                                <p class="text-[12px] text-slate-400">
                                  {{ t('scan.report.adapt-queued-hint') }}
                                </p>
                              </div>
                            </div>
                          </template>
                          <template v-else>
                            <div
                              class="px-8 flex flex-col gap-5 h-full items-center justify-center"
                            >
                              <div
                                class="text-slate-400 border-slate-200 rounded-2xl bg-slate-100 flex h-12 w-12 items-center justify-center border"
                              >
                                <RenderIcon
                                  icon="i-ant-design:clock-circle-outlined"
                                  class="size-5.5"
                                />
                              </div>
                              <div class="text-center space-y-1">
                                <p class="text-[14px] text-slate-700 font-semibold">
                                  {{ t('scan.report.adapt-empty-hint') }}
                                </p>
                                <p class="text-[12px] text-slate-400">
                                  {{ t('scan.report.adapt-desc') }}
                                </p>
                              </div>
                              <button
                                :disabled="adaptingFile"
                                class="adapt-action-btn text-[13px] text-white font-medium px-5 py-2 flex gap-2 transition-colors items-center rounded-lg disabled:opacity-50 disabled:cursor-not-allowed"
                                @click="handleAdaptFile"
                              >
                                <RenderIcon
                                  v-if="adaptingFile"
                                  icon="i-ant-design:loading-outlined"
                                  class="text-white size-3.5 animate-spin"
                                />
                                <RenderIcon
                                  v-else
                                  icon="i-lucide:zap"
                                  class="text-white size-3.5"
                                />
                                {{ t('scan.report.ai-adapt') }}
                              </button>
                            </div>
                          </template>
                        </div>
                      </div>
                    </div>
                  </div>
                </div>
              </div>
            </div>
          </LightCard>
        </template>
      </LightTabs>
    </ASpin>
  </div>
</template>

<style scope>
@import '@/styles/atlas.css';
</style>

<style lang="scss" scoped>
.report-nav-left-item {
  display: block;
  padding: 8px 12px;
  margin-bottom: 2px;
  font-size: 14px;
  font-weight: 500;
  color: #475569;
  text-decoration: none;
  cursor: pointer;
  border-radius: 6px;
  transition: all 0.2s;
}

.report-loading {
  display: flex;
  flex-direction: column;
  gap: 12px;
  align-items: center;
  justify-content: center;
  padding: 80px 0;
  font-size: 14px;
  color: #8a94a6;
}

.report-sub-title {
  margin: 20px 0 10px;
  font-size: 14px;
  font-weight: 600;
  color: #1a1a2e;
}

.report-stat-percent {
  margin-left: 4px;
  font-size: 12px;
  font-weight: normal;
  color: #8a94a6;
}

/* Label width of the project info description list */
.report-project-info {
  display: inline-block;
  width: auto;
}
.report-project-info :deep(.ant-descriptions-item-label) {
  width: 100px !important;
  min-width: 100px !important;
  white-space: nowrap;
}
.report-nav-left-item--active {
  font-weight: 600;
  color: #3578ff;
  background: #eef4ff;
}
.report-nav-left-item:hover {
  color: #3578ff;
  background: #eef4ff;
}

.report-content {
  flex: 1;
  min-width: 0;
}

.report-section {
  margin-bottom: 28px;
}

.report-section-title {
  padding-bottom: 8px;
  margin: 0 0 12px;
  font-size: 16px;
  font-weight: 600;
  color: #2e2e2e;
  border-bottom: 1px solid #f0f2f5;
}

.report-stats-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(140px, 1fr));
  gap: 12px;
}

.report-stat-card {
  padding: 16px;
  text-align: center;
  background: #f9fbff;
  border: 1px solid #e8edf5;
  border-radius: 8px;
}

.report-stat-value {
  font-size: 24px;
  font-weight: 700;
  line-height: 1.2;
  color: #3578ff;
}

.report-stat-label {
  margin-top: 4px;
  font-size: 13px;
  color: #7182a8;
}

.report-text {
  margin: 0 0 10px;
  font-size: 14px;
  line-height: 1.8;
  color: #3f4b66;
  text-align: justify;
}

.report-distribution-list {
  padding-left: 20px;
  margin: 0;
  list-style: none;
}

.report-distribution-item {
  margin-bottom: 4px;
  font-size: 14px;
  line-height: 1.8;
  color: #3f4b66;
}

.report-declaration-list {
  padding-left: 20px;
  margin: 0;
}

.report-declaration-item {
  margin-bottom: 8px;
  font-size: 14px;
  line-height: 1.8;
  color: #3f4b66;
  text-align: justify;
}

/* Adapt action button */
.adapt-action-btn {
  cursor: pointer;
  background: #3578ff;
  box-shadow: 0 1px 3px 0 rgba(53, 120, 255, 0.3);

  &:hover {
    background: #2962dd;
  }
}
</style>

<!-- Monaco file-question annotations (global styles — unified red palette) -->
<style>
/* --- Line highlights --- */
.fq-error-line {
  background: rgba(220, 53, 69, 0.12) !important;
}
.fq-error-glyph {
  width: 8px !important;
  height: 8px !important;
  margin: 6px 0 0 4px;
  background: #dc3545;
  border-radius: 50%;
}

/* --- Squiggly underlines --- */
.fq-error-squiggle {
  padding-bottom: 2px;
  background-image: url("data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' width='10' height='6'%3E%3Cpath d='M0 3 Q2.5 0 5 3 Q7.5 6 10 3' stroke='%23dc3545' fill='none' stroke-width='1.5'/%3E%3C/svg%3E");
  background-repeat: repeat-x;
  background-position: 0 100%;
}

/* --- View Zone cards (unified red palette, matching the inline assembly style) --- */
.fq-zone {
  display: flex;
  flex-direction: column;
  gap: 4px;
  max-height: 112px;
  padding: 4px 12px;
  margin: 2px 0;
  overflow-y: auto;
  font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
  font-size: 12px;
  line-height: 1.5;
  background: #fef2f2;
  border-left: 3px solid #ef4444;
  border-radius: 4px;
}
.fq-zone-header {
  display: flex;
  gap: 8px;
  align-items: center;
}
.fq-zone-badge {
  display: inline-block;
  flex-shrink: 0;
  padding: 0 8px;
  font-size: 11px;
  font-weight: 600;
  line-height: 20px;
  color: #fff;
  border-radius: 10px;
}
.fq-zone-rule {
  padding: 2px 0;
  font-size: 12px;
  font-weight: 500;
  color: #64748b;
}
.fq-zone-lines {
  margin-left: auto;
  font-family: 'SF Mono', 'Fira Code', 'Fira Mono', Menlo, Consolas, monospace;
  font-size: 11px;
  color: #94a3b8;
}
.fq-zone-text {
  padding: 4px 8px;
  margin-top: 4px;
  font-family: 'SF Mono', 'Fira Code', 'Fira Mono', Menlo, Consolas, monospace;
  font-size: 11.5px;
  color: #334155;
  word-break: break-all;
  white-space: pre-wrap;
  background: #f1f5f9;
  border-radius: 4px;
}
/* (type variants are merged into the default .fq-zone styles) */

/* File tree: nodes with questions turn red */
.tree-icon {
  display: inline-block;
}
.tree-icon--warn {
  color: #ef4444;
}

/* ===== Typewriter caret blinking ===== */
.typewriter-cursor {
  font-weight: 300;
  color: #3b82f6;
  animation: typewriter-blink 0.8s step-end infinite;
}
@keyframes typewriter-blink {
  0%,
  100% {
    opacity: 1;
  }
  50% {
    opacity: 0;
  }
}

/* ===== Dark Mode ===== */
.dark {
  /* --- Report left nav --- */
  .report-content + nav,
  nav:has(.report-nav-left-item) {
    border-right-color: #2a3050 !important;
  }
  nav:has(.report-nav-left-item) > div:first-child {
    color: #e8edf5 !important;
    border-bottom-color: #2a3050 !important;
  }

  /* --- Report title "RISC-V porting detection report" --- */
  .report-content h2 {
    color: #e8edf5 !important;
  }

  /* --- Report section dividers (inline border-bottom) --- */
  .report-content [style*='border-bottom'] {
    border-bottom-color: #2a3050 !important;
  }

  /* --- UnoCSS text utilities in report --- */
  .report-content .text-slate-700 {
    color: #cbd5e1;
  }
  .report-content .text-slate-400 {
    color: #94a3b8;
  }

  /* --- Report navigation --- */
  .report-nav-left-item {
    color: #a0adde;

    &:hover,
    &.report-nav-left-item--active {
      color: #4d8aff;
      background: rgba(53, 120, 255, 0.12);
    }
  }

  /* --- Report section title --- */
  .report-section-title {
    color: #e8edf5;
    border-bottom-color: #2a3050;
  }

  /* --- Report stat card --- */
  .report-stat-card {
    background: #1a1f2e;
    border-color: #2a3050;
  }
  .report-stat-value {
    color: #60a5fa;
  }
  .report-stat-label {
    color: #a0adde;
  }

  /* --- Monaco zone cards (fq-zone) — keep the red tone in dark mode --- */
  .fq-zone {
    background: rgba(239, 68, 68, 0.1);
    border-left-color: #f87171;
  }
  .fq-zone-rule {
    color: #fca5a5;
  }
  .fq-zone-lines {
    color: #f87171;
  }
  .fq-zone-text {
    color: #e8edf5;
    background: rgba(239, 68, 68, 0.12);
  }

  /* --- File Content tab — borders --- */
  .fc-left-panel {
    border-right-color: #2a3050 !important;
  }
  .fc-header,
  .fc-toolbar {
    border-bottom-color: #2a3050 !important;
  }
  .fc-stats {
    border-top-color: #2a3050 !important;
  }
  /* UnoCSS text colors */
  .scan-report-content .text-slate-700 {
    color: #e8edf5;
  }
  .scan-report-content .text-slate-800 {
    color: #f1f5f9;
  }
  .scan-report-content .text-slate-600 {
    color: #cbd5e1;
  }
  .scan-report-content .text-slate-500 {
    color: #94a3b8;
  }
  .scan-report-content .text-slate-400 {
    color: #6b7a9e;
  }
  .scan-report-content .text-slate-300 {
    color: #475569;
  }
  .scan-report-content .text-blue-500 {
    color: #60a5fa;
  }
  .scan-report-content .text-amber-500 {
    color: #fbbf24;
  }
  .scan-report-content .text-red-500 {
    color: #f87171;
  }
  /* Empty state overlay (empty state of the Monaco editor) */
  .file-empty-state {
    background: #1a1f2e !important;
  }
  .file-empty-state .text-base,
  .file-empty-state .text-sm {
    color: #a0adde;
  }
  .file-empty-state svg {
    color: #475569;
  }

  /* Adapt empty state */
  .adapt-empty-state {
    background: #1a1f2e !important;

    .text-slate-700 {
      color: #e8edf5;
    }
    .text-slate-400 {
      color: #94a3b8;
    }
    .bg-slate-100 {
      background-color: #2a3050 !important;
    }
    .border-slate-200 {
      border-color: #3a4060 !important;
    }
  }
}
</style>
