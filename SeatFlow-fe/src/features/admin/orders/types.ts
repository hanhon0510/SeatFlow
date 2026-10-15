import type { OrderStatus } from '../../checkout/types'

export type RefundRequest = {
  reason?: string
}

export type RefundResponse = {
  id: string
  orderId: string
  orderStatus: OrderStatus
  amount: number
  currency: string
  reason: string | null
  refundedAt: string
}

export type RefundableOrder = {
  orderId: string
  buyerEmail: string
  totalAmount: number
  currency: string
  seatCount: number
}
