import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import type { EventSeatLayout } from '../../events/types'
import type { WaitlistStatus } from '../types'
import { WaitlistCard } from './WaitlistCard'

const auth = vi.hoisted(() => ({
  value: { isAuthenticated: true, user: { role: 'USER' } } as {
    isAuthenticated: boolean
    user: { role: string } | null
  },
}))
const api = vi.hoisted(() => ({
  getWaitlistStatus: vi.fn(),
  joinWaitlist: vi.fn(),
  leaveWaitlist: vi.fn(),
  getEventSeatLayout: vi.fn(),
}))

vi.mock('../../auth/context/useAuth', () => ({ useAuth: () => auth.value }))
vi.mock('../api/waitlistApi', async (importOriginal) => ({
  ...(await importOriginal<typeof import('../api/waitlistApi')>()),
  getWaitlistStatus: api.getWaitlistStatus,
  joinWaitlist: api.joinWaitlist,
  leaveWaitlist: api.leaveWaitlist,
}))
vi.mock('../../events/api/eventsApi', async (importOriginal) => ({
  ...(await importOriginal<typeof import('../../events/api/eventsApi')>()),
  getEventSeatLayout: api.getEventSeatLayout,
}))

const EVENT_ID = 'event-1'

const layout: EventSeatLayout = {
  eventId: EVENT_ID,
  sections: [
    {
      id: 'section-1',
      name: 'Orchestra',
      displayOrder: 1,
      rows: [
        {
          rowLabel: 'A',
          seats: [
            { eventSeatId: 'seat-3', seatLabel: 'A3', seatNumber: 3, price: 100, permanentStatus: 'AVAILABLE', accessible: false },
            { eventSeatId: 'seat-4', seatLabel: 'A4', seatNumber: 4, price: 100, permanentStatus: 'AVAILABLE', accessible: false },
          ],
        },
      ],
    },
  ],
}

function status(overrides: Partial<WaitlistStatus>): WaitlistStatus {
  return {
    eventId: EVENT_ID,
    state: 'NONE',
    entryId: null,
    seatCount: null,
    position: null,
    queueLength: 0,
    offerExpiresAt: null,
    eventSeatIds: [],
    joinable: false,
    ...overrides,
  }
}

function renderCard(soldOut = true) {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter>
        <WaitlistCard eventId={EVENT_ID} soldOut={soldOut} />
      </MemoryRouter>
    </QueryClientProvider>,
  )
}

describe('WaitlistCard', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    auth.value = { isAuthenticated: true, user: { role: 'USER' } }
    api.getEventSeatLayout.mockResolvedValue(layout)
  })

  it('invites a signed-out visitor to sign in when the event is sold out', () => {
    auth.value = { isAuthenticated: false, user: null }

    renderCard(true)

    expect(screen.getByText(/sign in to join the waitlist/i)).toBeInTheDocument()
    expect(api.getWaitlistStatus).not.toHaveBeenCalled()
  })

  it('says nothing to a signed-out visitor while seats are still on sale', () => {
    auth.value = { isAuthenticated: false, user: null }

    const { container } = renderCard(false)

    expect(container).toBeEmptyDOMElement()
  })

  it('says nothing to an admin', () => {
    auth.value = { isAuthenticated: true, user: { role: 'ADMIN' } }

    const { container } = renderCard()

    expect(container).toBeEmptyDOMElement()
    expect(api.getWaitlistStatus).not.toHaveBeenCalled()
  })

  it('says nothing when the buyer could simply book a seat', async () => {
    api.getWaitlistStatus.mockResolvedValue(status({ joinable: false }))

    const { container } = renderCard()

    await waitFor(() => expect(api.getWaitlistStatus).toHaveBeenCalled())
    expect(container).toBeEmptyDOMElement()
  })

  it('lets a buyer join with the number of seats they need', async () => {
    api.getWaitlistStatus.mockResolvedValue(status({ joinable: true }))
    api.joinWaitlist.mockResolvedValue(
      status({ state: 'WAITING', entryId: 'entry-1', seatCount: 3, position: 2, queueLength: 2 }),
    )
    const user = userEvent.setup()
    renderCard()

    const input = await screen.findByLabelText('Seats you need')
    await user.clear(input)
    await user.type(input, '3')
    await user.click(screen.getByRole('button', { name: /join waitlist/i }))

    await waitFor(() => expect(api.joinWaitlist).toHaveBeenCalledWith(EVENT_ID, 3))
    expect(await screen.findByText('You are on the waitlist')).toBeInTheDocument()
    expect(screen.getByText(/of 2 waiting for/)).toBeInTheDocument()
  })

  it('shows the place in line and lets the buyer leave', async () => {
    api.getWaitlistStatus.mockResolvedValue(
      status({ state: 'WAITING', entryId: 'entry-1', seatCount: 1, position: 4, queueLength: 9 }),
    )
    api.leaveWaitlist.mockResolvedValue(undefined)
    const user = userEvent.setup()
    renderCard()

    expect(await screen.findByText(/of 9 waiting for/)).toBeInTheDocument()
    api.getWaitlistStatus.mockResolvedValue(status({ joinable: true }))
    await user.click(screen.getByRole('button', { name: /leave waitlist/i }))

    await waitFor(() => expect(api.leaveWaitlist).toHaveBeenCalledWith(EVENT_ID))
    expect(await screen.findByText('Join the waitlist')).toBeInTheDocument()
  })

  it('names the seats kept for the buyer and counts down', async () => {
    api.getWaitlistStatus.mockResolvedValue(
      status({
        state: 'OFFERED',
        entryId: 'entry-1',
        seatCount: 2,
        queueLength: 1,
        offerExpiresAt: new Date(Date.now() + 5 * 60_000).toISOString(),
        eventSeatIds: ['seat-3', 'seat-4'],
      }),
    )

    renderCard()

    expect(await screen.findByText('Seats are waiting for you')).toBeInTheDocument()
    expect(screen.getByText(/We are keeping 2 seats for you for [45]:\d\d\./)).toBeInTheDocument()
    expect(await screen.findByText(/Orchestra A3, Orchestra A4/)).toBeInTheDocument()
    expect(screen.getByRole('button', { name: /give up these seats/i })).toBeInTheDocument()
  })

  it('explains a refusal to join', async () => {
    api.getWaitlistStatus.mockResolvedValue(status({ joinable: true }))
    api.joinWaitlist.mockRejectedValue(new Error('boom'))
    const user = userEvent.setup()
    renderCard()

    await user.click(await screen.findByRole('button', { name: /join waitlist/i }))

    expect(await screen.findByText(/could not join the waitlist/i)).toBeInTheDocument()
  })
})
