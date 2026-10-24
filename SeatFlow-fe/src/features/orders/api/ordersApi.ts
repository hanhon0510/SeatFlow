import { apiClient } from '../../../shared/api/httpClient'
import type { OrderHistoryPage } from '../types'

export const orderHistoryQueryKeys = {
  all: ['orders', 'history'] as const,
  page: (page: number, size: number) => [...orderHistoryQueryKeys.all, { page, size }] as const,
}

export async function listOrderHistory(page: number, size: number) {
  const response = await apiClient.get<OrderHistoryPage>('/users/me/order-history', {
    params: { page, size },
  })
  return response.data
}
