import { onBeforeUnmount, ref } from 'vue'
import { createScanSocket } from '@/lib/scanSocket'

// --- WebSocket message types (the actual backend format) ---

/** Raw message pushed by the backend */
export interface ScanWsRawMessage {
  type: 'FILE_CHANGE' | 'PROGRESS_UPDATE' | 'FINISHED' | (string & {})
  timestamp: number
  /** data has no fixed shape in the new protocol, so use any */
  data: any
  // New protocol fields (FILE_CHANGE)
  fileName?: string
  path?: string
  state?: string
  errorNum?: number | null
  /** Number of AI adaptations */
  acNum?: number
  /** Total issues of this file (>0 means the verification flow ran to the end) */
  totalNum?: number
  /** Issues of this file still to verify (counts down to 0 while VERIFYING) */
  waitNum?: number
  // New protocol fields (PROGRESS_UPDATE)
  progress?: {
    total: number
    completed: number
    totalRow?: number
    startTime?: number
    endTime?: number
    relativePath?: string
    unzipPercent?: number
    progressPercent?: number
    /** Files already verified */
    alreadyVerifyFileCount?: number
    /** Total files to verify */
    totalVerifyFileCount?: number
  }
}

/** Message types this composable knows; anything else is logged for debugging */
const KNOWN_MESSAGE_TYPES = [
  'CONNECTED',
  'FILE_CHANGE',
  'PROGRESS_UPDATE',
  'FINISHED',
  'STATE_CHANGE',
]

/**
 * Scan progress WebSocket of a single task (heartbeat + auto reconnect).
 * The socket lifecycle itself lives in `@/lib/scanSocket`, shared with the project-list composable.
 */
export function useScanWebSocket() {
  const { t } = useI18n()
  const connected = ref(false)
  const latestMessage = ref<ScanWsRawMessage | null>(null)
  const error = ref<string | null>(null)

  const socket = createScanSocket<ScanWsRawMessage>({
    onOpen() {
      connected.value = true
      error.value = null
    },
    onMessage(data) {
      latestMessage.value = data
      if (!KNOWN_MESSAGE_TYPES.includes(data.type)) {
        console.warn('[WS] unknown message:', data)
      }
    },
    onError(reason) {
      error.value =
        reason === 'connect-failed'
          ? t('scan.messages.ws-connect-failed')
          : t('scan.messages.ws-error')
    },
    onClose() {
      connected.value = false
    },
    onReconnecting(attempt, delayMs) {
      console.warn(`[WS] reconnecting in ${delayMs / 1000}s (attempt ${attempt})...`)
      error.value = t('scan.messages.ws-reconnecting', { count: attempt })
    },
  })

  function connect(taskId: string) {
    // The previous socket is closed by open(), so reset the reflected state here
    connected.value = false
    latestMessage.value = null
    socket.open(taskId)
  }

  function disconnect() {
    socket.close()
    connected.value = false
    latestMessage.value = null
    error.value = null
  }

  onBeforeUnmount(disconnect)

  return {
    connected,
    latestMessage,
    error,
    connect,
    disconnect,
  }
}
