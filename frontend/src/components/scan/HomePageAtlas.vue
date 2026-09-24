<script setup lang="ts">
import { onMounted, ref, watch } from 'vue'
import {
  getAsmList,
  getFrameworkPortrait,
  getLibList,
  getMakeFileInfo,
  parseFileInfo,
} from '@/api/scan'
import { useAtlasData } from '@/composables/useAtlasData'
import AtlasPanel from './AtlasPanel.vue'

const props = defineProps<{
  taskId: string
  fileInfo: string | null
}>()

const emit = defineEmits<{
  loaded: []
}>()

const parsedFileInfo = computed(() => parseFileInfo(props.fileInfo))
const frameworkPortrait = ref<import('@/types/scan').FrameworkPortraitData | null>(null)
const libList = ref<import('@/types/scan').LibListEntry[]>([])
const asmList = ref<import('@/types/scan').AsmFileInfo | null>(null)
const makeFileList = ref<import('@/types/llm').BTQuestionInfo[]>([])

const atlas = useAtlasData({
  fileInfo: computed(
    () => parsedFileInfo.value as Record<string, import('@/types/scan').FileInfoCategory> | null,
  ),
  frameworkPortrait,
  libList,
  asmList,
  makeFileList,
})

async function loadAtlasData() {
  if (!props.taskId) return
  frameworkPortrait.value = null
  libList.value = []
  asmList.value = null
  makeFileList.value = []
  try {
    const [portrait, libs, asms, makeFiles] = await Promise.all([
      getFrameworkPortrait(props.taskId).catch(() => null),
      getLibList(props.taskId).catch(() => []),
      getAsmList(props.taskId).catch(() => null),
      getMakeFileInfo(props.taskId).catch(() => []),
    ])
    frameworkPortrait.value = portrait
    libList.value = Array.isArray(libs) ? libs : []
    asmList.value = asms
    makeFileList.value = makeFiles
    emit('loaded')
  } catch {
    // silent
  }
}

onMounted(() => loadAtlasData())
watch(
  () => props.taskId,
  () => loadAtlasData(),
)
</script>

<template>
  <AtlasPanel
    :atlas-source-language-rows="atlas.atlasSourceLanguageRows.value"
    :atlas-source-hidden-type-count="atlas.atlasSourceHiddenTypeCount.value"
    :atlas-source-total-line-text="atlas.atlasSourceTotalLineText.value"
    :atlas-source-total-line-style="atlas.atlasSourceTotalLineStyle.value"
    :atlas-source-total-file-count="atlas.atlasSourceTotalFileCount.value"
    :atlas-main-language-label="atlas.atlasMainLanguageLabel.value"
    :atlas-source-language-text="atlas.atlasSourceLanguageText.value"
    :atlas-source-build-systems-text="atlas.atlasSourceBuildSystemsText.value"
    :atlas-assembly-total-count="atlas.atlasAssemblyTotalCount.value"
    :atlas-assembly-file-count="atlas.atlasAssemblyFileCount.value"
    :atlas-inline-asm-count="atlas.atlasInlineAsmCount.value"
    :atlas-assembly-notice="atlas.atlasAssemblyNotice.value"
    :atlas-assembly-architecture-rows="atlas.atlasAssemblyArchitectureRows.value"
    :atlas-mainboard-stats="atlas.atlasMainboardStats.value"
    :atlas-mainboard-pie-rows="atlas.atlasMainboardPieRows.value"
    :atlas-mainboard-pie-option="atlas.atlasMainboardPieOption.value"
    :atlas-dep-total-count="atlas.atlasDepTotalCount.value"
    :atlas-dep-supported-count="atlas.atlasDepSupportedCount.value"
    :atlas-dep-unsupported-count="atlas.atlasDepUnsupportedCount.value"
    :atlas-dependency-notice="atlas.atlasDependencyNotice.value"
    :atlas-build-analyzed-files="atlas.atlasBuildAnalyzedFiles.value"
    :atlas-build-pattern-count="atlas.atlasBuildPatternCount.value"
    :atlas-build-arch-count="atlas.atlasBuildArchCount.value"
    :atlas-build-arch-rows="atlas.atlasBuildArchRows.value"
    :atlas-build-notice="atlas.atlasBuildNotice.value"
    :dep-lib-groups="atlas.depLibGroups.value"
  />
</template>
