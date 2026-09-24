import scanRequest from '@/utils/scan-request'

/**
 * OSS (this project's own backend) login response: fields come from the backend LoginResponse
 */
export interface OssLoginResponse {
  /** User ID */
  id: string
  /** Login account */
  loginName: string
  /** User name */
  userName: string
  /** User type */
  userType: string
  /** Token */
  token: string
}

/**
 * OSS login: POST /sys/user/login with a plain-text password.
 * Uses the shared API prefix (OSS has a single backend: login, scan and llm live in the same service).
 * @returns Login response (including the token)
 */
export async function ossLogin(params: {
  loginName: string
  password: string
}): Promise<OssLoginResponse> {
  return scanRequest<OssLoginResponse>('/sys/user/login', {
    method: 'POST',
    data: params,
  })
}
