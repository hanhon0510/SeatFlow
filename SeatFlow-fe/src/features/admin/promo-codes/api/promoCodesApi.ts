import { apiClient } from '../../../../shared/api/httpClient'
import type {
  PromoCode,
  PromoCodeCreateRequest,
  PromoCodeGenerationRequest,
  PromoCodeGenerationResponse,
  PromoCodePage,
} from '../types'

export const promoCodeQueryKeys = {
  all: ['admin', 'promo-codes'] as const,
  list: (page: number, size: number) =>
    [...promoCodeQueryKeys.all, 'list', { page, size }] as const,
}

export async function generatePromoCode(request: PromoCodeGenerationRequest) {
  const response = await apiClient.post<PromoCodeGenerationResponse>(
    '/admin/promo-codes/generate',
    request,
  )
  return response.data
}

export async function listPromoCodes(page: number, size: number) {
  const response = await apiClient.get<PromoCodePage>('/admin/promo-codes', {
    params: { page, size },
  })
  return response.data
}

export async function createPromoCode(request: PromoCodeCreateRequest) {
  const response = await apiClient.post<PromoCode>('/admin/promo-codes', request)
  return response.data
}

export async function deactivatePromoCode(promoCodeId: string) {
  const response = await apiClient.post<PromoCode>(`/admin/promo-codes/${promoCodeId}/deactivate`)
  return response.data
}
