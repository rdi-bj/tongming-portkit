import type { ECharts } from 'echarts/core'
import type { EChartsOption } from '@/plugins'
import { debounce, toMerged } from 'es-toolkit'
import echarts from '@/plugins/echarts'

export interface UseEChartsOptions {
  /**
   * ECharts init options
   */
  initOptions?: echarts.EChartsInitOpts
  /**
   * Whether to resize automatically
   */
  autoResize?: boolean
  /**
   * Whether animations are enabled
   */
  animation?: boolean
  /**
   * Debounce for resize
   */
  resizeDebounce?: number
  /**
   * Chart option
   */
  chartOptions?: EChartsOption
}

export function useECharts(domRef: Ref<HTMLElement | null>, options?: UseEChartsOptions) {
  const {
    initOptions,
    autoResize = true,
    animation = true,
    resizeDebounce = 300,
    chartOptions = {},
  } = options ?? {}

  let chartInst: ECharts | null
  const appStore = useAppStoreHook()

  // Init
  function init() {
    if (!domRef.value) {
      console.error('[useECharts] domRef is null')
      return
    }
    chartInst = echarts.init(domRef.value, appStore.isDark ? 'dark' : undefined, initOptions)
    if (chartOptions) {
      void nextTick(() => {
        setOption(chartOptions)
      })
    }
  }

  // Apply the option
  function setOption(options: EChartsOption, notMerge?: boolean, lazyUpdate?: boolean) {
    if (!chartInst) {
      console.error('[useECharts] chartInst is not initialized')
      return
    }
    void nextTick(() => {
      // @ts-expect-error initialized
      chartInst.setOption(
        toMerged(options, {
          backgroundColor: 'transparent',
        }),
        {
          notMerge,
          lazyUpdate,
          silent: !animation,
        },
      )
    })
  }

  // Resize handler
  const resize = debounce(
    () => {
      if (!chartInst) {
        return
      }
      chartInst.resize({
        animation: animation
          ? {
              duration: 300,
            }
          : undefined,
      })
    },
    resizeDebounce,
    { edges: ['leading', 'trailing'] },
  )

  // Auto resize
  if (autoResize) {
    useResizeObserver(domRef, resize)
  }

  // Dispose the instance
  function destroy() {
    if (chartInst) {
      chartInst?.dispose()
      chartInst = null
    }
  }

  onMounted(() => {
    init()
  })

  onUnmounted(() => {
    destroy()
  })

  watch(
    () => appStore.isDark,
    () => {
      destroy()
      void nextTick(() => {
        init()
      })
    },
  )

  return {
    init,
    // @ts-expect-error initialized
    chartInst,
    setOption,
    destroy,
    resize,
  }
}
