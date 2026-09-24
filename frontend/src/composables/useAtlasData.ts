import type { EChartsOption } from 'echarts'
import type { MaybeRefOrGetter, Ref } from 'vue'
import type { BTQuestionInfo } from '@/types/llm'
import type {
  AsmFileInfo,
  FileInfoCategory,
  FrameworkPortraitData,
  LibListEntry,
} from '@/types/scan'
import { computed, toValue } from 'vue'

export interface AtlasDataParams {
  /**
   * Raw file-info payload: an object map or the JSON string the backend returns.
   * Declared as one MaybeRefOrGetter so `toValue()` keeps a usable type.
   */
  fileInfo: MaybeRefOrGetter<Record<string, FileInfoCategory> | string | null>
  frameworkPortrait: Ref<FrameworkPortraitData | null>
  libList: Ref<LibListEntry[]>
  asmList: Ref<AsmFileInfo | null>
  makeFileList: Ref<BTQuestionInfo[]>
}

export type VisualTone = 'high' | 'medium' | 'low' | 'neutral'

interface DepLib {
  libName: string
  libCategory: string
  supportRiscv: string
}

interface DepLibGroup {
  category: string
  libs: DepLib[]
}

function normalizeFileInfo(raw: unknown): Record<string, FileInfoCategory> | null {
  if (!raw) return null
  if (typeof raw === 'string') {
    try {
      const parsed = JSON.parse(raw)
      return parsed as Record<string, FileInfoCategory>
    } catch {
      return null
    }
  }
  return raw as Record<string, FileInfoCategory>
}

export function compactText(value: string, limit = 18): string {
  const normalizedValue = value.trim()
  if (normalizedValue.length <= limit) return normalizedValue
  return `${normalizedValue.slice(0, Math.max(0, limit - 1))}\u2026`
}

export function nodeToneColor(tone: VisualTone): string {
  if (tone === 'high') return '#ef4444'
  if (tone === 'medium') return '#f59e0b'
  if (tone === 'low') return '#22c55e'
  return '#64748b'
}

export const MAINBOARD_PIE_COLORS = [
  '#ef4444',
  '#f59e0b',
  '#38bdf8',
  '#6366f1',
  '#22c55e',
  '#94a3b8',
] as const

export function useAtlasData(params: AtlasDataParams) {
  const { t } = useI18n()

  const parsedFileInfo = computed<Record<string, FileInfoCategory> | null>(() => {
    return normalizeFileInfo(toValue(params.fileInfo))
  })

  // ========== Dependency Categories ==========
  const DEPENDENCY_CATEGORIES = computed(
    () =>
      [
        { key: 'standard', label: t('scan.atlas.standard-lib') },
        { key: 'system', label: t('scan.atlas.system-lib') },
        { key: 'third_party', label: t('scan.atlas.third-party-lib') },
      ] as const,
  )

  // ========== Source Language Summary ==========
  const sourceLanguageSummary = computed(() => {
    const fileInfo = parsedFileInfo.value
    if (!fileInfo) return []

    const categoryMeta: Record<string, string> = {
      C_SOURCE: 'c',
      C_HEADER: 'c',
      CPP_SOURCE: 'cpp',
      CPP_HEADER: 'cpp',
      ASSEMBLY: 'assembly',
      SCRIPT: 'script',
      DOCUMENT: 'document',
      CONFIG: 'config',
      BUILD: 'build',
      OTHER: 'other',
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

    const merged = new Map<string, { fileCount: number; lineCount: number }>()
    for (const [key, cat] of Object.entries(fileInfo)) {
      const c = cat
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

    return Array.from(merged.entries(), ([key, data]) => ({
      key,
      label: categoryLabels[key] || key,
      fileCount: data.fileCount,
      lineCount: data.lineCount,
    }))
  })

  // ========== Source Overview ==========
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

  const atlasSourceTypeCount = computed(() => {
    return sourceLanguageSummary.value.filter(
      (item) =>
        (item.fileCount > 0 || item.lineCount > 0) &&
        !['other', 'document', 'config', 'build'].includes(item.key),
    ).length
  })

  const atlasSourceTotalFileCount = computed(() => {
    return sourceLanguageSummary.value
      .filter((item) => !['other', 'document', 'config', 'build'].includes(item.key))
      .reduce((sum, item) => sum + item.fileCount, 0)
  })

  const atlasSourceTotalLineCount = computed(() => {
    return sourceLanguageSummary.value
      .filter((item) => !['other', 'document', 'config', 'build'].includes(item.key))
      .reduce((sum, item) => sum + item.lineCount, 0)
  })

  const atlasSourceTotalLineText = computed(() => {
    return atlasSourceTotalLineCount.value.toLocaleString()
  })

  const atlasSourceTotalLineStyle = computed(() => {
    const textLength = atlasSourceTotalLineText.value.length
    const fontSize = Math.max(9, 26 - Math.max(0, textLength - 5) * 2)
    const letterSpacing = fontSize <= 11 ? '0em' : fontSize <= 14 ? '-0.01em' : '-0.02em'
    return {
      fontSize: `${fontSize}px`,
      letterSpacing,
      // line-height must fit font descenders (e.g. comma tails); otherwise overflow:hidden cuts them off and a comma looks like a dot
      lineHeight: '1.3',
    }
  })

  const atlasSourceLanguageRows = computed(() => {
    const rows = sourceLanguageSummary.value
      .filter(
        (item) =>
          (item.fileCount > 0 || item.lineCount > 0) &&
          !['other', 'document', 'config', 'build'].includes(item.key),
      )
      .sort((left, right) => {
        return (
          right.fileCount - left.fileCount ||
          right.lineCount - left.lineCount ||
          left.label.localeCompare(right.label, 'zh-Hans-CN')
        )
      })

    const maxFileCount = Math.max(1, ...rows.map((item) => item.fileCount))

    return rows.slice(0, 4).map((item) => ({
      key: item.label,
      label: item.label,
      fileCount: item.fileCount,
      lineCount: item.lineCount,
      widthPercent: Math.max(16, Math.round((item.fileCount / maxFileCount) * 100)),
    }))
  })

  const atlasSourceHiddenTypeCount = computed(() => {
    return Math.max(0, atlasSourceTypeCount.value - atlasSourceLanguageRows.value.length)
  })

  const atlasSourceLanguageText = computed(() => {
    const labels = sourceLanguageSummary.value
      .filter((item) => !['other', 'document', 'config', 'build'].includes(item.key))
      .map((item) => item.label)
    return labels.length > 0 ? compactText(labels.join(' / '), 28) : t('scan.atlas.unrecognized')
  })

  const atlasSourceBuildSystemsText = computed(() => {
    const list = params.makeFileList.value
    if (list.length === 0) return t('scan.atlas.unrecognized')
    const patterns = [...new Set(list.map((item) => item.questionType).filter(Boolean))]
    return patterns.length > 0 ? patterns.join(' / ') : t('scan.atlas.unrecognized')
  })

  // ========== Assembly ==========
  const atlasAssemblyFileCount = computed(() => {
    if (params.asmList.value) return params.asmList.value.asmInfo.asmFile
    const fp = params.frameworkPortrait.value
    if (fp) return fp.fileTypeStats.ASSEMBLY
    const fileInfo = parsedFileInfo.value
    if (fileInfo) {
      const c = fileInfo.ASSEMBLY
      return c ? (c.fileCount ?? 0) : 0
    }
    return 0
  })

  const atlasInlineAsmCount = computed(() => {
    if (params.asmList.value) return params.asmList.value.asmInfo.inlineAsm
    const fp = params.frameworkPortrait.value
    if (fp) return fp.questionType.ASM
    return 0
  })

  const atlasAssemblyTotalCount = computed(() => {
    if (params.asmList.value) return params.asmList.value.asmInfo.total
    return atlasAssemblyFileCount.value + atlasInlineAsmCount.value
  })

  const atlasAssemblyNotice = computed<null | {
    title: string
    detail: string
    tone: 'neutral' | 'info'
  }>(() => {
    if (atlasAssemblyFileCount.value === 0 && atlasInlineAsmCount.value === 0) {
      return {
        title: t('scan.atlas.no-asm-detected'),
        detail: t('scan.atlas.no-asm-detail'),
        tone: 'neutral',
      }
    }
    return null
  })

  const atlasAssemblyArchitectureRows = computed(() => {
    const asm = params.asmList.value
    if (atlasAssemblyTotalCount.value === 0) return []

    const codeCount = asm?.instructionSetCodeCount
    if (codeCount) {
      const rows = Object.entries(codeCount)
        .map(([label, total]) => ({ key: label, label, total }))
        .sort(
          (left, right) =>
            right.total - left.total || left.label.localeCompare(right.label, 'zh-Hans-CN'),
        )

      if (rows.length === 0) {
        return [
          {
            key: 'unknown',
            label: 'unknown',
            total: atlasAssemblyTotalCount.value,
            widthPercent: 100,
          },
        ]
      }

      const maxTotal = Math.max(1, ...rows.map((item) => item.total))
      return rows.slice(0, 5).map((item) => ({
        ...item,
        widthPercent: Math.max(12, Math.round((item.total / maxTotal) * 100)),
      }))
    }

    return [
      { key: 'unknown', label: 'unknown', total: atlasAssemblyTotalCount.value, widthPercent: 100 },
    ]
  })

  const atlasAssemblyArchitectureCount = computed(() => atlasAssemblyArchitectureRows.value.length)

  // ========== Mainboard (Architecture Profile) ==========
  const atlasMainboardStats = computed(() => {
    const fp = params.frameworkPortrait.value
    const cSource = (fp?.fileTypeStats.C_SOURCE ?? 0) + (fp?.fileTypeStats.C_HEADER ?? 0)
    const cppSource = (fp?.fileTypeStats.CPP_SOURCE ?? 0) + (fp?.fileTypeStats.CPP_HEADER ?? 0)
    const assembly = fp?.fileTypeStats.ASSEMBLY ?? 0
    const build = fp?.fileTypeStats.BUILD ?? 0

    return [
      {
        key: 'cc-source-files',
        label: t('scan.atlas.source-files-label'),
        value: cSource + cppSource,
        tone: (cSource + cppSource > 0 ? 'medium' : 'neutral') as VisualTone,
      },
      {
        key: 'assembly-files',
        label: t('scan.atlas.asm-files-label'),
        value: assembly,
        tone: (assembly > 0 ? 'medium' : 'neutral') as VisualTone,
      },
      {
        key: 'build-config-files',
        label: t('scan.atlas.build-config-label'),
        value: build,
        tone: (build > 0 ? 'medium' : 'neutral') as VisualTone,
      },
    ] as Array<{ key: string; label: string; value: number; tone: VisualTone }>
  })

  const atlasMainboardPieRows = computed(() => {
    const fp = params.frameworkPortrait.value
    if (!fp) return []
    const items = [
      {
        key: 'arch-header-files',
        label: t('scan.atlas.arch-header-files'),
        count: fp.questionType.INCLUDE,
        color: MAINBOARD_PIE_COLORS[0],
      },
      {
        key: 'arch-macros',
        label: t('scan.atlas.arch-macros'),
        count: fp.questionType.MACRO,
        color: MAINBOARD_PIE_COLORS[1],
      },
      {
        key: 'inline-asm',
        label: t('scan.atlas.inline-asm-label'),
        count: fp.questionType.ASM,
        color: MAINBOARD_PIE_COLORS[2],
      },
    ].filter((item) => item.count > 0)

    if (items.length === 0) return []

    const total = Math.max(
      1,
      items.reduce((sum, item) => sum + item.count, 0),
    )

    return items.map((item) => ({
      ...item,
      percent: Math.round((item.count / total) * 100),
    }))
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
          return `${item?.name ?? '-'}<br/>${t('scan.atlas.count-format', { value, percent })}`
        },
        backgroundColor: 'rgba(15, 23, 42, 0.92)',
        borderWidth: 0,
        textStyle: { color: '#f8fafc', fontSize: 12 },
      },
      series: [
        {
          type: 'pie',
          radius: ['58%', '82%'],
          center: ['50%', '50%'],
          startAngle: 90,
          clockwise: true,
          avoidLabelOverlap: true,
          itemStyle: { borderColor: 'rgba(255,255,255,0.96)', borderWidth: 2 },
          label: { show: false },
          labelLine: { show: false },
          emphasis: { scale: false },
          data: hasData
            ? rows.map((item) => ({
                value: item.count,
                name: item.label,
                itemStyle: { color: item.color },
              }))
            : [
                {
                  value: 1,
                  name: t('scan.atlas.no-data'),
                  itemStyle: { color: '#e2e8f0' },
                },
              ],
        },
      ],
    }
  })

  // ========== Dependencies ==========
  const atlasDepTotalCount = computed(() => {
    const seen = new Set<string>()
    for (const item of params.libList.value) {
      if (item.libName && !seen.has(item.libName)) seen.add(item.libName)
    }
    return seen.size
  })

  const atlasDepSupportedCount = computed(() => {
    let count = 0
    const seen = new Set<string>()
    for (const item of params.libList.value) {
      const libName = item.libName
      if (!libName || seen.has(libName)) continue
      seen.add(libName)
      if (item.supportRiscv === '1') count++
    }
    return count
  })

  const atlasDepUnsupportedCount = computed(() => {
    let count = 0
    const seen = new Set<string>()
    for (const item of params.libList.value) {
      const libName = item.libName
      if (!libName || seen.has(libName)) continue
      seen.add(libName)
      if (item.supportRiscv !== '1') count++
    }
    return count
  })

  const atlasDependencyNotice = computed<null | {
    title: string
    detail: string
    tone: 'neutral' | 'info'
  }>(() => {
    if (atlasDepTotalCount.value === 0) {
      return {
        title: t('scan.atlas.no-deps-detected'),
        detail: t('scan.atlas.no-deps-detail'),
        tone: 'neutral',
      }
    }
    return null
  })

  // ========== Build System ==========
  const atlasBuildAnalyzedFiles = computed(() => {
    const list = params.makeFileList.value
    if (list.length === 0) {
      const fp = params.frameworkPortrait.value
      return fp ? fp.fileTypeStats.BUILD : 0
    }
    const files = new Set(list.map((item) => item.filePath))
    return files.size
  })

  const atlasBuildPatternCount = computed(() => {
    const list = params.makeFileList.value
    if (list.length === 0) return 0
    return new Set(list.map((item) => item.questionType).filter(Boolean)).size
  })

  const atlasBuildArchCount = computed(() => params.makeFileList.value.length)

  const atlasBuildArchRows = computed<
    Array<{ key: string; label: string; count: number; widthPercent: number }>
  >(() => {
    const list = params.makeFileList.value
    if (list.length === 0) return []
    const map = new Map<string, number>()
    for (const item of list) {
      const arch = item.framework || item.questionType || 'unknown'
      map.set(arch, (map.get(arch) ?? 0) + 1)
    }
    const rows = Array.from(map, ([label, count]) => ({ key: label, label, count }))
    const max = Math.max(1, ...rows.map((r) => r.count))
    return rows
      .sort((a, b) => b.count - a.count)
      .map((r) => ({ ...r, widthPercent: Math.round((r.count / max) * 100) }))
  })

  const atlasBuildNotice = computed<null | {
    title: string
    detail: string
    tone: 'neutral' | 'info'
  }>(() => {
    if (atlasBuildAnalyzedFiles.value === 0) {
      return {
        title: t('scan.atlas.no-build-detected'),
        detail: t('scan.atlas.no-build-detail'),
        tone: 'neutral',
      }
    }
    return null
  })

  // ========== Dependency Groups ==========
  const depLibGroups = computed<DepLibGroup[]>(() => {
    const seen = new Set<string>()
    const groupMap = new Map<string, DepLib[]>()

    for (const cat of DEPENDENCY_CATEGORIES.value) {
      groupMap.set(cat.key, [])
    }

    for (const item of params.libList.value) {
      const libName = item.libName
      const libCategory = item.libCategory || ''
      if (!libName || seen.has(libName)) continue
      seen.add(libName)
      const lib: DepLib = {
        libName,
        libCategory,
        supportRiscv: item.supportRiscv || '',
      }
      const matched = DEPENDENCY_CATEGORIES.value.find((c) => c.key === libCategory)
      const targetCat = matched ? matched.key : 'third_party'
      if (targetCat === 'system') {
        groupMap.get('standard')?.push(lib)
      } else {
        if (!groupMap.has(targetCat)) {
          groupMap.set(targetCat, [])
        }
        const group = groupMap.get(targetCat)
        if (group) {
          group.push(lib)
        }
      }
    }

    return DEPENDENCY_CATEGORIES.value
      .filter((cat) => cat.key !== 'system')
      .map((cat) => ({ category: cat.label, libs: groupMap.get(cat.key) || [] }))
  })

  return {
    DEPENDENCY_CATEGORIES,
    sourceLanguageSummary,
    atlasMainLanguageLabel,
    atlasSourceTypeCount,
    atlasSourceTotalFileCount,
    atlasSourceTotalLineCount,
    atlasSourceTotalLineText,
    atlasSourceTotalLineStyle,
    atlasSourceLanguageRows,
    atlasSourceHiddenTypeCount,
    atlasSourceLanguageText,
    atlasSourceBuildSystemsText,
    atlasAssemblyFileCount,
    atlasInlineAsmCount,
    atlasAssemblyTotalCount,
    atlasAssemblyNotice,
    atlasAssemblyArchitectureRows,
    atlasAssemblyArchitectureCount,
    atlasMainboardStats,
    atlasMainboardPieRows,
    atlasMainboardPieOption,
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
  }
}
