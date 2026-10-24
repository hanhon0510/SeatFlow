import type { OrderStatus } from '../checkout/types'

export const orderStatusLabel: Record<OrderStatus, string> = {
  PENDING: 'Awaiting payment',
  PAID: 'Paid',
  FAILED: 'Payment failed',
  CANCELLED: 'Cancelled',
  REFUNDED: 'Refunded',
}

export const orderStatusColor: Record<OrderStatus, string> = {
  PENDING: 'gold',
  PAID: 'green',
  FAILED: 'red',
  CANCELLED: 'default',
  REFUNDED: 'purple',
}

export function formatMoney(value: number, currency: string) {
  return `${new Intl.NumberFormat(undefined, { maximumFractionDigits: 2 }).format(value)} ${currency}`
}

/** A short reference the buyer can quote to support: the first block of the order id. */
export function orderReference(orderId: string) {
  return orderId.slice(0, 8).toUpperCase()
}

export function formatDate(value: string, timezone?: string) {
  return new Intl.DateTimeFormat(undefined, { dateStyle: 'medium', timeZone: timezone }).format(new Date(value))
}
