import axios, {
  AxiosError,
  type AxiosRequestConfig,
  type AxiosResponse,
  type InternalAxiosRequestConfig,
} from 'axios'

import type { LoginResponse } from '../../features/auth/types'

type RetriableRequestConfig = InternalAxiosRequestConfig & {
  skipAuthRefresh?: boolean
  _retry?: boolean
}

let accessToken: string | null = null
let refreshPromise: Promise<string | null> | null = null
let notifyRefreshFailure = false
let accessTokenListener: ((token: string | null) => void) | null = null
let sessionExpiredListener: (() => void) | null = null

export const apiClient = axios.create({
  baseURL: '/api/v1',
  withCredentials: true,
})

export function getAccessToken() {
  return accessToken
}

export function setAccessToken(token: string | null) {
  accessToken = token
  accessTokenListener?.(token)
}

export function clearAccessToken() {
  setAccessToken(null)
}

export function setAccessTokenListener(listener: ((token: string | null) => void) | null) {
  accessTokenListener = listener
}

export function setSessionExpiredListener(listener: (() => void) | null) {
  sessionExpiredListener = listener
}

export async function refreshAccessToken(options: { notifyOnFailure?: boolean } = {}) {
  if (options.notifyOnFailure) {
    notifyRefreshFailure = true
  }

  if (refreshPromise) {
    return refreshPromise
  }

  refreshPromise = apiClient
    .post<LoginResponse>('/auth/refresh', undefined, {
      skipAuthRefresh: true,
    } as AxiosRequestConfig)
    .then((response) => {
      setAccessToken(response.data.accessToken)
      return response.data.accessToken
    })
    .catch(() => {
      clearAccessToken()
      if (notifyRefreshFailure) {
        sessionExpiredListener?.()
      }
      return null
    })
    .finally(() => {
      refreshPromise = null
      notifyRefreshFailure = false
    })

  return refreshPromise
}

apiClient.interceptors.request.use((config) => {
  if (accessToken) {
    config.headers.Authorization = `Bearer ${accessToken}`
  }

  return config
})

apiClient.interceptors.response.use(
  (response: AxiosResponse) => response,
  async (error: AxiosError) => {
    const config = error.config as RetriableRequestConfig | undefined

    if (
      error.response?.status === 401 &&
      config &&
      !config._retry &&
      !config.skipAuthRefresh
    ) {
      config._retry = true
      const token = await refreshAccessToken({ notifyOnFailure: true })

      if (token) {
        config.headers.Authorization = `Bearer ${token}`
        return apiClient(config)
      }
    }

    return Promise.reject(error)
  },
)

declare module 'axios' {
  export interface AxiosRequestConfig {
    skipAuthRefresh?: boolean
  }
}
