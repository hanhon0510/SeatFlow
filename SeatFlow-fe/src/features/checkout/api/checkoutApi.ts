import { apiClient } from '../../../shared/api/httpClient'
import type {
  OrderResponse,
  PaymentResponse,
  PaymentToken,
  PromoCodeValidationRequest,
  PromoCodeValidationResponse,
  ReservationResponse,
} from '../types'

export async function createReservation(holdId: string) {
  const response = await apiClient.post<ReservationResponse>('/reservations', { holdId })
  return response.data
}

export async function createOrder(reservationId: string, promoCode?: string) {
  const response = await apiClient.post<OrderResponse>('/orders', { reservationId, promoCode })
  return response.data
}

export async function createPayment(orderId: string, token: PaymentToken, idempotencyKey: string) {
  const response = await apiClient.post<PaymentResponse>(
    `/orders/${orderId}/payments`,
    { token },
    {
      headers: {
        'Idempotency-Key': idempotencyKey,
      },
    },
  )
  return response.data
}

export async function validatePromoCode(request: PromoCodeValidationRequest) {
  const response = await apiClient.post<PromoCodeValidationResponse>('/promo-codes/validate', request)
  return response.data
}

export async function cancelOrder(orderId: string) {
  const response = await apiClient.post<OrderResponse>(`/orders/${orderId}/cancel`)
  return response.data
}
