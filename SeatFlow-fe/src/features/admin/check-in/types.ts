export type CheckInOutcome =
  | 'CHECKED_IN'
  | 'ALREADY_USED'
  | 'CANCELLED'
  | 'WRONG_EVENT'
  | 'NOT_FOUND'

export type CheckInResult = {
  outcome: CheckInOutcome
  ticketId: string | null
  sectionName: string | null
  rowLabel: string | null
  seatLabel: string | null
  buyerEmail: string | null
  usedAt: string | null
}

export type CheckInEntry = {
  ticketId: string
  sectionName: string
  rowLabel: string
  seatLabel: string
  buyerEmail: string
  checkedInAt: string
}

export type CheckInSummary = {
  eventId: string
  eventName: string
  valid: number
  checkedIn: number
  remaining: number
  recent: CheckInEntry[]
}
