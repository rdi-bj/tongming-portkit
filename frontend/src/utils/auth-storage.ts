/** Storage key for the remembered credentials (like every other storage key, prefixed with VITE_APP_STORAGE_PREFIX) */
const SavedCredentialsKey = `${import.meta.env.VITE_APP_STORAGE_PREFIX}credentials`

/**
 * Get the app's own token (shared with userStore as tongming-portkit-token)
 */
export function getAppToken(): string | null {
  const key = `${import.meta.env.VITE_APP_STORAGE_PREFIX}token`
  return localStorage.getItem(key)
}

/**
 * Structure of the remembered credentials
 */
interface SavedCredentials {
  username: string
  password: string
  rememberMe: boolean
}

/**
 * Save the credentials (remember password)
 */
export function saveCredentials(username: string, password: string, rememberMe: boolean): void {
  if (rememberMe) {
    localStorage.setItem(
      SavedCredentialsKey,
      JSON.stringify({ username, password, rememberMe: true } as SavedCredentials),
    )
  } else {
    localStorage.removeItem(SavedCredentialsKey)
  }
}

/**
 * Read the remembered credentials
 */
export function getSavedCredentials(): SavedCredentials {
  const raw = localStorage.getItem(SavedCredentialsKey)
  if (raw) {
    try {
      return JSON.parse(raw) as SavedCredentials
    } catch {
      // ignore
    }
  }
  return { username: '', password: '', rememberMe: false }
}
