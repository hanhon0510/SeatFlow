import { apiClient } from '../../../shared/api/httpClient'

export type ChangePasswordRequest = {
  currentPassword: string
  newPassword: string
}

export async function changePassword(request: ChangePasswordRequest) {
  await apiClient.post('/users/me/password', request)
}
