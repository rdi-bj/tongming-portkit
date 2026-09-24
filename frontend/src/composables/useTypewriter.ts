import { onBeforeUnmount, ref } from 'vue'

export interface UseTypewriterOptions {
  /** Interval between ticks in ms, default 15 */
  speed?: number
  /** Range of characters emitted per tick [min, max], default [1, 4] */
  chunkSize?: [number, number]
}

export function useTypewriter(options: UseTypewriterOptions = {}) {
  const { speed = 15, chunkSize = [1, 4] } = options
  const [chunkMin, chunkMax] = chunkSize

  const displayed = ref('')
  const isTyping = ref(false)
  let timer: ReturnType<typeof setInterval> | null = null
  let doneCallback: (() => void) | null = null

  function stopTimer() {
    if (timer !== null) {
      clearInterval(timer)
      timer = null
    }
    if (doneCallback) {
      doneCallback()
      doneCallback = null
    }
  }

  // Always clear the timer on unmount to release the text reference held by the closure
  onBeforeUnmount(() => stopTimer())

  /** Start the typewriter from an empty string, emitting characters one by one. Resolves when done */
  async function start(text: string): Promise<void> {
    stopTimer()
    return new Promise((resolve) => {
      doneCallback = resolve
      if (!text) {
        displayed.value = ''
        isTyping.value = false
        resolve()
        doneCallback = null
        return
      }
      isTyping.value = true
      displayed.value = ''
      let i = 0
      timer = setInterval(() => {
        const chunk = chunkMin + Math.floor(Math.random() * (chunkMax - chunkMin + 1))
        i += chunk
        if (i >= text.length) {
          displayed.value = text
          stopTimer()
          isTyping.value = false
          resolve()
          doneCallback = null
        } else {
          displayed.value = text.slice(0, i)
        }
      }, speed)
    })
  }

  /** Show the full text immediately and stop */
  function complete(text: string) {
    stopTimer()
    displayed.value = text
    isTyping.value = false
  }

  /** Stop while keeping the text emitted so far */
  function stop() {
    stopTimer()
    isTyping.value = false
  }

  /** Reset to empty */
  function reset() {
    stopTimer()
    displayed.value = ''
    isTyping.value = false
  }

  return { displayed, isTyping, start, complete, stop, reset }
}
