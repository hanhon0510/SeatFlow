export type VenueStatus = 'ACTIVE' | 'ARCHIVED'

export type Venue = {
  id: string
  name: string
  address: string
  city: string
  country: string
  timezone: string
  status: VenueStatus
  createdAt: string
  updatedAt: string
}

export type VenuePage = {
  items: Venue[]
  page: number
  size: number
  totalItems: number
  totalPages: number
}

export type VenueFormValues = {
  name: string
  address: string
  city: string
  country: string
  timezone: string
}

export type VenueSection = {
  id: string
  venueId: string
  name: string
  displayOrder: number
  createdAt: string
}

export type Seat = {
  id: string
  sectionId: string
  rowLabel: string
  seatNumber: number
  seatLabel: string
  accessible: boolean
  createdAt: string
}

export type SeatLayoutSection = VenueSection & {
  seats: Seat[]
}

export type SeatLayout = {
  venueId: string
  sections: SeatLayoutSection[]
}

export type SectionFormValues = {
  name: string
  displayOrder: number
}

export type BulkSeatFormValues = {
  rowLabel: string
  startNumber: number
  quantity: number
  accessible: boolean
}

export type SeatCreateRequest = {
  rowLabel: string
  seatNumber: number
  seatLabel: string
  accessible: boolean
}
