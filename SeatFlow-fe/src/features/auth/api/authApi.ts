import { apiClient } from '../../../shared/api/httpClient'
import type {
  AuthUser,
  LoginRequest,
  LoginResponse,
  RegisterRequest,
  RegisterResponse,
} from '../types'

export async function loginUser(request: LoginRequest) {
  const response = await apiClient.post<LoginResponse>('/auth/login', request, {
    skipAuthRefresh: true,
  })
  return response.data
}

export async function registerUser(request: RegisterRequest) {
  const response = await apiClient.post<RegisterResponse>('/auth/register', request, {
    skipAuthRefresh: true,
  })
  return response.data
}

export async function logoutUser() {
  await apiClient.post('/auth/logout', undefined, {
    skipAuthRefresh: true,
  })
}

export async function getCurrentUser() {
  const response = await apiClient.get<AuthUser>('/users/me')
  return response.data
}
