export type AnalyticsCurrencyTotal = {
  currency: string
  orders: number
  seats: number
  gross: number
  refunded: number
  refundedOrders: number
}

export type AnalyticsDailyPoint = {
  date: string
  currency: string
  orders: number
  seats: number
  gross: number
  refunded: number
}

export type AnalyticsEventTotal = {
  eventId: string
  eventName: string
  currency: string
  orders: number
  seats: number
  gross: number
  refunded: number
}

export type AnalyticsCheckout = {
  started: number
  paid: number
  pending: number
  expired: number
  failed: number
  cancelled: number
}

export type AnalyticsWaitlist = {
  joined: number
  offered: number
  fulfilled: number
  expired: number
  cancelled: number
}

export type AnalyticsReport = {
  from: string
  to: string
  eventId: string | null
  totals: AnalyticsCurrencyTotal[]
  daily: AnalyticsDailyPoint[]
  topEvents: AnalyticsEventTotal[]
  checkout: AnalyticsCheckout
  waitlist: AnalyticsWaitlist
  generatedAt: string
}

export type AnalyticsQuery = {
  from: string
  to: string
  eventId?: string
}
