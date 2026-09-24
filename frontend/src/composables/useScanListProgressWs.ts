import type { ScanSocket } from '@/lib/scanSocket'
import type { ScanProgress } from '@/types/scan'
import { onBeforeUnmount, ref } from 'vue'
import { createScanSocket } from '@/lib/scanSocket'

/**
 * Progress of the "porting check" projects on the list page: one WebSocket per scanning project
 * (heartbeat + auto reconnect); PROGRESS_UPDATE / STATE_CHANGE / FINISHED messages keep
 * every project's progress up to date instead of polling /cscan/progress over HTTP.
 * The socket lifecycle itself lives in `@/lib/scanSocket`, shared with the single-task composable.
 */

/** Latest state of one task (keyed by taskId) */
export interface ScanListWsState {
  /** Backend stage: SCANNING | AI_VERIFY | SUCCESS | FAILED | '' */
  phase: string
  /** Latest progress snapshot (the progress field of PROGRESS_UPDATE) */
  progress: Partial<ScanProgress> | null
  /** Whether FINISHED has been received */
  finished: boolean
}

/** Messages this composable consumes; the rest of the payload is ignored */
interface ScanListWsMessage {
  type?: string
  state?: string
  progress?: Partial<ScanProgress>
}

export function useScanListProgressWs() {
  /** taskId → latest state (reactive) */
  const states = ref<Record<string, ScanListWsState>>({})
  /** Ids of the tasks currently connected */
  const activeTaskIds = ref<string[]>([])

  const sockets = new Map<string, ScanSocket>()

  function ensureState(taskId: string): ScanListWsState {
    const cur = states.value[taskId]
    if (cur) return cur
    const fresh: ScanListWsState = { phase: '', progress: null, finished: false }
    states.value = { ...states.value, [taskId]: fresh }
    return fresh
  }

  function patchState(taskId: string, patch: Partial<ScanListWsState>) {
    states.value = { ...states.value, [taskId]: { ...ensureState(taskId), ...patch } }
  }

  /** Open (or reuse) the progress connection of a task */
  function connect(taskId: string) {
    if (sockets.has(taskId)) return
    ensureState(taskId)
    const socket = createScanSocket<ScanListWsMessage>({
      onMessage(msg) {
        if (!msg.type) return
        if (msg.type === 'STATE_CHANGE') {
          patchState(taskId, { phase: msg.state ?? '' })
        } else if (msg.type === 'PROGRESS_UPDATE') {
          patchState(taskId, { progress: msg.progress ?? null })
        } else if (msg.type === 'FINISHED') {
          patchState(taskId, { finished: true })
        }
      },
    })
    sockets.set(taskId, socket)
    activeTaskIds.value = [...sockets.keys()]
    socket.open(taskId)
  }

  /** Close a task connection on purpose and clean up its state */
  function disconnect(taskId: string) {
    const socket = sockets.get(taskId)
    if (!socket) return
    socket.close()
    sockets.delete(taskId)
    activeTaskIds.value = [...sockets.keys()]
    const next = { ...states.value }
    delete next[taskId]
    states.value = next
  }

  /** Close every connection (called on unmount) */
  function disconnectAll() {
    for (const taskId of [...sockets.keys()]) disconnect(taskId)
  }

  onBeforeUnmount(disconnectAll)

  return { states, activeTaskIds, connect, disconnect, disconnectAll }
}
