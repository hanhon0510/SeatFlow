import type { EventSeatLayout, EventSeatLayoutSeat, EventSeatLayoutStatus } from '../types'

export type SeatStateChangeType = 'SEATS_HELD' | 'SEATS_RELEASED' | 'SEATS_SOLD'

export type SeatStateUpdateMessage = {
  type: SeatStateChangeType
  eventId: string
  eventSeatIds: string[]
  occurredAt?: string
}

export function seatStateDestination(eventId: string) {
  return `/topic/events/${eventId}/seats`
}

export function parseSeatStateUpdate(body: string): SeatStateUpdateMessage | null {
  try {
    const candidate = JSON.parse(body) as Partial<SeatStateUpdateMessage>
    if (!isSeatStateChangeType(candidate.type)) {
      return null
    }
    if (typeof candidate.eventId !== 'string') {
      return null
    }
    if (!Array.isArray(candidate.eventSeatIds) || !candidate.eventSeatIds.every(isString)) {
      return null
    }

    return {
      type: candidate.type,
      eventId: candidate.eventId,
      eventSeatIds: candidate.eventSeatIds,
      occurredAt: typeof candidate.occurredAt === 'string' ? candidate.occurredAt : undefined,
    }
  } catch {
    return null
  }
}

/**
 * @param heldByYouSeatIds seats this browser asked to hold. The broadcast is public and names
 *                         no holder, so a visitor's own hold is only distinguishable from
 *                         someone else's by what this client requested.
 */
export function applySeatStateUpdate(
  layout: EventSeatLayout,
  message: SeatStateUpdateMessage,
  heldByYouSeatIds: ReadonlySet<string> = new Set(),
): EventSeatLayout {
  if (layout.eventId !== message.eventId || message.eventSeatIds.length === 0) {
    return layout
  }

  const targetIds = new Set(message.eventSeatIds)
  let changed = false
  const sections = layout.sections.map((section) => ({
    ...section,
    rows: section.rows.map((row) => ({
      ...row,
      seats: row.seats.map((seat) => {
        if (!targetIds.has(seat.eventSeatId)) {
          return seat
        }

        const nextSeat = seatWithState(
          seat,
          message.type,
          heldByYouSeatIds.has(seat.eventSeatId),
        )
        if (nextSeat !== seat) {
          changed = true
        }
        return nextSeat
      }),
    })),
  }))

  return changed ? { ...layout, sections } : layout
}

export function invalidSelectedSeatIds(layout: EventSeatLayout | undefined, selectedSeatIds: string[]) {
  const seatsById = seatsByEventSeatId(layout)
  return selectedSeatIds.filter((eventSeatId) => {
    const seat = seatsById.get(eventSeatId)
    if (!seat) {
      return true
    }
    // A seat the visitor holds is still theirs to check out, so it is not a lost selection.
    const state = seatState(seat)
    return state !== 'AVAILABLE' && state !== 'HELD_BY_YOU'
  })
}

function seatWithState(
  seat: EventSeatLayoutSeat,
  type: SeatStateChangeType,
  heldByYou: boolean,
): EventSeatLayoutSeat {
  if (type === 'SEATS_HELD') {
    if (seat.permanentStatus !== 'AVAILABLE') {
      return seat
    }
    const status = heldByYou || seat.status === 'HELD_BY_YOU' ? 'HELD_BY_YOU' : 'HELD'
    return seat.status === status ? seat : { ...seat, status }
  }

  if (type === 'SEATS_RELEASED') {
    const status = seat.permanentStatus === 'AVAILABLE'
      ? 'AVAILABLE'
      : seat.permanentStatus
    return seat.status === status ? seat : { ...seat, status }
  }

  if (seat.permanentStatus === 'SOLD' && seat.status === 'SOLD') {
    return seat
  }
  return { ...seat, permanentStatus: 'SOLD', status: 'SOLD' }
}

function seatsByEventSeatId(layout: EventSeatLayout | undefined) {
  const seats = new Map<string, EventSeatLayoutSeat>()
  layout?.sections.forEach((section) => {
    section.rows.forEach((row) => {
      row.seats.forEach((seat) => seats.set(seat.eventSeatId, seat))
    })
  })
  return seats
}

function seatState(seat: EventSeatLayoutSeat): EventSeatLayoutStatus {
  return seat.status ?? seat.permanentStatus
}

function isSeatStateChangeType(type: unknown): type is SeatStateChangeType {
  return type === 'SEATS_HELD' || type === 'SEATS_RELEASED' || type === 'SEATS_SOLD'
}

function isString(value: unknown): value is string {
  return typeof value === 'string'
}
