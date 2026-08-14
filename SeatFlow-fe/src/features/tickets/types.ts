export type TicketStatus = 'ACTIVE' | 'USED' | 'CANCELLED'

export type TicketEvent = {
  id: string
  name: string
  startTime: string
  venueId: string
  venueName: string
  venueAddress: string
  venueCity: string
  venueCountry: string
  venueTimezone: string
}

export type TicketSeat = {
  id: string
  sectionName: string
  rowLabel: string
  seatNumber: number
  seatLabel: string
  accessible: boolean
  price: number
}

export type Ticket = {
  id: string
  orderId: string
  eventSeatId: string
  ticketCode: string
  status: TicketStatus
  issuedAt: string
  usedAt: string | null
  createdAt: string
  event: TicketEvent
  seat: TicketSeat
  qrData: string
}
