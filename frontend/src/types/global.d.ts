import type { MessageInstance } from 'antdv-next/dist/message/interface'
import type { HookAPI } from 'antdv-next/dist/modal/useModal/types'
import type { NotificationInstance } from 'antdv-next/dist/notification/interface'

declare global {
  interface Window {
    $message: MessageInstance
    $notification: NotificationInstance
    $modal: HookAPI
  }
}

export {}
