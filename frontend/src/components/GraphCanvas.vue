<script setup lang="ts">
import type { EdgeData } from '@/lib/edgesDataLoader'
import { nextTick, onMounted, onUnmounted, ref, watch } from 'vue'
import { useGraphScene } from '@/composables/useGraphScene'
import { loadEdgesData } from '@/lib/edgesDataLoader'
import { defaultForceConfig, ForceSimulation3D } from '@/lib/forceSimulation'

const props = withDefaults(
  defineProps<{
    autoOrbit?: boolean
    showCommunityBoundaries?: boolean
    communityMode?: 'off' | 'auto' | 'all'
    searchTerm?: string
    /** External edges data — the only data source; the component renders an empty state without it */
    edges?: EdgeData[] | null
  }>(),
  {
    autoOrbit: true,
    showCommunityBoundaries: true,
    communityMode: 'auto' as const,
    searchTerm: '',
    edges: null,
  },
)

const emit = defineEmits<{
  nodeSelect: [nodeId: string | null]
  hoveredNode: [nodeId: string | null]
  loaded: []
  loading: [isLoading: boolean]
}>()

const containerRef = ref<HTMLDivElement | null>(null)
const statusText = ref('Initializing starfield...')
/** True when no edges were passed in — the scene then shows an empty state instead of a graph */
const isEmpty = ref(false)
const isRunning = ref(true)
const simConfig = {
  ...defaultForceConfig,
  chargeStrength: -150,
  linkDistance: 38,
  linkStrength: 0.32,
  collisionRadius: 7,
  communityStrength: 0.24,
  centerStrength: 0.018,
  spread3D: 190,
  levelSpacing: 52,
  sphericalConstraint: 0.07,
}

function buildAdaptiveConfig(entityCount: number) {
  if (entityCount <= 10) {
    return {
      ...simConfig,
      chargeStrength: -60,
      linkDistance: 24,
      collisionRadius: 4,
      spread3D: 60,
      levelSpacing: 14,
      sphericalConstraint: 0.04,
    }
  }
  if (entityCount <= 50) {
    return {
      ...simConfig,
      chargeStrength: -90,
      linkDistance: 26,
      spread3D: 90,
    }
  }
  return simConfig
}

const scene = useGraphScene()
const { t } = useI18n()

// Watch for prop changes
watch(
  () => props.autoOrbit,
  (v) => {
    scene.autoOrbit.value = v
  },
)
watch(
  () => props.communityMode,
  (v) => {
    scene.communityMode.value = v
  },
)
watch(
  () => props.searchTerm,
  (v) => {
    scene.searchTerm.value = v
  },
)
watch(
  () => props.showCommunityBoundaries,
  (v) => {
    scene.showCommunityBoundaries.value = v
  },
)

// Node selection/hover
watch(scene.selectedNode, (node) => {
  emit('nodeSelect', node?.id ?? null)
})

watch(scene.hoveredNode, (node) => {
  emit('hoveredNode', node?.id ?? null)
})

watch(scene.loading, (v) => {
  emit('loading', v)
})

// Reinitialize when external edges change
watch(
  () => props.edges,
  (newEdges) => {
    if (newEdges && newEdges.length > 0) {
      scene.dispose()
      isRunning.value = true
      isEmpty.value = false
      scene.loading.value = true
      statusText.value = 'Loading new graph data...'
      nextTick(() => initialize())
    } else {
      // Edges were cleared: drop the rendered scene and fall back to the empty state
      scene.dispose()
      scene.loading.value = false
      isEmpty.value = true
    }
  },
)

async function initialize() {
  if (!containerRef.value) return

  // Edges come from the caller only. Without them there is nothing to simulate,
  // so show an explicit empty state instead of creating a scene full of mock data.
  if (!props.edges || props.edges.length === 0) {
    scene.loading.value = false
    isEmpty.value = true
    return
  }

  isEmpty.value = false
  scene.init(containerRef.value)
  scene.autoOrbit.value = props.autoOrbit
  scene.communityMode.value = props.communityMode
  scene.showCommunityBoundaries.value = props.showCommunityBoundaries

  statusText.value = `Loading graph data (${props.edges.length} edges)...`
  await nextTick()
  const data = loadEdgesData(props.edges)
  statusText.value = `Loaded ${data.entities.length} functions, ${data.relationships.length} calls`

  setTimeout(() => {
    statusText.value = 'Solving 3D force lattice...'
  }, 100)

  const adaptiveConfig = buildAdaptiveConfig(data.entities.length)
  const simulation = new ForceSimulation3D(adaptiveConfig)
  const layout = await simulation.generateLayout(data)

  statusText.value = 'Projecting knowledge universe...'
  scene.setLayout(layout)
  await nextTick()

  // Emit loaded after scene renders
  setTimeout(() => {
    emit('loaded')
  }, 300)
}

onMounted(() => {
  initialize()
})

onUnmounted(() => {
  scene.dispose()
  isRunning.value = false
})
</script>

<template>
  <div ref="containerRef" class="graph-canvas-container">
    <!-- Loading overlay -->
    <div v-if="scene.loading.value" class="loading-overlay">
      <div class="loading-content">
        <div class="loading-spinner" />
        <p class="loading-text">{{ statusText }}</p>
      </div>
    </div>
    <!-- No edges passed in: nothing to render, so state it plainly -->
    <div v-else-if="isEmpty" class="empty-overlay">
      <p class="empty-text">{{ t('scan.atlas.no-deps-detected') }}</p>
    </div>
  </div>
</template>

<style scoped>
.graph-canvas-container {
  position: relative;
  width: 100%;
  height: 100%;
  overflow: hidden;
  background: #05070c;
}

.loading-overlay {
  position: absolute;
  inset: 0;
  z-index: 10;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(5, 7, 12, 0.85);
  backdrop-filter: blur(4px);
}

.loading-content {
  text-align: center;
}

.loading-spinner {
  width: 36px;
  height: 36px;
  margin: 0 auto 16px;
  border: 2px solid rgba(103, 232, 249, 0.2);
  border-top-color: #67e8f9;
  border-radius: 50%;
  animation: spin 0.8s linear infinite;
}

@keyframes spin {
  to {
    transform: rotate(360deg);
  }
}

.loading-text {
  font-family: ui-monospace, monospace;
  font-size: 14px;
  color: rgba(103, 232, 249, 0.8);
  letter-spacing: 0.1em;
}

.empty-overlay {
  position: absolute;
  inset: 0;
  z-index: 10;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #05070c;
}

.empty-text {
  font-size: 14px;
  color: rgba(148, 163, 184, 0.9);
  letter-spacing: 0.05em;
}
</style>
