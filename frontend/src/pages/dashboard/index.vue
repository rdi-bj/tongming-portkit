<script setup lang="ts">
import type { BTAppInfo, CallEdge } from '@/types/scan'
import { computed, onMounted, ref } from 'vue'
import { getAppInfoPage, getLibList } from '@/api/scan'
import GraphCanvas from '@/components/GraphCanvas.vue'
import HomePageAtlas from '@/components/scan/HomePageAtlas.vue'

definePage({
  name: 'DashboardHome',
  meta: {
    title: 'routes.dashboard.index',
    menu: true,
    menuSort: 1,
    icon: 'ant-design:home-outlined',
  },
})

const { t } = useI18n()
const allProjects = ref<BTAppInfo[]>([])
const selectedProject = ref<BTAppInfo | null>(null)
const selectedId = ref<string | undefined>(undefined)
const loading = ref(false)

// --- Dependency library analysis modal ---
const depsModalVisible = ref(false)
const depsEdges = ref<CallEdge[] | null>(null)
const depsLoading = ref(false)

/** Normalize lib category: merge standard+system into one group */
function normalizeCategory(cat: string): string {
  if (cat === 'standard' || cat === 'system') return 'standard-system'
  return cat || 'unknown'
}

/** Convert LibListEntry[] to CallEdge[] so GraphCanvas can render it as a dependency graph */
function depsToEdges(
  list: { libName: string; libCategory: string; supportRiscv: string }[],
): CallEdge[] {
  const projectName = selectedProject.value?.name || 'Project'
  return list.map((lib, i) => {
    const category = normalizeCategory(lib.libCategory)
    return {
      caller: { definedInFile: null, functionName: projectName },
      callee: { definedInFile: category, functionName: lib.libName },
      file: category,
      line: i + 1,
      indirect: false,
      unresolved: lib.supportRiscv !== '1',
    }
  })
}

async function openDepsModal() {
  if (!selectedProject.value?.taskId) return
  depsModalVisible.value = true
  depsEdges.value = null
  depsLoading.value = true
  try {
    const data = await getLibList(selectedProject.value.taskId)
    depsEdges.value = depsToEdges(data)
  } catch (e) {
    console.error('Failed to load dependency list:', e)
  } finally {
    depsLoading.value = false
  }
}

// --- Stats ---
const stats = computed(() => {
  const total = allProjects.value.length
  const scanned = allProjects.value.filter((p) => p.taskId).length
  const notScanned = total - scanned
  return { total, scanned, notScanned }
})

const selectOptions = computed(() =>
  [...allProjects.value]
    .sort((a, b) => {
      const ta = a.updateTime || a.createTime || ''
      const tb = b.updateTime || b.createTime || ''
      return tb.localeCompare(ta)
    })
    .map((p) => ({
      value: p.id,
      label: p.name + (p.description ? ` - ${p.description}` : ''),
      disabled: !p.taskId,
    })),
)

async function loadProjects() {
  loading.value = true
  try {
    const res = await getAppInfoPage(1, -1)
    allProjects.value = res.records ?? []

    // Select the first project that has a taskId by default
    const first = allProjects.value.find((p) => p.taskId)
    if (first) {
      selectedId.value = first.id
      selectedProject.value = first
    }
  } catch (error) {
    window.$message.error(error instanceof Error ? error.message : t('scan.dashboard.load-failed'))
  } finally {
    loading.value = false
  }
}

function onSelectChange(value: string | undefined) {
  selectedId.value = value
  if (!value) {
    selectedProject.value = null
    return
  }
  const p = allProjects.value.find((item) => item.id === value)
  if (p) {
    selectedProject.value = p
  }
}

onMounted(loadProjects)
</script>

<template>
  <div>
    <!-- Stats Cards -->
    <div style="margin-bottom: 24px">
      <ARow :gutter="[16, 16]">
        <ACol :xs="24" :sm="12" :md="12" :lg="6" :xl="8">
          <div
            class="dashboard-stat-card px-3 py-3 border-slate-100 flex gap-3 shadow-sm items-center bg-white border rounded-lg"
          >
            <div
              class="dashboard-stat-icon dashboard-stat-icon--indigo flex shrink-0 items-center justify-center rounded-lg"
            >
              <RenderIcon icon="i-ant-design:folder-open-outlined" class="size-4" />
            </div>
            <div>
              <div class="text-xs text-slate-400 mb-0.5">
                {{ t('scan.dashboard.total-projects') }}
              </div>
              <div class="text-slate-800 font-bold text-xl">{{ stats.total }}</div>
            </div>
          </div>
        </ACol>
        <ACol :xs="24" :sm="12" :md="12" :lg="6" :xl="8">
          <div
            class="dashboard-stat-card px-3 py-3 border-slate-100 flex gap-3 shadow-sm items-center bg-white border rounded-lg"
          >
            <div
              class="dashboard-stat-icon dashboard-stat-icon--blue flex shrink-0 items-center justify-center rounded-lg"
            >
              <RenderIcon icon="i-ant-design:check-circle-outlined" class="size-4" />
            </div>
            <div>
              <div class="text-xs text-slate-400 mb-0.5">{{ t('scan.dashboard.scanned') }}</div>
              <div class="text-blue-600 font-bold text-xl">{{ stats.scanned }}</div>
            </div>
          </div>
        </ACol>
        <ACol :xs="24" :sm="12" :md="12" :lg="6" :xl="8">
          <div
            class="dashboard-stat-card px-3 py-3 border-slate-100 flex gap-3 shadow-sm items-center bg-white border rounded-lg"
          >
            <div
              class="dashboard-stat-icon dashboard-stat-icon--orange flex shrink-0 items-center justify-center rounded-lg"
            >
              <RenderIcon icon="i-ant-design:clock-circle-outlined" class="size-4" />
            </div>
            <div>
              <div class="text-xs text-slate-400 mb-0.5">{{ t('scan.dashboard.unscanned') }}</div>
              <div class="text-amber-500 font-bold text-xl">{{ stats.notScanned }}</div>
            </div>
          </div>
        </ACol>
      </ARow>
    </div>

    <!-- Atlas section -->
    <div v-if="selectedProject?.taskId" class="relative">
      <div class="flex gap-2 items-center right-4 top-4 absolute z-10">
        <ASelect
          v-if="selectOptions.length > 0"
          v-model:value="selectedId"
          :loading="loading"
          :placeholder="t('scan.dashboard.select-project')"
          show-search
          size="middle"
          :options="selectOptions"
          :list-height="400"
          option-filter-prop="label"
          class="w-40"
          @change="onSelectChange"
        />
        <AButton v-if="selectedProject?.taskId" size="middle" @click="openDepsModal">
          <RenderIcon icon="i-ant-design:database-outlined" class="mr-1" />
          {{ t('scan.dashboard.deps-analysis') }}
        </AButton>
      </div>

      <HomePageAtlas :task-id="selectedProject.taskId" :file-info="selectedProject.fileInfo" />
    </div>

    <div v-else class="flex flex-col items-center justify-center">
      <ASelect
        v-if="selectOptions.length > 0"
        v-model:value="selectedId"
        :loading="loading"
        :placeholder="t('scan.dashboard.select-a-project')"
        show-search
        size="middle"
        :options="selectOptions"
        :list-height="400"
        option-filter-prop="label"
        @change="onSelectChange"
      />

      <ACard
        v-if="selectOptions.length === 0"
        class="py-20 text-center border-0 w-full shadow-none"
      >
        <div class="mb-6 flex justify-center">
          <RenderIcon icon="i-ant-design:inbox-outlined" class="text-slate-200 size-24" />
        </div>
        <div class="text-slate-400" style="margin-bottom: 12px; font-size: 22px; font-weight: 600">
          {{ t('scan.dashboard.no-projects') }}
        </div>
        <div class="text-slate-400" style="font-size: 15px">
          {{ t('scan.dashboard.no-projects-hint') }}
        </div>
      </ACard>
      <ACard v-else class="py-12 text-center">
        <div class="text-slate-400 text-lg">{{ t('scan.dashboard.select-a-project') }}</div>
        <div class="text-slate-400 mt-2 text-sm">{{ t('scan.dashboard.select-hint') }}</div>
      </ACard>
    </div>
  </div>

  <!-- Dependency library analysis modal -->
  <AModal
    v-model:open="depsModalVisible"
    :title="t('scan.dashboard.deps-analysis')"
    :footer="null"
    width="90vw"
    :body-style="{ height: '80vh', padding: 0, overflow: 'hidden' }"
    destroy-on-close
    centered
  >
    <div class="graph-modal-container">
      <div v-if="depsLoading" class="graph-modal-loading">
        <ASpin size="large" />
        <p style="margin-top: 16px; color: #67e8f9">{{ t('scan.dashboard.deps-loading') }}</p>
      </div>
      <GraphCanvas
        v-else-if="depsEdges && depsEdges.length > 0"
        :edges="depsEdges"
        :auto-orbit="true"
        :show-community-boundaries="false"
        community-mode="auto"
      />
      <div v-else-if="!depsLoading" class="graph-modal-empty">
        <p>{{ t('scan.atlas.no-deps-detected') }}</p>
      </div>
    </div>
  </AModal>
</template>

<style lang="scss" scoped>
.graph-modal-container {
  width: 100%;
  height: 100%;
  background: #05070c;
}

.graph-modal-loading {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  height: 100%;
  background: #05070c;
}

.graph-modal-empty {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 100%;
  font-size: 16px;
  color: rgba(226, 232, 240, 0.5);
}
</style>

<style lang="scss">
.dark {
  .dashboard-stat-card {
    background: #1a1f2e !important;
    border-color: #2a3050 !important;

    .text-slate-400 {
      color: #a0adde !important;
    }

    .text-slate-800 {
      color: #e8edf5 !important;
    }

    .text-blue-600 {
      color: #60a5fa !important;
    }

    .text-amber-500 {
      color: #fbbf24 !important;
    }
  }

  .dashboard-stat-icon {
    &--indigo {
      color: #818cf8 !important;
      background: rgba(99, 102, 241, 0.2) !important;
    }

    &--blue {
      color: #60a5fa !important;
      background: rgba(59, 130, 246, 0.2) !important;
    }

    &--orange {
      color: #fb923c !important;
      background: rgba(249, 115, 22, 0.2) !important;
    }
  }

  .text-slate-200 {
    color: #475569 !important;
  }
  .text-slate-400 {
    color: #a0adde !important;
  }
}
</style>
