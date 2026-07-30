import { createContext } from 'react'

import type { AuthUser, LoginRequest, RegisterRequest } from '../types'

export type AuthContextValue = {
  accessToken: string | null
  user: AuthUser | null
  isAuthenticated: boolean
  isRestoring: boolean
  isLoadingUser: boolean
  login: (request: LoginRequest) => Promise<void>
  register: (request: RegisterRequest) => Promise<void>
  logout: () => Promise<void>
}

export const AuthContext = createContext<AuthContextValue | null>(null)
