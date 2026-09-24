// Type declarations for troika-three-text.
// The package ships no types of its own, so only the members this project uses
// are declared here. `Text` extends three's `Mesh` because troika renders text
// as a mesh and this project stores instances in Mesh collections.
//
// NOTE: keep this file a global script (no top-level import/export) — otherwise
// `declare module` degrades into an augmentation and the module stays untyped.

declare module 'troika-three-text' {
  import { Mesh } from 'three'

  export type TextAnchorX = 'left' | 'center' | 'right' | number
  export type TextAnchorY =
    | 'top'
    | 'top-baseline'
    | 'middle'
    | 'bottom-baseline'
    | 'bottom'
    | number
  export type TextAlign = 'left' | 'center' | 'right' | 'justify'

  export class Text extends Mesh {
    constructor()
    /** The text to render */
    text: string
    /** Font URL or built-in font name */
    font: string | null
    fontSize: number
    fontWeight: string | number
    /** Color as a CSS string or a hex number */
    color: string | number
    fillOpacity: number
    outlineWidth: number | string
    outlineColor: string | number
    outlineOpacity: number
    anchorX: TextAnchorX
    anchorY: TextAnchorY
    maxWidth: number
    textAlign: TextAlign
    whiteSpace: 'normal' | 'nowrap'
    /** Schedules the text for re-rendering on the next frame */
    sync(callback?: () => void): void
    /** Releases the text's GPU resources */
    dispose(): void
  }

  export interface PreloadFontOptions {
    font?: string
    characters?: string | string[]
    sdfFontSize?: number
  }

  export function preloadFont(options: PreloadFontOptions, callback?: () => void): void
}
