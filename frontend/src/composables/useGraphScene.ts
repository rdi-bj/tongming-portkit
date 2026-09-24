import type { GraphLayout, Link3D, Node3D } from '@/lib/forceSimulation'
import type { Community } from '@/lib/graphData'
import * as THREE from 'three'
import { OrbitControls } from 'three/examples/jsm/controls/OrbitControls.js'
import { Line2 } from 'three/examples/jsm/lines/Line2.js'
import { LineGeometry } from 'three/examples/jsm/lines/LineGeometry.js'
import { LineMaterial } from 'three/examples/jsm/lines/LineMaterial.js'
import { EffectComposer } from 'three/examples/jsm/postprocessing/EffectComposer.js'
import { RenderPass } from 'three/examples/jsm/postprocessing/RenderPass.js'
import { ShaderPass } from 'three/examples/jsm/postprocessing/ShaderPass.js'
import { UnrealBloomPass } from 'three/examples/jsm/postprocessing/UnrealBloomPass.js'
import { preloadFont, Text } from 'troika-three-text'
import { ref, shallowRef, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { calculateLinkThickness } from '@/lib/forceSimulation'

export interface GraphSceneOptions {
  container: HTMLElement | null
  layout: GraphLayout | null
  selectedNode?: Node3D | null
  hoveredNode?: Node3D | null
  visibleCommunities?: Community[]
  communityMode?: 'off' | 'auto' | 'all'
  searchTerm?: string
  showCommunityBoundaries?: boolean
  autoOrbit?: boolean
}

export interface GraphSceneState {
  selectedNode: Node3D | null
  hoveredNode: Node3D | null
  highlightedNodeIds: Set<string>
  loading: boolean
  error: string | null
}

const BLOOM_SCENE = 1

export function useGraphScene() {
  const { t } = useI18n()
  // ── Reactive state ──
  const sceneContainer = shallowRef<HTMLElement | null>(null)
  const loaded = ref(false)
  const loading = ref(true)
  const selectedNode = ref<Node3D | null>(null)
  const hoveredNode = ref<Node3D | null>(null)
  const searchTerm = ref('')
  const communityMode = ref<'off' | 'auto' | 'all'>('auto')
  const showCommunityBoundaries = ref(true)
  const autoOrbit = ref(true)
  const bloomEnabled = ref(true)

  // ── Three.js internals ──
  let renderer: THREE.WebGLRenderer | null = null
  let scene: THREE.Scene | null = null
  let camera: THREE.PerspectiveCamera | null = null
  let controls: OrbitControls | null = null
  let raycaster: THREE.Raycaster | null = null
  const pointer: THREE.Vector2 = new THREE.Vector2()
  let animationId: number = 0
  let hasInteracted = false
  let currentLayout: GraphLayout | null = null
  let graphSize: number = 200
  let highlightedLinkIds = new Set<string>()
  let heroEdgeIds = new Set<string>()

  // Selective bloom (material-swapping approach, matching React)
  let bloomComposer: EffectComposer | null = null
  let bloomPass: UnrealBloomPass | null = null
  let bloomLayer: THREE.Layers
  let darkMaterial: THREE.MeshBasicMaterial
  const bloomMaterials: Map<string, THREE.Material> = new Map() // uuid → original material
  let nonBloomMeshes: THREE.Mesh[] = []
  let cacheNeedsRebuild = true

  // Object caches for fast scene updates
  const nodeMeshes: Map<string, THREE.Mesh> = new Map()
  const nodeLabels: Map<string, THREE.Mesh> = new Map()
  const linkLines: Map<string, Line2> = new Map()
  const energyMeshes: Map<string, THREE.Mesh> = new Map()
  const boundaryMeshes: Map<string, THREE.Mesh> = new Map()
  const boundaryLabels: Map<string, THREE.Mesh> = new Map()

  // Node shader material (shared base, cloned per node)
  const nodeShaderDefs = {
    vertexShader: `
      varying vec3 vN; varying vec3 vV;
      void main(){
        vN = normalize(normalMatrix * normal);
        vec4 mv = modelViewMatrix * vec4(position,1.0);
        vV = normalize(-mv.xyz);
        gl_Position = projectionMatrix * mv;
      }
    `,
    fragmentShader: `
      uniform vec3 colorCore, colorRim; uniform float time; uniform float opacity;
      varying vec3 vN; varying vec3 vV;
      void main(){
        float fres = pow(1.0 - max(dot(vN, vV), 0.0), 2.0);
        float core = smoothstep(0.0, 0.6, fres);
        vec3 col = mix(colorCore, colorRim, fres);
        float pulse = 0.6 + 0.4*sin(time*1.5);
        gl_FragColor = vec4(col*(core*1.2 + pulse*0.15), opacity);
      }
    `,
  }

  // ── Selective bloom helpers (material-swapping) ──

  function rebuildBloomCache() {
    if (!scene) return
    bloomMaterials.clear()
    nonBloomMeshes = []

    scene.traverse((obj) => {
      if (obj instanceof THREE.Mesh) {
        const mesh = obj as THREE.Mesh
        // Skip text meshes — they should not be affected by bloom processing
        if (mesh.userData?.isLabel) return

        if (mesh.layers.test(bloomLayer)) {
          // Bloom objects keep their material
        } else {
          nonBloomMeshes.push(mesh)
          const mat = mesh.material as THREE.Material
          if (!Array.isArray(mat)) {
            bloomMaterials.set(mesh.uuid, mat)
          }
        }
      }
    })
    cacheNeedsRebuild = false
  }

  function darkenNonBloomedObjects() {
    for (let i = 0; i < nonBloomMeshes.length; i++) {
      nonBloomMeshes[i].material = darkMaterial
    }
  }

  function restoreOriginalMaterials() {
    for (let i = 0; i < nonBloomMeshes.length; i++) {
      const mesh = nonBloomMeshes[i]
      const original = bloomMaterials.get(mesh.uuid)
      if (original) {
        mesh.material = original
      }
    }
  }

  function initBloomResources() {
    if (!renderer) return
    const width = sceneContainer.value?.clientWidth ?? window.innerWidth
    const height = sceneContainer.value?.clientHeight ?? window.innerHeight

    bloomLayer = new THREE.Layers()
    bloomLayer.set(BLOOM_SCENE)

    darkMaterial = new THREE.MeshBasicMaterial({
      color: 0x000000,
      transparent: true,
      opacity: 0.0,
    })
    ;(darkMaterial as THREE.Material & { colorWrite?: boolean }).colorWrite = false
    darkMaterial.depthWrite = false

    // Composer renders to screen directly
    bloomComposer = new EffectComposer(renderer)
    bloomComposer.setSize(width, height)

    const renderPass = new RenderPass(scene!, camera!)
    renderPass.clear = true
    bloomComposer.addPass(renderPass)

    bloomPass = new UnrealBloomPass(new THREE.Vector2(width, height), 0.66, 1.35, 0.2)
    bloomPass.resolution.set(width, height)
    bloomComposer.addPass(bloomPass)

    // Sync bloom pass enabled state with reactive flag
    bloomPass.enabled = bloomEnabled.value

    // Vignette shader
    const VignetteShader = {
      uniforms: { tDiffuse: { value: null }, strength: { value: 0.18 } },
      vertexShader: `
        varying vec2 vUv;
        void main(){ vUv = uv; gl_Position = projectionMatrix * modelViewMatrix * vec4(position, 1.0); }
      `,
      fragmentShader: `
        uniform sampler2D tDiffuse; uniform float strength; varying vec2 vUv;
        void main(){
          vec4 c = texture2D(tDiffuse, vUv);
          float d = distance(vUv, vec2(0.5));
          float v = smoothstep(0.6, 0.98, d);
          c.rgb *= (1.0 - v * strength);
          gl_FragColor = c;
        }
      `,
      depthTest: false,
      depthWrite: false,
    }
    const vignettePass = new ShaderPass(VignetteShader)
    vignettePass.material.depthTest = false
    vignettePass.material.depthWrite = false
    bloomComposer.addPass(vignettePass)

    // Ensure all passes are properly sized
    bloomComposer.setSize(width, height)

    cacheNeedsRebuild = true
  }

  // ── Setup ──
  function init(container: HTMLElement) {
    sceneContainer.value = container
    const width = container.clientWidth
    const height = container.clientHeight

    // Renderer
    renderer = new THREE.WebGLRenderer({
      antialias: true,
      alpha: false,
    })
    renderer.setSize(width, height)
    renderer.setPixelRatio(Math.min(window.devicePixelRatio, 2))
    renderer.toneMapping = THREE.ACESFilmicToneMapping
    renderer.toneMappingExposure = 1.2
    renderer.outputColorSpace = THREE.SRGBColorSpace
    container.appendChild(renderer.domElement)

    // Scene
    scene = new THREE.Scene()
    scene.background = new THREE.Color(0x06070a)

    // Camera
    camera = new THREE.PerspectiveCamera(60, width / height, 0.1, 5000)
    camera.position.set(300, 150, 300)
    camera.lookAt(0, 0, 0)

    // Controls
    controls = new OrbitControls(camera, renderer.domElement)
    controls.enableDamping = true
    controls.dampingFactor = 0.05
    controls.minDistance = 0
    controls.maxDistance = 2000
    controls.target.set(0, 0, 0)
    controls.update()

    // Raycaster
    raycaster = new THREE.Raycaster()

    // Init postprocessing before creating scene objects
    initBloomResources()

    // Warm up default font for troika-three-text (fire-and-forget)
    preloadFont({}, () => {})

    // Nebula backdrop
    createNebulaBackdrop()

    // Scene lighting
    const ambient = new THREE.AmbientLight(0xffffff, 0.6)
    scene.add(ambient)

    const dirLight1 = new THREE.DirectionalLight(new THREE.Color('#a0b9ff'), 1.0)
    dirLight1.position.set(100, 100, 120)
    scene.add(dirLight1)

    const dirLight2 = new THREE.DirectionalLight(new THREE.Color('#7f8cff'), 0.6)
    dirLight2.position.set(-120, -80, -100)
    scene.add(dirLight2)

    // Event listeners
    renderer.domElement.addEventListener('pointermove', onPointerMove)
    renderer.domElement.addEventListener('pointerdown', onPointerDown)
    renderer.domElement.addEventListener('pointerdown', onUserInteraction)
    renderer.domElement.addEventListener('wheel', onUserInteraction, { passive: true })
    window.addEventListener('resize', onResize)

    // Start loop
    animate()

    loaded.value = true
  }

  function dispose() {
    if (animationId) cancelAnimationFrame(animationId)
    if (controls) controls.dispose()
    if (bloomComposer) bloomComposer.dispose()
    if (renderer) {
      renderer.domElement.removeEventListener('pointermove', onPointerMove)
      renderer.domElement.removeEventListener('pointerdown', onPointerDown)
      renderer.domElement.removeEventListener('pointerdown', onUserInteraction)
      renderer.domElement.removeEventListener('wheel', onUserInteraction)
      window.removeEventListener('resize', onResize)
      renderer.dispose()
      if (renderer.domElement.parentElement) {
        renderer.domElement.parentElement.removeChild(renderer.domElement)
      }
    }
    // Clear caches
    nodeMeshes.clear()
    nodeLabels.clear()
    linkLines.clear()
    energyMeshes.clear()
    boundaryMeshes.clear()
    boundaryLabels.clear()
    bloomMaterials.clear()
    nonBloomMeshes = []
  }

  // ── Nebula backdrop ──
  function createNebulaBackdrop() {
    if (!scene) return
    const mat = new THREE.ShaderMaterial({
      side: THREE.BackSide,
      depthWrite: false,
      uniforms: {
        cTop: { value: new THREE.Color('#101221') },
        cBot: { value: new THREE.Color('#05060b') },
      },
      vertexShader: `
        varying vec2 vUv;
        void main(){ vUv=uv; gl_Position=projectionMatrix*modelViewMatrix*vec4(position,1.0); }
      `,
      fragmentShader: `
        uniform vec3 cTop,cBot; varying vec2 vUv;
        void main(){ vec3 col=mix(cBot,cTop, vUv.y); gl_FragColor=vec4(col,1.0); }
      `,
    })
    const mesh = new THREE.Mesh(new THREE.PlaneGeometry(4000, 2500), mat)
    mesh.position.set(0, 0, -800)
    mesh.layers.disable(BLOOM_SCENE)
    scene.add(mesh)
  }

  // ── Build scene graph from layout ──
  function buildScene(layout: GraphLayout) {
    if (!scene || !camera) return
    currentLayout = layout

    // Clear existing dynamic objects
    clearDynamicObjects()

    // Determine hero edges (top 10% by weight, matching React)
    const sortedWeights = layout.links.map((l) => l.weight).sort((a, b) => a - b)
    let heroThreshold = Infinity
    if (sortedWeights.length > 0) {
      const qIndex = Math.floor(sortedWeights.length * 0.9)
      heroThreshold = sortedWeights[Math.min(sortedWeights.length - 1, Math.max(0, qIndex))]
    }
    const ids = new Set(layout.links.filter((l) => l.weight >= heroThreshold).map((l) => l.id))
    heroEdgeIds = ids

    // Create nodes
    layout.nodes.forEach((node) => {
      createNodeMesh(node)
    })

    // Create links
    layout.links.forEach((link) => {
      createLinkLine(link)
      if (heroEdgeIds.has(link.id) || link.unresolved) {
        createEnergyEdge(link)
      }
    })

    // Create community boundaries
    updateCommunityBoundaries(
      layout.communities,
      selectedNode.value ?? undefined,
      communityMode.value,
    )

    // Set camera for reasonable initial view
    updateCameraView(layout)
    loading.value = false

    // Rebuild bloom cache after scene changes
    rebuildBloomCache()
  }

  function clearDynamicObjects() {
    const s = scene
    if (!s) return

    nodeMeshes.forEach((mesh) => {
      s.remove(mesh)
      mesh.geometry.dispose()
      if (Array.isArray(mesh.material)) mesh.material.forEach((m) => m.dispose())
      else mesh.material.dispose()
    })
    nodeMeshes.clear()

    nodeLabels.forEach((mesh) => {
      s.remove(mesh)
      ;(mesh as unknown as { dispose: () => void }).dispose()
    })
    nodeLabels.clear()

    linkLines.forEach((line) => {
      s.remove(line)
      line.geometry.dispose()
      if (Array.isArray(line.material)) line.material.forEach((m) => m.dispose())
      else (line.material as THREE.Material).dispose()
    })
    linkLines.clear()

    energyMeshes.forEach((mesh) => {
      s.remove(mesh)
      mesh.geometry.dispose()
      if (Array.isArray(mesh.material)) mesh.material.forEach((m) => m.dispose())
      else mesh.material.dispose()
    })
    energyMeshes.clear()

    boundaryMeshes.forEach((mesh) => {
      s.remove(mesh)
      mesh.geometry.dispose()
      if (Array.isArray(mesh.material)) mesh.material.forEach((m) => m.dispose())
      else mesh.material.dispose()
    })
    boundaryMeshes.clear()

    boundaryLabels.forEach((mesh) => {
      s.remove(mesh)
      ;(mesh as unknown as { dispose: () => void }).dispose()
    })
    boundaryLabels.clear()

    // Mark bloom cache for rebuild
    cacheNeedsRebuild = true
  }

  function createNodeMesh(node: Node3D) {
    if (!scene) return

    const size = node.computedSize
    const geometry = new THREE.SphereGeometry(size, 32, 32)

    const material = new THREE.ShaderMaterial({
      transparent: true,
      blending: THREE.AdditiveBlending,
      depthWrite: false,
      uniforms: {
        colorCore: { value: new THREE.Color('#7de1ff') },
        colorRim: { value: new THREE.Color('#ff9bd5') },
        time: { value: 0.0 },
        opacity: { value: 0.85 },
      },
      vertexShader: nodeShaderDefs.vertexShader,
      fragmentShader: nodeShaderDefs.fragmentShader,
    })

    const mesh = new THREE.Mesh(geometry, material)
    mesh.position.set(node.x, node.y, node.z)
    mesh.userData.nodeId = node.id
    mesh.userData.isNode = true
    mesh.layers.enable(BLOOM_SCENE)
    scene.add(mesh)
    nodeMeshes.set(node.id, mesh)

    // Create text label via troika-three-text
    const label = createTextLabel(node.title, size)
    if (label) {
      label.position.set(node.x, node.y + size + 4, node.z)
      label.userData.nodeId = node.id
      label.userData.isLabel = true
      scene.add(label)
      nodeLabels.set(node.id, label)
    }
  }

  function createTextLabel(text: string, nodeSize: number): THREE.Mesh | null {
    const truncated = text.length > 25 ? `${text.substring(0, 25)}...` : text
    const textMesh = new Text()
    textMesh.text = truncated
    textMesh.fontSize = Math.max(1.4, nodeSize * 0.7)
    textMesh.color = 0xffffff
    textMesh.fillOpacity = 0.95
    textMesh.outlineWidth = 0.08
    textMesh.outlineColor = 0x000000
    textMesh.anchorX = 'center'
    textMesh.anchorY = 'middle'
    textMesh.maxWidth = 30
    textMesh.textAlign = 'center'
    textMesh.sync()
    return textMesh
  }

  function createLinkLine(link: Link3D) {
    if (!scene) return
    const thickness = calculateLinkThickness(link.weight)
    const geometry = new LineGeometry()
    geometry.setPositions([
      link.source.x,
      link.source.y,
      link.source.z,
      link.target.x,
      link.target.y,
      link.target.z,
    ])
    const material = new LineMaterial({
      color: 0x888888,
      linewidth: thickness,
      transparent: true,
      opacity: 0.7,
      resolution: new THREE.Vector2(
        sceneContainer.value?.clientWidth ?? window.innerWidth,
        sceneContainer.value?.clientHeight ?? window.innerHeight,
      ),
    })
    const line = new Line2(geometry, material)
    line.computeLineDistances()
    line.userData.linkId = link.id
    line.userData.linkWeight = link.weight
    scene.add(line)
    linkLines.set(link.id, line)
  }

  function createEnergyEdge(link: Link3D) {
    if (!scene) return
    const source = new THREE.Vector3(link.source.x, link.source.y, link.source.z)
    const target = new THREE.Vector3(link.target.x, link.target.y, link.target.z)
    const curve = new THREE.CatmullRomCurve3([source, target])
    const thickness = Math.max(0.06, calculateLinkThickness(link.weight) * 0.2)

    const unresolved = link.unresolved
    const geometry = new THREE.TubeGeometry(curve, 32, thickness, 6, false)
    const material = new THREE.ShaderMaterial({
      transparent: true,
      blending: THREE.AdditiveBlending,
      depthWrite: false,
      uniforms: {
        time: { value: 0.0 },
        c1: { value: unresolved ? new THREE.Color('#ff3333') : new THREE.Color('#6be6ff') },
        c2: { value: unresolved ? new THREE.Color('#ff6644') : new THREE.Color('#ff8bcb') },
        uAlpha: { value: unresolved ? 0.85 : 0.7 },
      },
      vertexShader: `
        varying float vLen;
        void main(){ vLen = position.y; gl_Position=projectionMatrix*modelViewMatrix*vec4(position,1.0); }
      `,
      fragmentShader: `
        uniform float time; uniform vec3 c1,c2; uniform float uAlpha; varying float vLen;
        float hash(float x){ return fract(sin(x*12.9898)*43758.5453); }
        void main(){
          float t = fract(vLen*0.1 - time*0.8);
          float band = smoothstep(0.0,0.05,t)*smoothstep(0.2,0.15,t);
          vec3 col = mix(c1,c2, t);
          gl_FragColor = vec4(col*(0.5+band*2.0), uAlpha);
        }
      `,
    })
    const mesh = new THREE.Mesh(geometry, material)
    mesh.layers.enable(BLOOM_SCENE)
    mesh.userData.linkId = link.id
    mesh.userData.isNode = false
    mesh.userData.unresolved = link.unresolved
    scene.add(mesh)
    energyMeshes.set(link.id, mesh)
  }

  function updateCommunityBoundaries(
    communities: Community[],
    selectedComm?: { id: string; computedHierarchy?: Community['computedHierarchy'] },
    mode?: string,
  ) {
    if (!scene) return

    // Remove old boundaries
    boundaryMeshes.forEach((mesh) => {
      scene!.remove(mesh)
      mesh.geometry.dispose()
      if (Array.isArray(mesh.material)) mesh.material.forEach((m) => m.dispose())
      else mesh.material.dispose()
    })
    boundaryMeshes.clear()
    boundaryLabels.forEach((mesh) => {
      scene!.remove(mesh)
      ;(mesh as unknown as { dispose: () => void }).dispose()
    })
    boundaryLabels.clear()
    cacheNeedsRebuild = true

    if (!showCommunityBoundaries.value) return

    communities.forEach((community) => {
      if (!community.computedBounds) return

      const bounds = community.computedBounds
      const isSelected = selectedComm && community.id === selectedComm.id
      const isAncestor =
        selectedComm &&
        selectedComm.computedHierarchy?.parentCommunities.some((p) => p.id === community.id)
      const isDescendant =
        selectedComm &&
        selectedComm.computedHierarchy?.childCommunities.some((c) => c.id === community.id)

      let color: string
      if (isSelected) color = '#ffffff'
      else if (isAncestor) color = '#cccccc'
      else if (isDescendant) color = '#aaaaaa'
      else color = '#ffffff'

      const baseOpacity = isSelected ? 0.3 : isAncestor ? 0.22 : isDescendant ? 0.18 : 0.2
      const op = mode === 'auto' ? baseOpacity + 0.2 : baseOpacity

      const mat = new THREE.MeshBasicMaterial({
        color: new THREE.Color(color),
        transparent: true,
        opacity: op,
        wireframe: true,
        depthTest: false,
        depthWrite: false,
        fog: false,
      })

      const geo = new THREE.BoxGeometry(...bounds.size)
      const mesh = new THREE.Mesh(geo, mat)
      const zOffset = community.level * 0.1
      mesh.position.set(bounds.center[0], bounds.center[1], bounds.center[2] + zOffset)
      mesh.renderOrder = 999
      mesh.frustumCulled = false
      mesh.layers.enable(BLOOM_SCENE)
      scene!.add(mesh)
      boundaryMeshes.set(community.id, mesh)

      // Community label — translate known category keys
      const catKey = community.title
      let labelTitle: string
      if (catKey === 'standard-system') {
        labelTitle = `${t('scan.atlas.standard-lib')}/${t('scan.atlas.system-lib')}`
      } else if (catKey === 'standard') {
        labelTitle = t('scan.atlas.standard-lib')
      } else if (catKey === 'system') {
        labelTitle = t('scan.atlas.system-lib')
      } else if (catKey === 'third_party') {
        labelTitle = t('scan.atlas.third-party-lib')
      } else {
        labelTitle = catKey
      }
      const labelText = `${getLevelLabel(community.level)}: ${labelTitle}`
      const labelMesh = createCommunityLabel(labelText, color, bounds.size[0])
      if (labelMesh) {
        labelMesh.position.set(
          bounds.center[0],
          bounds.center[1] + bounds.size[1] / 2 + 8,
          bounds.center[2],
        )
        labelMesh.userData.isLabel = true
        scene!.add(labelMesh)
        boundaryLabels.set(community.id, labelMesh)
      }

      // Community size label (matching React)
      const sizeText = `${community.size} entities`
      const sizeMesh = new Text()
      sizeMesh.text = sizeText
      sizeMesh.fontSize = Math.max(1, Math.min(2.5, bounds.size[0] * 0.05))
      sizeMesh.color = '#ffffff'
      sizeMesh.outlineWidth = 0.04
      sizeMesh.outlineColor = '#333333'
      sizeMesh.anchorX = 'center'
      sizeMesh.anchorY = 'bottom'
      sizeMesh.textAlign = 'center'
      sizeMesh.renderOrder = 999
      sizeMesh.frustumCulled = false
      sizeMesh.position.set(
        bounds.center[0],
        bounds.center[1] + bounds.size[1] / 2 + 4,
        bounds.center[2],
      )
      sizeMesh.sync()
      sizeMesh.userData.isLabel = true
      scene!.add(sizeMesh)
      boundaryLabels.set(`size-${community.id}`, sizeMesh)
    })
  }

  function createCommunityLabel(
    text: string,
    color: string,
    maxBoundSize: number,
  ): THREE.Mesh | null {
    const textMesh = new Text()
    textMesh.text = text
    textMesh.fontSize = Math.max(1.0, Math.min(3, maxBoundSize * 0.08))
    textMesh.color = color
    textMesh.outlineWidth = 0.05
    textMesh.outlineColor = '#ffffff'
    textMesh.anchorX = 'center'
    textMesh.anchorY = 'bottom'
    textMesh.maxWidth = maxBoundSize
    textMesh.textAlign = 'center'
    textMesh.renderOrder = 999
    textMesh.frustumCulled = false
    textMesh.sync()
    return textMesh
  }

  // ── Camera positioning ──
  function updateCameraView(layout: GraphLayout) {
    if (!camera || !controls) return

    // Calculate bounding sphere of nodes
    const center = new THREE.Vector3()
    let maxDist = 0
    layout.nodes.forEach((node) => {
      const v = new THREE.Vector3(node.x, node.y, node.z)
      center.add(v)
      const d = v.length()
      if (d > maxDist) maxDist = d
    })
    center.divideScalar(layout.nodes.length)
    controls.target.copy(center)

    const radius = Math.max(maxDist, 60)
    graphSize = radius
    const dist = radius * 1.8
    camera.position.set(dist * 0.6, dist * 0.5, dist * 0.8)
    camera.lookAt(center)
    controls.update()
  }

  // ── Pointer events ──
  let lastHoveredId: string | null = null

  function onUserInteraction() {
    hasInteracted = true
    autoOrbit.value = false
  }

  function onPointerMove(event: PointerEvent) {
    if (!renderer || !camera || !raycaster) return
    const rect = renderer.domElement.getBoundingClientRect()
    pointer.x = ((event.clientX - rect.left) / rect.width) * 2 - 1
    pointer.y = -((event.clientY - rect.top) / rect.height) * 2 + 1

    raycaster.setFromCamera(pointer, camera)

    // Collect all node meshes
    const meshes: THREE.Mesh[] = []
    nodeMeshes.forEach((m) => meshes.push(m))

    const intersects = raycaster.intersectObjects(meshes)

    if (intersects.length > 0) {
      const hit = intersects[0].object as THREE.Mesh
      const nodeId = hit.userData.nodeId as string
      if (nodeId && currentLayout) {
        const node = currentLayout.nodes.find((n) => n.id === nodeId)
        if (node && node.id !== lastHoveredId) {
          lastHoveredId = node.id
          hoveredNode.value = node
          renderer.domElement.style.cursor = 'pointer'
        }
      }
    } else if (lastHoveredId !== null) {
      lastHoveredId = null
      hoveredNode.value = null
      renderer.domElement.style.cursor = 'default'
    }
  }

  function onPointerDown(_event: PointerEvent) {
    if (!renderer || !camera || !raycaster || !currentLayout) return
    const rect = renderer.domElement.getBoundingClientRect()
    pointer.x = ((_event.clientX - rect.left) / rect.width) * 2 - 1
    pointer.y = -((_event.clientY - rect.top) / rect.height) * 2 + 1

    raycaster.setFromCamera(pointer, camera)

    const meshes: THREE.Mesh[] = []
    nodeMeshes.forEach((m) => meshes.push(m))

    const intersects = raycaster.intersectObjects(meshes)
    if (intersects.length > 0) {
      const hit = intersects[0].object as THREE.Mesh
      const nodeId = hit.userData.nodeId as string
      if (nodeId) {
        const node = currentLayout.nodes.find((n) => n.id === nodeId)
        if (node) {
          selectedNode.value = node
          if (selectedNode.value) {
            // Highlight connected links
            const newHighlighted = new Set<string>()
            currentLayout.links.forEach((link) => {
              if (link.source.id === node.id || link.target.id === node.id) {
                newHighlighted.add(link.id)
              }
            })

            // Create energy edges for non-hero highlighted links (matching React)
            newHighlighted.forEach((linkId) => {
              if (!heroEdgeIds.has(linkId) && !energyMeshes.has(linkId)) {
                const link = currentLayout!.links.find((l) => l.id === linkId)
                if (link) createEnergyEdge(link)
              }
            })

            // Remove energy edges that are no longer highlighted (non-hero only)
            highlightedLinkIds.forEach((linkId) => {
              if (!heroEdgeIds.has(linkId) && !newHighlighted.has(linkId)) {
                const mesh = energyMeshes.get(linkId)
                if (mesh) {
                  scene?.remove(mesh)
                  mesh.geometry.dispose()
                  ;(mesh.material as THREE.Material).dispose()
                  energyMeshes.delete(linkId)
                }
              }
            })

            highlightedLinkIds = newHighlighted
          } else {
            highlightedLinkIds = new Set<string>()
          }
          return
        }
      }
    }
    selectedNode.value = null
    highlightedLinkIds.clear()
  }

  function onResize() {
    if (!renderer || !camera || !sceneContainer.value) return
    const width = sceneContainer.value.clientWidth
    const height = sceneContainer.value.clientHeight

    camera.aspect = width / height
    camera.updateProjectionMatrix()
    renderer.setSize(width, height)

    if (bloomComposer) {
      bloomComposer.setSize(width, height)
      bloomPass?.resolution.set(width, height)
    }
  }

  // ── Animation loop ──
  const clock = new THREE.Timer()

  function animate() {
    animationId = requestAnimationFrame(animate)

    if (!scene || !camera || !controls) return

    clock.update()
    const elapsed = clock.getElapsed()

    // Auto-orbit — use actual graph size
    if (autoOrbit.value && !hasInteracted) {
      const graphCenter = controls.target
      const radius = Math.max(graphSize * 1.2, 80)
      const angle = elapsed * 0.05
      const x = graphCenter.x + Math.cos(angle) * radius
      const z = graphCenter.z + Math.sin(angle) * radius
      const y = graphCenter.y + radius * 0.5
      camera.position.set(x, y, z)
      camera.lookAt(graphCenter)
    }

    // Update node shader uniforms
    nodeMeshes.forEach((mesh, nodeId) => {
      const mat = mesh.material as THREE.ShaderMaterial
      const uTime = mat.uniforms.time
      const uOpacity = mat.uniforms.opacity
      const uColorCore = mat.uniforms.colorCore
      uTime.value = elapsed
      uOpacity.value = 0.85

      // Selection/hover highlights
      if (selectedNode.value?.id === nodeId) {
        mesh.scale.setScalar(1.5)
        ;(uColorCore.value as THREE.Color).set('#b6f3ff')
      } else if (hoveredNode.value?.id === nodeId) {
        mesh.scale.setScalar(1.25)
      } else {
        mesh.scale.setScalar(1.0)
        ;(uColorCore.value as THREE.Color).set('#7de1ff')
      }

      // Isolator mode dimming
      if (communityMode.value === 'auto' && selectedNode.value) {
        const isInHierarchy = isNodeInHierarchy(nodeId)
        uOpacity.value = isInHierarchy ? 0.85 : 0.25
      }
    })

    // Update energy edge shader uniforms
    energyMeshes.forEach((mesh) => {
      const mat = mesh.material as THREE.ShaderMaterial
      mat.uniforms.time.value = elapsed
    })

    // Update link opacities for isolator mode
    if (communityMode.value === 'auto' && selectedNode.value) {
      linkLines.forEach((line, linkId) => {
        const link = currentLayout?.links.find((l) => l.id === linkId)
        if (link) {
          const srcIn = isNodeInHierarchy(link.source.id)
          const tgtIn = isNodeInHierarchy(link.target.id)
          const mat = line.material
          const isHighlighted = highlightedLinkIds.has(linkId)
          if (srcIn && tgtIn) {
            mat.color.set(isHighlighted ? '#ffffff' : '#888888')
            mat.opacity = isHighlighted ? 0.95 : 0.7
            mat.linewidth = isHighlighted
              ? calculateLinkThickness(link.weight) * 2
              : calculateLinkThickness(link.weight)
          } else {
            mat.opacity = 0.25
            mat.color.set('#888888')
          }
        }
      })
      energyMeshes.forEach((mesh, linkId) => {
        const link = currentLayout?.links.find((l) => l.id === linkId)
        if (link) {
          const srcIn = isNodeInHierarchy(link.source.id)
          const tgtIn = isNodeInHierarchy(link.target.id)
          const showEnergy =
            link.unresolved ||
            ((heroEdgeIds.has(linkId) || highlightedLinkIds.has(linkId)) &&
              (communityMode.value !== 'auto' || !selectedNode.value || (srcIn && tgtIn)))
          mesh.visible = srcIn && tgtIn && showEnergy
        }
      })
    } else {
      linkLines.forEach((line) => {
        const mat = line.material
        const isHighlighted = highlightedLinkIds.has(line.userData.linkId as string)
        const weight = line.userData.linkWeight as number
        mat.color.set(isHighlighted ? '#ffffff' : '#888888')
        mat.opacity = isHighlighted ? 0.95 : 0.7
        mat.linewidth = isHighlighted
          ? calculateLinkThickness(weight) * 2
          : calculateLinkThickness(weight)
      })
      energyMeshes.forEach((mesh, linkId) => {
        mesh.visible =
          mesh.userData.unresolved || heroEdgeIds.has(linkId) || highlightedLinkIds.has(linkId)
      })
    }

    controls.enabled = !(autoOrbit.value && !hasInteracted)
    controls.update()

    // Make text labels always face the camera (billboard)
    // Copy to a local const: the null check would not survive into the callbacks below
    const billboardCamera = camera
    if (billboardCamera) {
      nodeLabels.forEach((label) => {
        const nodeId = label.userData.nodeId as string
        const isInHierarchy = selectedNode.value ? isNodeInHierarchy(nodeId) : true

        // Hide in isolator mode when not in hierarchy (matching React)
        label.visible = !selectedNode.value || communityMode.value !== 'auto' || isInHierarchy

        label.lookAt(billboardCamera.position)
      })
      boundaryLabels.forEach((label) => label.lookAt(billboardCamera.position))
    }

    // ── Render ──
    // Always render through composer for consistent color pipeline (matching React)
    if (!bloomComposer) return

    if (cacheNeedsRebuild) rebuildBloomCache()

    if (bloomEnabled.value) {
      // Material-swapping selective bloom
      darkenNonBloomedObjects()
    }

    const prevAutoClear = renderer!.autoClear
    renderer!.autoClear = false
    bloomComposer.render()
    renderer!.autoClear = prevAutoClear

    if (bloomEnabled.value) {
      restoreOriginalMaterials()
    }
  }

  function isNodeInHierarchy(nodeId: string): boolean {
    if (!selectedNode.value || !currentLayout) return true
    const selId = selectedNode.value.id
    if (nodeId === selId) return true

    // Check direct connections
    return currentLayout.links.some(
      (l) =>
        (l.source.id === selId && l.target.id === nodeId) ||
        (l.target.id === selId && l.source.id === nodeId),
    )
  }

  // ── Public API ──

  function setLayout(layout: GraphLayout | null) {
    if (layout) {
      buildScene(layout)
    }
  }

  function selectNode(node: Node3D | null) {
    selectedNode.value = node
  }

  // ── Reactively update community boundaries on selection/mode change ──
  watch([selectedNode, communityMode], () => {
    if (currentLayout) {
      updateCommunityBoundaries(
        currentLayout.communities,
        selectedNode.value?.community,
        communityMode.value,
      )
      cacheNeedsRebuild = true
    }
  })

  // ── Toggle bloom pass at runtime ──
  watch(bloomEnabled, (enabled) => {
    if (bloomPass) bloomPass.enabled = enabled
  })

  return {
    // State
    sceneContainer,
    loaded,
    loading,
    selectedNode,
    hoveredNode,
    searchTerm,
    communityMode,
    showCommunityBoundaries,
    autoOrbit,
    bloomEnabled,

    // Methods
    init,
    dispose,
    setLayout,
    selectNode,
  }
}

function getLevelLabel(level: number): string {
  const levelMap: Record<number, string> = {
    0: 'Sector',
    1: 'System',
    2: 'Subsystem',
    3: 'Component',
    4: 'Element',
  }
  return levelMap[level] || `L${level}`
}
