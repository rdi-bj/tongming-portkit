import { presetAntd } from '@antdv-next/unocss'
import { FileSystemIconLoader } from '@iconify/utils/lib/loader/node-loaders'
import {
  defineConfig,
  presetIcons,
  presetTypography,
  presetWind4,
  transformerCompileClass,
  transformerDirectives,
  transformerVariantGroup,
} from 'unocss'
import { presetAnimations } from 'unocss-preset-animations'
import { resolvePath } from './build'

// https://unocss.dev/
export default defineConfig({
  presets: [
    presetWind4({
      dark: 'class',
    }),
    presetIcons({
      prefix: 'i-',
      extraProperties: {
        'display': 'inline-block',
        'vertical-align': 'middle',
        'width': '1rem',
        'height': '1rem',
      },
      collections: {
        'ant-design': async () =>
          import('@iconify-json/ant-design/icons.json').then((res) => res.default),
        'lucide': async () => import('@iconify-json/lucide/icons.json').then((res) => res.default),
        'circle-flags': async () =>
          import('@iconify-json/circle-flags/icons.json').then((res) => res.default),
        'svg': FileSystemIconLoader(resolvePath('src/assets/svg'), (svg) =>
          svg.replace(/#fff/, 'currentColor'),
        ),
      },
    }),
    presetTypography(),
    presetAnimations(),
    presetAntd({
      prefix: 'ant',
    }),
  ],
  transformers: [
    transformerCompileClass({
      classPrefix: ':uno:',
    }),
    transformerDirectives(),
    transformerVariantGroup(),
  ],
  shortcuts: [
    ['flex-c', 'flex justify-center items-center'],
    ['flex-bc', 'flex justify-between items-center'],
    ['flex-col-c', 'flex flex-col justify-center items-center'],
    ['flex-col-bc', 'flex flex-col justify-between items-center'],
  ],
})
