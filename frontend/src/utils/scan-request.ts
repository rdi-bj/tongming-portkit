import type { HttpResponse } from '#/api/response'
import { createAlova } from 'alova'
import adapterFetch from 'alova/fetch'
import { i18n } from '@/plugins/i18n'
import { getAppToken } from '@/utils/auth-storage'

const scanAlova = createAlova({
  requestAdapter: adapterFetch(),
  beforeRequest: (method) => {
    const token = getAppToken()
    if (token) {
      method.config.headers = {
        ...method.config.headers,
        Authorization: token,
      }
    }
  },
  responded: async (response) => {
    let payload: unknown = null
    try {
      payload = await response.json()
    } catch {
      try {
        payload = await response.text()
      } catch {
        /* ignore */
      }
    }

    if (!response.ok) {
      let message = i18n.global.t('common.request.failed-with-status', { status: response.status })
      if (typeof payload === 'string' && payload.length < 500) {
        message = payload
      } else if (
        payload &&
        typeof payload === 'object' &&
        'msg' in payload &&
        typeof (payload as { msg: string }).msg === 'string'
      ) {
        message = (payload as { msg: string }).msg
      }
      throw new Error(message)
    }

    const result = payload as Required<HttpResponse> | null

    if (!result || result.code !== 200) {
      throw new Error(result?.msg ?? i18n.global.t('common.request.failed'))
    }

    return result.data
  },
  // The API prefix is an absolute path on the origin (e.g. /tongming-portkit-api) and is independent of
  // the frontend mount path (VITE_APP_BASE_PATH): the API URL never changes. The dev proxy key and the
  baseURL: import.meta.env.VITE_API_BASE_PATH.replace(/\/+$/, ''),
  cacheFor: null,
})

/**
 * Download a binary file (GET). Unlike scanRequest it skips JSON parsing,
 * which suits endpoints returning a file stream (e.g. docx).
 */
export interface DownloadResult {
  blob: Blob
  /** File name from the backend Content-Disposition header, null when absent */
  filename: string | null
}

/** Parse the file name from Content-Disposition, preferring the filename*=UTF-8'' form */
function parseFilenameFromContentDisposition(header: string | null): string | null {
  if (!header) return null
  const starMatch = /filename\*\s*=\s*(?:UTF-8|utf-8)''([^;]+)/.exec(header)
  if (starMatch) {
    try {
      return decodeURIComponent(starMatch[1])
    } catch {
      /* fallthrough to plain filename */
    }
  }
  const plainMatch = /filename\s*=\s*"?([^";]+)"?/.exec(header)
  return plainMatch ? plainMatch[1] : null
}

export async function downloadBlob(
  url: string,
  params?: Record<string, string | number>,
): Promise<DownloadResult> {
  const token = getAppToken()
  const query = params
    ? `?${new URLSearchParams(Object.entries(params).map(([k, v]) => [k, String(v)]))}`
    : ''
  const response = await fetch(`${scanAlova.options.baseURL}${url}${query}`, {
    headers: token ? { Authorization: token } : undefined,
  })

  const contentType = response.headers.get('content-type') ?? ''
  if (!response.ok || contentType.includes('application/json')) {
    let message = i18n.global.t('common.request.failed-with-status', { status: response.status })
    try {
      const result = (await response.json()) as Required<HttpResponse> | null
      if (result?.msg) {
        message = result.msg
      }
    } catch {
      /* ignore */
    }
    throw new Error(message)
  }

  return {
    blob: await response.blob(),
    filename: parseFilenameFromContentDisposition(response.headers.get('content-disposition')),
  }
}

export default async function scanRequest<T = unknown>(
  url: string,
  config: {
    method?: 'GET' | 'POST' | 'PUT' | 'DELETE'
    data?: unknown
    params?: Record<string, unknown>
    headers?: Record<string, string>
  } = {},
): Promise<T> {
  const method = config.method ?? 'GET'

  const extraConfig: { headers?: Record<string, string> } = {}
  if (config.headers) {
    extraConfig.headers = config.headers
  }

  switch (method) {
    case 'GET':
      return scanAlova.Get<T>(url, { params: config.params ?? {}, ...extraConfig })
    case 'POST':
      return scanAlova.Post<T>(url, config.data ?? {}, {
        params: config.params ?? {},
        ...extraConfig,
      })
    case 'PUT':
      return scanAlova.Put<T>(url, config.data ?? {}, {
        params: config.params ?? {},
        ...extraConfig,
      })
    case 'DELETE':
      return scanAlova.Delete<T>(url, config.data ?? {}, {
        params: config.params ?? {},
        ...extraConfig,
      })
    default:
      return scanAlova.Get<T>(url)
  }
}
