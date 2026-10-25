export type WaitlistState = 'NONE' | 'WAITING' | 'OFFERED'

/** Where the signed-in buyer stands on one event's waitlist. */
export type WaitlistStatus = {
  eventId: string
  state: WaitlistState
  entryId: string | null
  seatCount: number | null
  /** 1 for the next in line; only while waiting. */
  position: number | null
  queueLength: number
  /** When the offered seats go back on sale; only while offered. */
  offerExpiresAt: string | null
  /** The seats kept for the buyer; only while offered. */
  eventSeatIds: string[]
  /** Whether the buyer could join right now: the event is on sale and has no free seats. */
  joinable: boolean
}
