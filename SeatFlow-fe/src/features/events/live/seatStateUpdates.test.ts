import { describe, expect, it } from 'vitest'

import type { EventSeatLayout } from '../types'
import {
  applySeatStateUpdate,
  invalidSelectedSeatIds,
  parseSeatStateUpdate,
  seatStateDestination,
  type SeatStateUpdateMessage,
} from './seatStateUpdates'

const eventId = '8fb3eb5f-9a73-45d8-8494-ffb98a3137d2'
const eventSeatIdA1 = '8a58df81-409e-4f2d-bf7b-2270c35b9087'
const eventSeatIdA2 = '868af2d5-42c2-4ea4-8406-87137214ca2a'
const eventSeatIdA3 = 'b4f68b2b-c2db-470d-95ad-0d34290d8a51'

describe('seat state updates', () => {
  it('uses the event-scoped subscription destination', () => {
    expect(seatStateDestination(eventId)).toBe(`/topic/events/${eventId}/seats`)
  })

  it('marks held seats unavailable', () => {
    const updated = applySeatStateUpdate(layout(), message('SEATS_HELD', [eventSeatIdA1]))

    expect(seat(updated, eventSeatIdA1)?.status).toBe('HELD')
    expect(invalidSelectedSeatIds(updated, [eventSeatIdA1])).toEqual([eventSeatIdA1])
  })

  it('marks released seats available again', () => {
    const heldLayout = applySeatStateUpdate(layout(), message('SEATS_HELD', [eventSeatIdA1]))
    const updated = applySeatStateUpdate(heldLayout, message('SEATS_RELEASED', [eventSeatIdA1]))

    expect(seat(updated, eventSeatIdA1)?.status).toBe('AVAILABLE')
    expect(invalidSelectedSeatIds(updated, [eventSeatIdA1])).toEqual([])
  })

  it('marks sold seats permanently unavailable', () => {
    const updated = applySeatStateUpdate(layout(), message('SEATS_SOLD', [eventSeatIdA2]))

    expect(seat(updated, eventSeatIdA2)?.permanentStatus).toBe('SOLD')
    expect(seat(updated, eventSeatIdA2)?.status).toBe('SOLD')
    expect(invalidSelectedSeatIds(updated, [eventSeatIdA2])).toEqual([eventSeatIdA2])
  })

  it('removes only selected seats that became unavailable', () => {
    const updated = applySeatStateUpdate(layout(), message('SEATS_HELD', [eventSeatIdA1]))

    expect(invalidSelectedSeatIds(updated, [eventSeatIdA1, eventSeatIdA2])).toEqual([eventSeatIdA1])
  })

  it('ignores invalid message payloads safely', () => {
    expect(parseSeatStateUpdate('{bad json')).toBeNull()
    expect(parseSeatStateUpdate(JSON.stringify({ type: 'UNKNOWN', eventId, eventSeatIds: [] }))).toBeNull()
    expect(parseSeatStateUpdate(JSON.stringify({ type: 'SEATS_HELD', eventId, eventSeatIds: [1] }))).toBeNull()
  })
})

function message(
  type: SeatStateUpdateMessage['type'],
  eventSeatIds: string[],
): SeatStateUpdateMessage {
  return {
    type,
    eventId,
    eventSeatIds,
    occurredAt: '2026-08-20T00:00:00Z',
  }
}

function seat(layout: EventSeatLayout, eventSeatId: string) {
  return layout.sections
    .flatMap((section) => section.rows)
    .flatMap((row) => row.seats)
    .find((candidate) => candidate.eventSeatId === eventSeatId)
}

function layout(): EventSeatLayout {
  return {
    eventId,
    sections: [
      {
        id: 'f5936746-4e3c-4e50-a64d-a0d45f3d3861',
        name: 'Orchestra',
        displayOrder: 1,
        rows: [
          {
            rowLabel: 'A',
            seats: [
              {
                eventSeatId: eventSeatIdA1,
                seatLabel: 'A1',
                seatNumber: 1,
                price: 50000,
                permanentStatus: 'AVAILABLE',
                accessible: true,
              },
              {
                eventSeatId: eventSeatIdA2,
                seatLabel: 'A2',
                seatNumber: 2,
                price: 75000,
                permanentStatus: 'AVAILABLE',
                accessible: false,
              },
              {
                eventSeatId: eventSeatIdA3,
                seatLabel: 'A3',
                seatNumber: 3,
                price: 50000,
                permanentStatus: 'SOLD',
                status: 'SOLD',
                accessible: false,
              },
            ],
          },
        ],
      },
    ],
  }
}
