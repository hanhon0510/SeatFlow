import type { OrderStatus } from '../checkout/types'
import type { TicketStatus } from '../tickets/types'

export type OrderHistorySeat = {
  ticketId: string | null
  ticketStatus: TicketStatus | null
  sectionName: string
  rowLabel: string
  seatLabel: string
}

export type OrderHistoryEvent = {
  id: string
  name: string
  startTime: string
  venueName: string
  venueTimezone: string
  cancelled: boolean
  cancellationReason: string | null
}

export type OrderHistoryRefund = {
  amount: number
  reason: string | null
  refundedAt: string
}

export type OrderHistoryItem = {
  orderId: string
  status: OrderStatus
  totalAmount: number
  discountAmount: number
  promoCode: string | null
  currency: string
  createdAt: string
  updatedAt: string
  event: OrderHistoryEvent
  seats: OrderHistorySeat[]
  refund: OrderHistoryRefund | null
}

export type OrderHistoryPage = {
  items: OrderHistoryItem[]
  page: number
  size: number
  totalItems: number
  totalPages: number
}
