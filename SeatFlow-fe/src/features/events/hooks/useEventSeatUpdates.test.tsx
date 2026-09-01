import type { ReactNode } from 'react'
import { act, renderHook } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { publicEventQueryKeys } from '../api/eventsApi'
import type { EventSeatLayout } from '../types'
import type { SeatStateUpdateMessage } from '../live/seatStateUpdates'
import type { SeatUpdateConnectionOptions } from '../live/seatUpdateConnection'
import { useEventSeatUpdates } from './useEventSeatUpdates'

const emit = vi.hoisted(() => ({ current: undefined as ((message: SeatStateUpdateMessage) => void) | undefined }))

vi.mock('../live/seatUpdateConnection', () => ({
  createSeatUpdateConnection: (options: SeatUpdateConnectionOptions) => {
    emit.current = options.onMessage
    return { deactivate: () => Promise.resolve() }
  },
}))

const eventId = '8fb3eb5f-9a73-45d8-8494-ffb98a3137d2'
const eventSeatIdA1 = '8a58df81-409e-4f2d-bf7b-2270c35b9087'
const eventSeatIdA2 = '868af2d5-42c2-4ea4-8406-87137214ca2a'

describe('useEventSeatUpdates', () => {
  beforeEach(() => {
    emit.current = undefined
  })

  it('keeps the visitor selection and stays quiet when their own hold is broadcast', () => {
    const setup = renderSeatUpdates([eventSeatIdA1])

    act(() => {
      setup.result.current.claimSeatsHeldByYou([eventSeatIdA1])
      setup.emit('SEATS_HELD', [eventSeatIdA1])
    })

    expect(setup.notification.warning).not.toHaveBeenCalled()
    expect(setup.notification.info).not.toHaveBeenCalled()
    expect(setup.onSelectionInvalidated).not.toHaveBeenCalled()
    expect(setup.setSelectedSeatIds).not.toHaveBeenCalled()
    expect(setup.seatStatus(eventSeatIdA1)).toBe('HELD_BY_YOU')
  })

  it('drops the selection when another visitor holds the seat', () => {
    const setup = renderSeatUpdates([eventSeatIdA1])

    act(() => {
      setup.emit('SEATS_HELD', [eventSeatIdA1])
    })

    expect(setup.notification.warning).toHaveBeenCalledWith({
      title: 'Selection updated',
      description: 'Unavailable seats were removed from your selection.',
    })
    expect(setup.onSelectionInvalidated).toHaveBeenCalledOnce()
    expect(setup.seatStatus(eventSeatIdA1)).toBe('HELD')
  })

  it('reports a hold that also covers seats the visitor does not own', () => {
    const setup = renderSeatUpdates([])

    act(() => {
      setup.result.current.claimSeatsHeldByYou([eventSeatIdA1])
      setup.emit('SEATS_HELD', [eventSeatIdA1, eventSeatIdA2])
    })

    expect(setup.notification.info).toHaveBeenCalledWith({
      title: 'Seats held',
      description: 'The seat map was updated.',
    })
    expect(setup.seatStatus(eventSeatIdA1)).toBe('HELD_BY_YOU')
    expect(setup.seatStatus(eventSeatIdA2)).toBe('HELD')
  })
})

function renderSeatUpdates(selectedSeatIds: string[]) {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  })
  const queryKey = publicEventQueryKeys.seatLayout(eventId)
  queryClient.setQueryData(queryKey, layout())

  const notification = {
    info: vi.fn(),
    success: vi.fn(),
    warning: vi.fn(),
    error: vi.fn(),
  }
  const setSelectedSeatIds = vi.fn()
  const onSelectionInvalidated = vi.fn()

  const wrapper = ({ children }: { children: ReactNode }) => (
    <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>
  )
  const result = renderHook(
    () =>
      useEventSeatUpdates({
        eventId,
        selectedSeatIds,
        setSelectedSeatIds,
        notification,
        onSelectionInvalidated,
      }),
    { wrapper },
  ).result

  return {
    result,
    notification,
    setSelectedSeatIds,
    onSelectionInvalidated,
    emit: (type: SeatStateUpdateMessage['type'], eventSeatIds: string[]) => {
      emit.current?.({ type, eventId, eventSeatIds })
    },
    seatStatus: (eventSeatId: string) =>
      queryClient
        .getQueryData<EventSeatLayout>(queryKey)
        ?.sections.flatMap((section) => section.rows)
        .flatMap((row) => row.seats)
        .find((seat) => seat.eventSeatId === eventSeatId)?.status,
  }
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
                accessible: false,
              },
              {
                eventSeatId: eventSeatIdA2,
                seatLabel: 'A2',
                seatNumber: 2,
                price: 75000,
                permanentStatus: 'AVAILABLE',
                accessible: false,
              },
            ],
          },
        ],
      },
    ],
  }
}
