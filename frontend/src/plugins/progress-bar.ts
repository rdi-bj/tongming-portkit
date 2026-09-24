import { BProgress } from '@bprogress/core'
// @ts-expect-error no type
import '@bprogress/core/css'

// https://bprogress.vercel.app/
export const progressBar = BProgress.configure({
  minimum: 0.08,
  maximum: 1,
  template: `<div class="bar"><div class="peg"></div></div>
             <div class="spinner"><div class="spinner-icon"></div></div>
             <div class="indeterminate"><div class="inc"></div><div class="dec"></div></div>`,
  easing: 'linear',
  positionUsing: 'translate',
  speed: 200,
  trickle: true,
  trickleSpeed: 200,
  showSpinner: true,
  indeterminate: false,
  indeterminateSelector: '.indeterminate',
  barSelector: '.bar',
  spinnerSelector: '.spinner',
  parent: 'body',
  direction: 'ltr',
})
