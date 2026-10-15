import { apiClient } from '../../../../shared/api/httpClient'
import type { RefundRequest, RefundResponse } from '../types'

export async function refundOrder(orderId: string, request: RefundRequest) {
  const response = await apiClient.post<RefundResponse>(`/admin/orders/${orderId}/refund`, request)
  return response.data
}
