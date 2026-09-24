/**
 * Shared socket mechanics of the scan WebSocket: URL building, heartbeat and reconnect backoff.
 *
 * `useScanWebSocket` (single task) and `useScanListProgressWs` (one socket per scanning project) used to
 * carry one copy of this logic each. They now keep only their own state shape and delegate the socket
 * lifecycle here, so the protocol behaviour lives in exactly one place:
 * - a PING heartbeat every 15s while the socket is open;
 * - exponential reconnect backoff 1s, 2s, 4s, ... capped at 30s, with no limit on the number of attempts
 *   (deep detection can take tens of minutes, so progress must not stall after a drop);
 * - no reconnect after an intentional `close()` (user pressed stop / component unmounted).
 */

/** Heartbeat interval: 15s */
const HEARTBEAT_INTERVAL = 15_000
/** First reconnect delay: 1s */
const RECONNECT_BASE_MS = 1_000
/** Maximum reconnect delay: 30s */
const RECONNECT_MAX_MS = 30_000

/** Callbacks of one scan socket; every callback is optional */
export interface ScanSocketHandlers<TMessage> {
  /** Socket opened: the backoff counter has already been reset */
  onOpen?: () => void
  /** A parsed message that is not a PONG heartbeat reply */
  onMessage?: (message: TMessage) => void
  /** A WebSocket error event, or the constructor throwing while opening */
  onError?: (reason: 'socket-error' | 'connect-failed') => void
  /** Socket closed for any reason, including an intentional close */
  onClose?: () => void
  /** A reconnect was scheduled after a drop; `attempt` counts from 1 */
  onReconnecting?: (attempt: number, delayMs: number) => void
}

export interface ScanSocket {
  /** Connect to a task: closes any previous socket, resets the backoff and tries immediately */
  open: (taskId: string) => void
  /** Close on purpose: the socket is dropped and no reconnect is scheduled */
  close: () => void
}

/** Same origin and prefix as REST requests: the API prefix is an absolute origin path, without the base path */
function buildScanWsUrl(taskId: string): string {
  const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:'
  const host = window.location.host
  const apiBase = import.meta.env.VITE_API_BASE_PATH.replace(/\/+$/, '')
  return `${protocol}//${host}${apiBase}/ws/scan/${taskId}`
}

export function createScanSocket<TMessage extends { type?: string }>(
  handlers: ScanSocketHandlers<TMessage> = {},
): ScanSocket {
  let socket: WebSocket | null = null
  // taskId of the last open() call, reused when reconnecting
  let taskId: string | null = null
  let heartbeatTimer: ReturnType<typeof setInterval> | null = null
  let reconnectTimer: ReturnType<typeof setTimeout> | null = null
  let reconnectCount = 0
  // Intentional-close flag: it survives reconnects, so it is reset in open() and set in close()
  let intentionalClose = false

  function stopHeartbeat() {
    if (heartbeatTimer !== null) {
      clearInterval(heartbeatTimer)
      heartbeatTimer = null
    }
  }

  function startHeartbeat(ws: WebSocket) {
    stopHeartbeat()
    heartbeatTimer = setInterval(() => {
      if (ws.readyState === WebSocket.OPEN) {
        ws.send(JSON.stringify({ type: 'PING' }))
      }
    }, HEARTBEAT_INTERVAL)
  }

  function cancelReconnect() {
    if (reconnectTimer !== null) {
      clearTimeout(reconnectTimer)
      reconnectTimer = null
    }
  }

  function scheduleReconnect() {
    if (intentionalClose) return
    const delay = Math.min(RECONNECT_BASE_MS * 2 ** reconnectCount, RECONNECT_MAX_MS)
    reconnectCount++
    handlers.onReconnecting?.(reconnectCount, delay)
    reconnectTimer = setTimeout(() => {
      if (!intentionalClose && taskId) doConnect(taskId)
    }, delay)
  }

  /** Drop the current socket without notifying the handlers (the close is not a connection loss) */
  function dropSocket() {
    stopHeartbeat()
    if (socket) {
      socket.onopen = null
      socket.onmessage = null
      socket.onerror = null
      socket.onclose = null
      socket.close()
      socket = null
    }
  }

  function doConnect(id: string) {
    let ws: WebSocket
    try {
      ws = new WebSocket(buildScanWsUrl(id))
    } catch {
      handlers.onError?.('connect-failed')
      // A failed connection may need a reconnect too (e.g. once the network is back)
      scheduleReconnect()
      return
    }

    ws.onopen = () => {
      reconnectCount = 0 // Reset the counter once connected
      handlers.onOpen?.()
      startHeartbeat(ws)
    }

    ws.onmessage = (event: MessageEvent) => {
      try {
        const data = JSON.parse(event.data as string) as TMessage
        // Ignore heartbeat replies (the backend may answer PONG)
        if (data.type === 'PONG') return
        handlers.onMessage?.(data)
      } catch {
        // Not a JSON message, ignore
      }
    }

    ws.onerror = () => {
      handlers.onError?.('socket-error')
    }

    ws.onclose = () => {
      stopHeartbeat()
      if (socket === ws) socket = null
      handlers.onClose?.()
      // Not an intentional close → trigger a reconnect
      if (!intentionalClose) scheduleReconnect()
    }

    socket = ws
  }

  function open(id: string) {
    // Clear the previous intentional-close flag and reconnect state
    intentionalClose = false
    taskId = id
    reconnectCount = 0
    cancelReconnect()
    dropSocket()
    doConnect(id)
  }

  function close() {
    intentionalClose = true
    taskId = null
    reconnectCount = 0
    cancelReconnect()
    dropSocket()
  }

  return { open, close }
}
