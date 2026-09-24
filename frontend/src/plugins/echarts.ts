import type { BarSeriesOption } from 'echarts'
import type { LineSeriesOption, PieSeriesOption } from 'echarts/charts'
import type {
  DatasetComponentOption,
  GridComponentOption,
  LegendComponentOption,
  TooltipComponentOption,
} from 'echarts/components'
import type { ComposeOption } from 'echarts/core'
import { BarChart, LineChart, PieChart } from 'echarts/charts'
import {
  DatasetComponent,
  GridComponent,
  LegendComponent,
  TooltipComponent,
} from 'echarts/components'
import * as echarts from 'echarts/core'
import { LabelLayout, UniversalTransition } from 'echarts/features'
import { CanvasRenderer } from 'echarts/renderers'

echarts.use([
  // Components
  TooltipComponent,
  GridComponent,
  DatasetComponent,
  LegendComponent,
  // Charts
  LineChart,
  BarChart,
  PieChart,
  // Features
  LabelLayout,
  UniversalTransition,
  // Renderers
  CanvasRenderer,
])

export type EChartsOption = ComposeOption<
  | LineSeriesOption
  | BarSeriesOption
  | PieSeriesOption
  | TooltipComponentOption
  | GridComponentOption
  | DatasetComponentOption
  | LegendComponentOption
>
export type { EChartsType } from 'echarts/core'

export default echarts
