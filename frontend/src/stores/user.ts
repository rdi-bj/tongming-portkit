import type { UserInfo } from '#/api/user'
import type { AccountLoginForm } from '#/pages/auth'
import { ossLogin } from '@/api/auth'
import { store } from '@/plugins'

export const useUserStore = defineStore('user', () => {
  const token = useLocalStorage<string>(`${import.meta.env.VITE_APP_STORAGE_PREFIX}token`, null)
  const isLogin = computed<boolean>(() => !!token.value)

  const userInfo = ref<UserInfo>({} as UserInfo)
  const permissionList = ref<string[]>([])
  /** OSS user info persistence: this backend has no dedicated user-info endpoint, so cache it at login and restore it after a refresh */
  const ossUserInfo = useLocalStorage<UserInfo | null>(
    `${import.meta.env.VITE_APP_STORAGE_PREFIX}userInfo`,
    null,
  )

  const routes = useRoutesStore()

  // Login (OSS: uses this backend's unified login endpoint, password in plain text)
  async function handleLogin(form: AccountLoginForm) {
    const res = await ossLogin({ loginName: form.username, password: form.password })
    token.value = res.token
    const info: UserInfo = {
      id: res.id,
      userName: res.userName,
      loginName: res.loginName,
      deptName: '',
      email: '',
      phone: '',
      avatar: '',
    }
    userInfo.value = info
    ossUserInfo.value = info
    // Build the menu
    routes.generateMenu()
  }

  // Get user info
  async function getUserInfo() {
    // No dedicated user-info endpoint on this backend: userInfo is cached at login and restored from localStorage after a refresh
    if (ossUserInfo.value) {
      userInfo.value = ossUserInfo.value
    }
  }

  // Get user permissions (returns an empty list for now; wire it up when needed)
  async function getPermissionList() {
    permissionList.value = []
  }

  // Log out
  function handleLogout() {
    token.value = null
    userInfo.value = {} as UserInfo
    permissionList.value = []
    ossUserInfo.value = null
  }

  // Permission check (no permission system yet, always allowed)
  function hasPermission(_code: string | string[]): boolean {
    return true
  }

  return {
    token,
    isLogin,
    userInfo,
    handleLogin,
    getUserInfo,
    getPermissionList,
    handleLogout,
    hasPermission,
  }
})

export const useUserStoreHook = () => useUserStore(store)
