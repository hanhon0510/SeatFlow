export type ReservationStatus =
  | 'PENDING_PAYMENT'
  | 'CONFIRMED'
  | 'PAYMENT_FAILED'
  | 'EXPIRED'
  | 'CANCELLED'

export type ReservationItem = {
  id: string
  eventSeatId: string
  price: number
  createdAt: string
}

export type ReservationResponse = {
  id: string
  userId: string
  eventId: string
  holdId: string
  status: ReservationStatus
  expiresAt: string
  totalAmount: number
  items: ReservationItem[]
  createdAt: string
  updatedAt: string
}

export type OrderStatus = 'PENDING' | 'PAID' | 'FAILED' | 'CANCELLED'

export type OrderResponse = {
  id: string
  reservationId: string
  userId: string
  status: OrderStatus
  totalAmount: number
  currency: string
  createdAt: string
  updatedAt: string
}

export type PaymentStatus = 'PENDING' | 'SUCCEEDED' | 'DECLINED' | 'TIMED_OUT' | 'FAILED'

export type PaymentToken = 'tok_success' | 'tok_declined' | 'tok_timeout'

export type PaymentResponse = {
  id: string
  orderId: string
  status: PaymentStatus
  amount: number
  providerReference: string
  failureReason?: string | null
  createdAt: string
  updatedAt: string
}

export type CheckoutPaymentResult = {
  holdId: string
  reservation: ReservationResponse
  order: OrderResponse
  payment: PaymentResponse
  completedAt: string
}
