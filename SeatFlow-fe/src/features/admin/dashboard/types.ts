import type { EventStatus } from '../events/types'

export type DashboardVenues = {
  total: number
  active: number
  archived: number
  sections: number
  seats: number
}

export type DashboardEvents = {
  total: number
  draft: number
  published: number
  cancelled: number
  completed: number
  onSaleNow: number
  startingSoon: number
}

export type DashboardRevenue = {
  currency: string
  amount: number
  orderCount: number
}

export type DashboardSales = {
  paidOrders: number
  pendingOrders: number
  ticketsIssued: number
  ticketsUsed: number
  revenue: DashboardRevenue[]
}

export type DashboardUpcomingEvent = {
  eventId: string
  name: string
  venueName: string
  startTime: string
  salesEndTime: string
  status: EventStatus
  seatsTotal: number
  seatsSold: number
}

export type AdminDashboard = {
  venues: DashboardVenues
  events: DashboardEvents
  sales: DashboardSales
  upcomingEvents: DashboardUpcomingEvent[]
  generatedAt: string
}
