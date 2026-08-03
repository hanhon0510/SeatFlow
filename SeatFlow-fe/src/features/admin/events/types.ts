import type { Dayjs } from 'dayjs'

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
