import { apiClient } from '../../../../shared/api/httpClient'
import type {
  PromoCodeGenerationRequest,
  PromoCodeGenerationResponse,
} from '../types'

export async function generatePromoCode(request: PromoCodeGenerationRequest) {
  const response = await apiClient.post<PromoCodeGenerationResponse>(
    '/admin/promo-codes/generate',
    request,
  )
  return response.data
}
