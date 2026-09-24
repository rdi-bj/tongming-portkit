/**
 * HTML ELEMENT LIB V0.2.1 (TS)
 * Headless effects (file upload, file download) by driving the DOM the way a user would
 *
 * Notes
 *  - Pure TypeScript, no any / as.
 *  - Every public function has a precise type signature.
 *  - `createElement` infers the returned element type from its generic, keeping property assignment type-safe.
 */

/* -------------------------------------------------------------------------- */
/*                              Generic helpers                               */
/* -------------------------------------------------------------------------- */

/**
 * Create an element with the given tag and copy the properties in `options` (if any) onto it.
 *
 * @param tagName   Tag name, constrained by `HTMLElementTagNameMap`.
 * @param options   Properties to write, typed as `Partial<>` of that element.
 *
 * @returns The created element with its properties applied.
 */
export function createElement<K extends keyof HTMLElementTagNameMap>(
  tagName: K,
  options: Partial<HTMLElementTagNameMap[K]>,
): HTMLElementTagNameMap[K] {
  const element = document.createElement(tagName)
  // Object.assign is checked at compile time: keys must exist on the target element
  Object.assign(element, options)
  return element
}

/* -------------------------------------------------------------------------- */
/*                                Type guards                                 */
/* -------------------------------------------------------------------------- */

/**
 * Tell whether an `EventTarget` is an `HTMLInputElement`.
 *
 * Uses only the standard `instanceof` check, no `as` / `any`.
 */
function isHTMLInputElement(target: EventTarget | null): target is HTMLInputElement {
  return target instanceof HTMLInputElement
}

/* -------------------------------------------------------------------------- */
/*                                File upload                                 */
/* -------------------------------------------------------------------------- */

type UploadCallback = (files: FileList) => void

/**
 * Open the browser file picker and call `next` with the selection.
 *
 * @param next      Callback receiving the `FileList` chosen by the user.
 * @param multiple  Allow selecting several files (default `false`).
 * @param accept    Filter string for `<input accept="…">` (e.g. ".png,.jpg").
 */
export function upload(next: UploadCallback, multiple?: boolean, accept?: string): void {
  const opt: Partial<HTMLInputElement> = {
    type: 'file',
    onchange(this: GlobalEventHandlers, ev: Event) {
      const target = ev.target
      if (isHTMLInputElement(target)) {
        const files = target.files
        if (files) {
          next(files)
        }
      }
    },
  }

  if (multiple) {
    opt.multiple = true
  }
  if (accept) {
    opt.accept = accept
  }

  createElement('input', opt).click()
}

/* -------------------------------------------------------------------------- */
/*                               File download                                */
/* -------------------------------------------------------------------------- */

/**
 * Download `blob` by creating a hidden `<a>`.
 *
 * @param blob      Binary data to download.
 * @param filename  File name to save as (browser default when omitted).
 */
export function download(blob: Blob, filename?: string): void {
  const url = URL.createObjectURL(blob)
  const opt: Partial<HTMLAnchorElement> = {
    href: url,
  }
  if (filename) {
    opt.download = filename
  }
  createElement('a', opt).click()
  URL.revokeObjectURL(url)
}

/* -------------------------------------------------------------------------- */
/*                                 Open link                                  */
/* -------------------------------------------------------------------------- */

/**
 * Open a link in the given target window.
 *
 * @param linkUrl   Full URL to open.
 * @param target    Window/tab target (e.g. `_blank`, `_self`). Defaults to `_self`.
 */
export function openLink(linkUrl: string, target?: string): void {
  const opt: Partial<HTMLAnchorElement> = {
    href: linkUrl,
    target: '_self',
  }
  if (target) {
    opt.target = target
  }
  createElement('a', opt).click()
}
