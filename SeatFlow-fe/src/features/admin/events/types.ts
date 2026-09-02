import type { Dayjs } from 'dayjs'

import type { OrderStatus } from '../../checkout/types'
import type { EventSalesStatus } from '../../events/types'

export type EventStatus = 'DRAFT' | 'PUBLISHED' | 'CANCELLED' | 'COMPLETED'

export type Event = {
  id: string
  venueId: string
  name: string
  description: string | null
  startTime: string
  salesStartTime: string
  salesEndTime: string
  status: EventStatus
  createdAt: string
  updatedAt: string
}

export type EventPage = {
  items: Event[]
  page: number
  size: number
  totalItems: number
  totalPages: number
}

export type EventFormValues = {
  venueId: string
  name: string
  description?: string
  startTime: Dayjs
  salesStartTime: Dayjs
  salesEndTime: Dayjs
}

export type EventRequest = {
  venueId: string
  name: string
  description: string | null
  startTime: string
  salesStartTime: string
  salesEndTime: string
}

export type EventSectionConfiguration = {
  eventId: string
  sections: EventSectionPrice[]
}

export type EventSectionPrice = {
  id: string
  eventId: string
  venueSectionId: string
  price: number
  salesEnabled: boolean
  createdAt: string
  updatedAt: string
}

export type EventSectionReplaceRequest = {
  sections: EventSectionPriceRequest[]
}

export type EventSectionPriceRequest = {
  venueSectionId: string
  price: number
  salesEnabled: boolean
}

export type EventPublishResponse = {
  eventId: string
  status: EventStatus
  inventoryCount: number
}

export type EventSalesEvent = {
  id: string
  venueId: string
  venueName: string
  venueTimezone: string
  name: string
  description: string | null
  startTime: string
  salesStartTime: string
  salesEndTime: string
  status: EventStatus
  salesStatus: EventSalesStatus | null
}

export type EventSalesInventory = {
  seatsTotal: number
  seatsAvailable: number
  seatsSold: number
  seatsBlocked: number
  seatsInCheckout: number
  inventoryValue: number
  soldValue: number
}

export type EventSalesRevenue = {
  currency: string
  paidAmount: number
  paidOrders: number
  pendingAmount: number
  pendingOrders: number
}

export type EventSalesOrderCounts = {
  total: number
  paid: number
  pending: number
  failed: number
  cancelled: number
}

export type EventSalesRecentOrder = {
  orderId: string
  buyerEmail: string
  status: OrderStatus
  totalAmount: number
  currency: string
  seatCount: number
  createdAt: string
  updatedAt: string
}

export type EventSalesOrders = {
  counts: EventSalesOrderCounts
  recent: EventSalesRecentOrder[]
}

export type EventSalesTickets = {
  issued: number
  active: number
  used: number
  cancelled: number
}

export type EventSalesSection = {
  venueSectionId: string
  name: string
  price: number
  salesEnabled: boolean
  seatsTotal: number
  seatsAvailable: number
  seatsSold: number
  seatsBlocked: number
  soldValue: number
}

export type EventSalesHeatmapRow = {
  rowLabel: string
  seatsTotal: number
  seatsAvailable: number
  seatsSold: number
  seatsBlocked: number
}

export type EventSalesHeatmapSection = {
  sectionId: string
  name: string
  rows: EventSalesHeatmapRow[]
}

export type EventSalesDailyPoint = {
  date: string
  paidOrders: number
  seatsSold: number
}

export type EventSalesReport = {
  event: EventSalesEvent
  inventory: EventSalesInventory
  revenue: EventSalesRevenue[]
  orders: EventSalesOrders
  tickets: EventSalesTickets
  sections: EventSalesSection[]
  heatmap: EventSalesHeatmapSection[]
  dailySales: EventSalesDailyPoint[]
  generatedAt: string
}
