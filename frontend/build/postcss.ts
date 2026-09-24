import type { CSSOptions } from 'vite'
import autoprefixer from 'autoprefixer'
import pxtorem from 'postcss-pxtorem'

export function createPostcssConfig(): CSSOptions['postcss'] {
  return {
    plugins: [
      autoprefixer(),
      pxtorem({
        rootValue: 16,
        unitPrecision: 5,
        propList: ['*', '!border*'],
        selectorBlackList: [],
        replace: true,
        mediaQuery: false,
        minPixelValue: 0,
        exclude: 'node_modules',
      }),
    ],
  }
}
