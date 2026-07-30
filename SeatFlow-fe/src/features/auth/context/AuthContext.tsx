import {
  useCallback,
  useEffect,
  useMemo,
  useState,
  type PropsWithChildren,
} from 'react'
import { App as AntdApp } from 'antd'
import { useQuery, useQueryClient } from '@tanstack/react-query'

import {
  clearAccessToken,
  getAccessToken,
  refreshAccessToken,
  setAccessToken,
  setAccessTokenListener,
  setSessionExpiredListener,
} from '../../../shared/api/httpClient'
import { getCurrentUser, loginUser, logoutUser, registerUser } from '../api/authApi'
import type { LoginRequest, RegisterRequest } from '../types'
import { AuthContext, type AuthContextValue } from './AuthContextValue'

const currentUserQueryKey = ['auth', 'current-user']

export function AuthProvider({ children }: PropsWithChildren) {
  const [token, setToken] = useState<string | null>(() => getAccessToken())
  const [isRestoring, setIsRestoring] = useState(true)
  const queryClient = useQueryClient()
  const { notification } = AntdApp.useApp()

  useEffect(() => {
    setAccessTokenListener(setToken)
    setSessionExpiredListener(() => {
      queryClient.removeQueries({ queryKey: currentUserQueryKey })
      notification.warning({
        title: 'Session expired',
        description: 'Please log in again.',
      })
    })

    return () => {
      setAccessTokenListener(null)
      setSessionExpiredListener(null)
    }
  }, [notification, queryClient])

  useEffect(() => {
    let active = true

    refreshAccessToken()
      .then((restoredToken) => {
        if (active) {
          setToken(restoredToken)
        }
      })
      .finally(() => {
        if (active) {
          setIsRestoring(false)
        }
      })

    return () => {
      active = false
    }
  }, [])

  const currentUserQuery = useQuery({
    queryKey: currentUserQueryKey,
    queryFn: getCurrentUser,
    enabled: Boolean(token) && !isRestoring,
    retry: false,
  })

  const login = useCallback(
    async (request: LoginRequest) => {
      const response = await loginUser(request)
      setAccessToken(response.accessToken)
      await queryClient.invalidateQueries({ queryKey: currentUserQueryKey })
    },
    [queryClient],
  )

  const register = useCallback(async (request: RegisterRequest) => {
    await registerUser(request)
  }, [])

  const logout = useCallback(async () => {
    try {
      await logoutUser()
    } catch {
      // Logging out locally is still correct if the server-side session has already expired.
    } finally {
      clearAccessToken()
      queryClient.removeQueries({ queryKey: currentUserQueryKey })
      notification.success({ title: 'Logged out' })
    }
  }, [notification, queryClient])

  const value = useMemo<AuthContextValue>(
    () => ({
      accessToken: token,
      user: currentUserQuery.data ?? null,
      isAuthenticated: Boolean(token),
      isRestoring,
      isLoadingUser: currentUserQuery.isLoading,
      login,
      register,
      logout,
    }),
    [
      currentUserQuery.data,
      currentUserQuery.isLoading,
      isRestoring,
      login,
      logout,
      register,
      token,
    ],
  )

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}
