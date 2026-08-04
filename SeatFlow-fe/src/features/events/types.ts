export type PublicEvent = {
  id: string
  venueId: string
  venueName: string
  venueAddress: string
  venueCity: string
  venueCountry: string
  venueTimezone: string
  name: string
  description: string | null
  startTime: string
  salesStartTime: string
  salesEndTime: string
  minimumPrice: number | null
}

export type PublicEventPage = {
  items: PublicEvent[]
  page: number
  size: number
  totalItems: number
  totalPages: number
}

export type EventCatalogFilters = {
  search?: string
  venueId?: string
  startDate?: string
  endDate?: string
  page: number
  size: number
  sort: EventCatalogSort
}

export type EventCatalogSort = 'startAsc' | 'startDesc' | 'priceAsc' | 'priceDesc'
