import { apiClient } from '../../../shared/api/httpClient'
import type {
  OrderResponse,
  PaymentResponse,
  PaymentToken,
  ReservationResponse,
} from '../types'

export async function createReservation(holdId: string) {
  const response = await apiClient.post<ReservationResponse>('/reservations', { holdId })
  return response.data
}

export async function createOrder(reservationId: string) {
  const response = await apiClient.post<OrderResponse>('/orders', { reservationId })
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
