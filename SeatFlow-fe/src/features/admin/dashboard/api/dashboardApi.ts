import { apiClient } from '../../../../shared/api/httpClient'
import type { AdminDashboard } from '../types'

export const dashboardQueryKeys = {
  all: ['admin', 'dashboard'] as const,
}

export async function getAdminDashboard() {
  const response = await apiClient.get<AdminDashboard>('/admin/dashboard')
  return response.data
}
