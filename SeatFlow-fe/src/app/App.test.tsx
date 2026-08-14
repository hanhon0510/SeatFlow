import { cleanup, fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { AxiosError } from 'axios'
import type { AxiosResponse, InternalAxiosRequestConfig } from 'axios'
import { describe, expect, it, vi, type Mock } from 'vitest'

import type {
  SeatCreateRequest,
  SeatLayout,
  Venue,
  VenuePage,
} from '../features/admin/venues/types'
import type {
  Event,
  EventPublishResponse,
  EventSectionConfiguration,
} from '../features/admin/events/types'
import type { EventSeatLayout, PublicEvent, PublicEventPage } from '../features/events/types'
import type { AuthUser, LoginResponse, RegisterResponse } from '../features/auth/types'
import type {
  OrderResponse,
  PaymentResponse,
  PaymentStatus,
  ReservationResponse,
} from '../features/checkout/types'
import type { SeatHold } from '../features/holds/types'
import type { Ticket } from '../features/tickets/types'
import { apiClient } from '../shared/api/httpClient'
import { ROUTES } from '../shared/constants/routes'
import App from './App'

type PaymentSpy = Mock<(config: InternalAxiosRequestConfig) => void>

const authUser: AuthUser = {
  id: '77e450c5-a8ee-4880-a74c-c0c6d523d44c',
  email: 'user@example.com',
  role: 'USER',
  status: 'ACTIVE',
  createdAt: '2026-07-27T00:00:00Z',
  updatedAt: '2026-07-27T00:00:00Z',
}

const adminUser: AuthUser = {
  ...authUser,
  id: '0ad1e7e0-a1a6-4b4f-9b4e-8c6c0ac83b3e',
  email: 'admin@example.com',
  role: 'ADMIN',
}

const loginResponse: LoginResponse = {
  accessToken: 'access-token',
  tokenType: 'Bearer',
  expiresAt: '2026-07-27T01:00:00Z',
}

const venue: Venue = {
  id: '6c5d2e2b-1817-46d8-b897-65be94a34706',
  name: 'Concert Hall',
  address: '100 Main Street',
  city: 'New York',
  country: 'United States',
  timezone: 'America/New_York',
  status: 'ACTIVE',
  createdAt: '2026-07-27T00:00:00Z',
  updatedAt: '2026-07-27T00:00:00Z',
}

const emptySeatLayout: SeatLayout = {
  venueId: venue.id,
  sections: [],
}

const event: Event = {
  id: 'f91978bb-a882-46e4-85ef-96d9cf778d83',
  venueId: venue.id,
  name: 'Opening Night',
  description: 'Season opener',
  startTime: '2026-09-01T19:00:00.000Z',
  salesStartTime: '2026-08-01T00:00:00.000Z',
  salesEndTime: '2026-09-01T18:00:00.000Z',
  status: 'DRAFT',
  createdAt: '2026-08-03T00:00:00Z',
  updatedAt: '2026-08-03T00:00:00Z',
}

const publishedEvent: PublicEvent = {
  id: '8fb3eb5f-9a73-45d8-8494-ffb98a3137d2',
  venueId: venue.id,
  venueName: venue.name,
  venueAddress: venue.address,
  venueCity: venue.city,
  venueCountry: venue.country,
  venueTimezone: venue.timezone,
  name: 'Opening Gala',
  description: 'A published concert',
  startTime: '2026-09-01T19:00:00.000Z',
  salesStartTime: '2026-08-01T00:00:00.000Z',
  salesEndTime: '2026-09-01T18:00:00.000Z',
  minimumPrice: 50000,
}

const publicSeatLayout: EventSeatLayout = {
  eventId: publishedEvent.id,
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
              eventSeatId: '8a58df81-409e-4f2d-bf7b-2270c35b9087',
              seatLabel: 'A1',
              seatNumber: 1,
              price: 50000,
              permanentStatus: 'AVAILABLE',
              accessible: true,
            },
            {
              eventSeatId: '868af2d5-42c2-4ea4-8406-87137214ca2a',
              seatLabel: 'A2',
              seatNumber: 2,
              price: 75000,
              permanentStatus: 'AVAILABLE',
              accessible: false,
            },
            {
              eventSeatId: 'b4f68b2b-c2db-470d-95ad-0d34290d8a51',
              seatLabel: 'A3',
              seatNumber: 3,
              price: 50000,
              permanentStatus: 'SOLD',
              accessible: false,
            },
            {
              eventSeatId: 'cf4117c9-0915-4e72-83be-8c331887f28c',
              seatLabel: 'A4',
              seatNumber: 4,
              price: 50000,
              permanentStatus: 'BLOCKED',
              accessible: false,
            },
          ],
        },
      ],
    },
    {
      id: '9839e2e2-4afd-419c-ab78-c7385882ff14',
      name: 'Balcony',
      displayOrder: 2,
      rows: [
        {
          rowLabel: 'B',
          seats: [
            {
              eventSeatId: 'fe2c4c9b-b219-4a59-ab98-8fa24b869c92',
              seatLabel: 'B1',
              seatNumber: 1,
              price: 30000,
              permanentStatus: 'AVAILABLE',
              accessible: false,
            },
          ],
        },
      ],
    },
  ],
}

function activeSeatHold(expiresAt = new Date(Date.now() + 10 * 60 * 1000).toISOString()): SeatHold {
  return {
    holdId: 'b7a60452-f6aa-471f-a60d-151721ce2f98',
    eventId: publishedEvent.id,
    eventSeatId: publicSeatLayout.sections[0].rows[0].seats[0].eventSeatId,
    eventSeatIds: [
      publicSeatLayout.sections[0].rows[0].seats[0].eventSeatId,
      publicSeatLayout.sections[0].rows[0].seats[1].eventSeatId,
    ],
    userId: authUser.id,
    expiresAt,
  }
}

function reservationResponse(hold: SeatHold): ReservationResponse {
  return {
    id: '411f1828-139c-49ec-bf40-a66f40685388',
    userId: authUser.id,
    eventId: hold.eventId,
    holdId: hold.holdId,
    status: 'PENDING_PAYMENT',
    expiresAt: hold.expiresAt,
    totalAmount: 125000,
    items: hold.eventSeatIds.map((eventSeatId, index) => ({
      id: `reservation-item-${index + 1}`,
      eventSeatId,
      price: index === 0 ? 50000 : 75000,
      createdAt: '2026-08-14T00:00:00Z',
    })),
    createdAt: '2026-08-14T00:00:00Z',
    updatedAt: '2026-08-14T00:00:00Z',
  }
}

function orderResponse(reservation: ReservationResponse, status: OrderResponse['status'] = 'PENDING'): OrderResponse {
  return {
    id: 'ccde3f27-e8fb-4af5-b58e-2301c782fe87',
    reservationId: reservation.id,
    userId: authUser.id,
    status,
    totalAmount: 125000,
    currency: 'VND',
    createdAt: '2026-08-14T00:00:00Z',
    updatedAt: '2026-08-14T00:00:00Z',
  }
}

function paymentResponse(order: OrderResponse, status: PaymentStatus): PaymentResponse {
  return {
    id: '33b3c7c1-7f49-479d-95b5-1db9dc1d2210',
    orderId: order.id,
    status,
    amount: order.totalAmount,
    providerReference: `sim_${status.toLowerCase()}`,
    failureReason: status === 'DECLINED'
      ? 'Payment declined'
      : status === 'TIMED_OUT'
        ? 'Payment timed out'
        : null,
    createdAt: '2026-08-14T00:00:00Z',
    updatedAt: '2026-08-14T00:00:00Z',
  }
}

function ticketForOrder(order: OrderResponse, eventSeatId = activeSeatHold().eventSeatIds[0]): Ticket {
  return {
    id: 'f2287e38-c265-4841-b20b-57d2d7ba1b9d',
    orderId: order.id,
    eventSeatId,
    ticketCode: 'ticket_code_abcdefghijklmnopqrstuvwxyz123456',
    status: 'ACTIVE',
    issuedAt: '2026-08-14T00:00:00Z',
    usedAt: null,
    createdAt: '2026-08-14T00:00:00Z',
    event: {
      id: publishedEvent.id,
      name: publishedEvent.name,
      startTime: publishedEvent.startTime,
      venueId: venue.id,
      venueName: venue.name,
      venueAddress: venue.address,
      venueCity: venue.city,
      venueCountry: venue.country,
      venueTimezone: venue.timezone,
    },
    seat: {
      id: '645efc9c-34d5-4b3e-87c4-8712c694d79a',
      sectionName: 'Orchestra',
      rowLabel: 'A',
      seatNumber: 1,
      seatLabel: 'A1',
      accessible: true,
      price: 50000,
    },
    qrData: 'seatflow:ticket:f2287e38-c265-4841-b20b-57d2d7ba1b9d:ticket_code_abcdefghijklmnopqrstuvwxyz123456',
  }
}

const layoutWithSection: SeatLayout = {
  venueId: venue.id,
  sections: [
    {
      id: '1f89f6d1-3bc4-4d46-8c85-24d6db537d87',
      venueId: venue.id,
      name: 'Orchestra',
      displayOrder: 1,
      createdAt: '2026-08-03T00:00:00Z',
      seats: [
        {
          id: '5f3bc98c-981d-4cf6-8cf1-ee48482e13ea',
          sectionId: '1f89f6d1-3bc4-4d46-8c85-24d6db537d87',
          rowLabel: 'A',
          seatNumber: 1,
          seatLabel: 'A1',
          accessible: false,
          createdAt: '2026-08-03T00:00:00Z',
        },
      ],
    },
  ],
}

const emptyEventSections: EventSectionConfiguration = {
  eventId: event.id,
  sections: [],
}

type ApiHandler = (
  config: InternalAxiosRequestConfig,
) => AxiosResponse<unknown> | Promise<AxiosResponse<unknown>>

function installApiMock(handler: ApiHandler) {
  apiClient.defaults.adapter = async (config) => handler(config)
}

function response<T>(
  config: InternalAxiosRequestConfig,
  status: number,
  data: T,
): AxiosResponse<T> {
  return {
    data,
    status,
    statusText: status >= 400 ? 'Error' : 'OK',
    headers: {},
    config,
    request: {},
  }
}

function rejectedResponse(config: InternalAxiosRequestConfig, status: number, message: string) {
  return Promise.reject(
    new AxiosError(message, undefined, config, {}, response(config, status, {
      success: false,
      message,
      data: null,
      timestamp: '2026-07-27T00:00:00Z',
    })),
  )
}

function endpoint(config: InternalAxiosRequestConfig) {
  return `${config.method?.toUpperCase()} ${config.url}`
}

function authorization(config: InternalAxiosRequestConfig) {
  const headers = config.headers as {
    get?: (name: string) => string | undefined
    Authorization?: string
    authorization?: string
  }

  return headers.get?.('Authorization') ?? headers.Authorization ?? headers.authorization
}

function requestBody<T>(config: InternalAxiosRequestConfig) {
  return typeof config.data === 'string' ? (JSON.parse(config.data) as T) : (config.data as T)
}

function venuePage(items: Venue[]): VenuePage {
  return {
    items,
    page: 0,
    size: 10,
    totalItems: items.length,
    totalPages: items.length === 0 ? 0 : 1,
  }
}

function publicEventPage(
  items: PublicEvent[],
  page = 0,
  size = 12,
  totalItems = items.length,
): PublicEventPage {
  return {
    items,
    page,
    size,
    totalItems,
    totalPages: totalItems === 0 ? 0 : Math.ceil(totalItems / size),
  }
}

function publicEventsResponse(config: InternalAxiosRequestConfig, items: PublicEvent[] = []) {
  const params = config.params as { page?: number; size?: number } | undefined
  const page = params?.page ?? 0
  const size = params?.size ?? 12
  return response<PublicEventPage>(config, 200, publicEventPage(items, page, size))
}

function availableSeatLayout(count: number, price = 50000): EventSeatLayout {
  return {
    eventId: publishedEvent.id,
    sections: [
      {
        id: publicSeatLayout.sections[0].id,
        name: 'Orchestra',
        displayOrder: 1,
        rows: [
          {
            rowLabel: 'A',
            seats: Array.from({ length: count }, (_, index) => ({
              eventSeatId: `available-seat-${index + 1}`,
              seatLabel: `A${index + 1}`,
              seatNumber: index + 1,
              price,
              permanentStatus: 'AVAILABLE',
              accessible: false,
            })),
          },
        ],
      },
    ],
  }
}

async function fillDate(user: ReturnType<typeof userEvent.setup>, label: string, value: string) {
  const input = screen.getByLabelText(label)
  await user.click(input)
  await user.clear(input)
  await user.type(input, value)
  fireEvent.keyDown(input, { key: 'Enter', code: 'Enter' })
  fireEvent.blur(input)
}

function installCheckoutMock({
  hold = activeSeatHold(),
  status = 'SUCCEEDED',
  paymentSpy = vi.fn<(config: InternalAxiosRequestConfig) => void>(),
  tickets,
}: {
  hold?: SeatHold
  status?: PaymentStatus
  paymentSpy?: PaymentSpy
  tickets?: Ticket[]
} = {}) {
  const reservation = reservationResponse(hold)
  const order = orderResponse(reservation, status === 'SUCCEEDED' ? 'PAID' : 'FAILED')
  const issuedTickets = tickets ?? (status === 'SUCCEEDED' ? [ticketForOrder(order)] : [])

  installApiMock((config) => {
    if (endpoint(config) === 'POST /auth/refresh') {
      return response<LoginResponse>(config, 200, loginResponse)
    }

    if (endpoint(config) === 'GET /users/me') {
      return response<AuthUser>(config, 200, authUser)
    }

    if (endpoint(config) === `GET /holds/${hold.holdId}`) {
      return response<SeatHold>(config, 200, hold)
    }

    if (endpoint(config) === `GET /events/${publishedEvent.id}`) {
      return response<PublicEvent>(config, 200, publishedEvent)
    }

    if (endpoint(config) === `GET /events/${publishedEvent.id}/seats`) {
      return response<EventSeatLayout>(config, 200, publicSeatLayout)
    }

    if (endpoint(config) === 'POST /reservations') {
      return response<ReservationResponse>(config, 201, reservation)
    }

    if (endpoint(config) === 'POST /orders') {
      return response<OrderResponse>(config, 201, order)
    }

    if (endpoint(config) === `POST /orders/${order.id}/payments`) {
      paymentSpy(config)
      return response<PaymentResponse>(config, 201, paymentResponse(order, status))
    }

    if (endpoint(config) === 'GET /users/me/tickets') {
      return response<Ticket[]>(config, 200, issuedTickets)
    }

    return rejectedResponse(config, 500, 'Unexpected request')
  })

  return { hold, reservation, order, tickets: issuedTickets, paymentSpy }
}

describe('App', () => {
  it('renders the health page', () => {
    render(<App />)

    expect(screen.getByRole('heading', { name: 'SeatFlow frontend is running.' })).toBeInTheDocument()
  })

  it('navigates between public routes', async () => {
    const user = userEvent.setup()
    render(<App />)

    await user.click(screen.getByRole('link', { name: /register/i }))
    expect(screen.getByRole('heading', { name: 'Register' })).toBeInTheDocument()

    await user.click(screen.getByRole('link', { name: /log in/i }))
    expect(screen.getByRole('heading', { name: 'Login' })).toBeInTheDocument()
  })

  it('renders a 404 result for unknown routes', () => {
    window.history.pushState({}, '', '/missing-route')

    render(<App />)

    expect(screen.getByText('404')).toBeInTheDocument()
    expect(screen.getByText('Page not found')).toBeInTheDocument()
  })

  it('shows public event catalog and applies search with pagination', async () => {
    const user = userEvent.setup()
    const listParams: unknown[] = []
    window.history.pushState({}, '', ROUTES.events)
    installApiMock((config) => {
      if (endpoint(config) === 'POST /auth/refresh') {
        return rejectedResponse(config, 401, 'Invalid refresh token')
      }

      if (endpoint(config) === 'GET /events') {
        listParams.push(config.params)
        const params = config.params as { size?: number; page?: number }
        if (params.size === 100) {
          return response<PublicEventPage>(config, 200, publicEventPage([publishedEvent], 0, 100))
        }

        return response<PublicEventPage>(
          config,
          200,
          publicEventPage([publishedEvent], params.page ?? 0, 12, 13),
        )
      }

      return rejectedResponse(config, 500, 'Unexpected request')
    })

    render(<App />)

    expect(await screen.findByRole('heading', { name: 'Events' })).toBeInTheDocument()
    expect(await screen.findByRole('link', { name: publishedEvent.name })).toBeInTheDocument()
    expect(screen.getByText(venue.name)).toBeInTheDocument()
    expect(screen.getByText(/From 50,000/)).toBeInTheDocument()

    await user.type(screen.getByLabelText('Search events'), 'gala{enter}')
    await waitFor(() =>
      expect(listParams).toContainEqual(expect.objectContaining({ search: 'gala' })),
    )

    fireEvent.click(await screen.findByTitle('2'))
    await waitFor(() =>
      expect(listParams).toContainEqual(expect.objectContaining({ page: 1 })),
    )
  }, 10000)

  it('shows public event detail', async () => {
    window.history.pushState({}, '', ROUTES.eventDetail(publishedEvent.id))
    installApiMock((config) => {
      if (endpoint(config) === 'POST /auth/refresh') {
        return rejectedResponse(config, 401, 'Invalid refresh token')
      }

      if (endpoint(config) === `GET /events/${publishedEvent.id}`) {
        return response<PublicEvent>(config, 200, publishedEvent)
      }

      if (endpoint(config) === `GET /events/${publishedEvent.id}/seats`) {
        return response<EventSeatLayout>(config, 200, publicSeatLayout)
      }

      return rejectedResponse(config, 500, 'Unexpected request')
    })

    render(<App />)

    expect(await screen.findByRole('heading', { name: publishedEvent.name })).toBeInTheDocument()
    expect(screen.getByText(venue.name)).toBeInTheDocument()
    expect(screen.getByText(venue.timezone)).toBeInTheDocument()
    expect(screen.getByText(/From 50,000/)).toBeInTheDocument()
    expect(await screen.findByRole('button', { name: /Seat A1, available/i })).toBeInTheDocument()
  })

  it('renders seat states and prevents sold or blocked seat selection', async () => {
    window.history.pushState({}, '', ROUTES.eventDetail(publishedEvent.id))
    installApiMock((config) => {
      if (endpoint(config) === 'POST /auth/refresh') {
        return rejectedResponse(config, 401, 'Invalid refresh token')
      }

      if (endpoint(config) === `GET /events/${publishedEvent.id}`) {
        return response<PublicEvent>(config, 200, publishedEvent)
      }

      if (endpoint(config) === `GET /events/${publishedEvent.id}/seats`) {
        return response<EventSeatLayout>(config, 200, publicSeatLayout)
      }

      return rejectedResponse(config, 500, 'Unexpected request')
    })

    render(<App />)

    expect(await screen.findByText('Select seats')).toBeInTheDocument()
    expect(screen.getByText('Available')).toBeInTheDocument()
    expect(screen.getByText('Selected')).toBeInTheDocument()
    expect(screen.getByText('Sold')).toBeInTheDocument()
    expect(screen.getByText('Blocked')).toBeInTheDocument()
    expect(screen.getByText('Accessible')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: /Seat A3, sold/i })).toBeDisabled()
    expect(screen.getByRole('button', { name: /Seat A4, blocked/i })).toBeDisabled()
  })

  it('renders held seats as unavailable', async () => {
    const user = userEvent.setup()
    const heldSeatLayout: EventSeatLayout = {
      ...publicSeatLayout,
      sections: publicSeatLayout.sections.map((section) => ({
        ...section,
        rows: section.rows.map((row) => ({
          ...row,
          seats: row.seats.map((seat) => {
            if (seat.seatLabel === 'A1') {
              return { ...seat, status: 'HELD' as const }
            }
            if (seat.seatLabel === 'A2') {
              return { ...seat, status: 'HELD_BY_YOU' as const }
            }
            return seat
          }),
        })),
      })),
    }
    window.history.pushState({}, '', ROUTES.eventDetail(publishedEvent.id))
    installApiMock((config) => {
      if (endpoint(config) === 'POST /auth/refresh') {
        return rejectedResponse(config, 401, 'Invalid refresh token')
      }

      if (endpoint(config) === `GET /events/${publishedEvent.id}`) {
        return response<PublicEvent>(config, 200, publishedEvent)
      }

      if (endpoint(config) === `GET /events/${publishedEvent.id}/seats`) {
        return response<EventSeatLayout>(config, 200, heldSeatLayout)
      }

      return rejectedResponse(config, 500, 'Unexpected request')
    })

    render(<App />)

    expect(await screen.findByText('Held')).toBeInTheDocument()
    expect(screen.getByText('Held by you')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: /Seat A1, held/i })).toBeDisabled()
    expect(screen.getByRole('button', { name: /Seat A2, held by you/i })).toBeDisabled()

    await user.click(screen.getByRole('button', { name: /Seat A1, held/i }))

    expect(screen.getByText('0 of 8 selected')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Continue' })).toBeDisabled()
  })

  it('selects and deselects available seats while calculating total price', async () => {
    const user = userEvent.setup()
    window.history.pushState({}, '', ROUTES.eventDetail(publishedEvent.id))
    installApiMock((config) => {
      if (endpoint(config) === 'POST /auth/refresh') {
        return rejectedResponse(config, 401, 'Invalid refresh token')
      }

      if (endpoint(config) === `GET /events/${publishedEvent.id}`) {
        return response<PublicEvent>(config, 200, publishedEvent)
      }

      if (endpoint(config) === `GET /events/${publishedEvent.id}/seats`) {
        return response<EventSeatLayout>(config, 200, publicSeatLayout)
      }

      return rejectedResponse(config, 500, 'Unexpected request')
    })

    render(<App />)

    await user.click(await screen.findByRole('button', { name: /Seat A1, available/i }))
    await user.click(screen.getByRole('button', { name: /Seat A2, available/i }))

    const summaryCard = screen.getByText('Selection summary').closest('.ant-card') as HTMLElement
    expect(screen.getByText('2 of 8 selected')).toBeInTheDocument()
    expect(screen.getByText('125,000')).toBeInTheDocument()
    expect(within(summaryCard).getByText('A1')).toBeInTheDocument()
    expect(within(summaryCard).getByText('A2')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Continue' })).toBeEnabled()

    await user.click(screen.getByRole('button', { name: /Seat A1, selected/i }))

    expect(screen.getByText('1 of 8 selected')).toBeInTheDocument()
    expect(screen.getByText('75,000')).toBeInTheDocument()
    expect(within(summaryCard).queryByText('A1')).not.toBeInTheDocument()
    expect(screen.getByRole('button', { name: /Seat A1, available/i })).toHaveAttribute('aria-pressed', 'false')
  })

  it('enforces the seat selection limit', async () => {
    const user = userEvent.setup()
    window.history.pushState({}, '', ROUTES.eventDetail(publishedEvent.id))
    installApiMock((config) => {
      if (endpoint(config) === 'POST /auth/refresh') {
        return rejectedResponse(config, 401, 'Invalid refresh token')
      }

      if (endpoint(config) === `GET /events/${publishedEvent.id}`) {
        return response<PublicEvent>(config, 200, publishedEvent)
      }

      if (endpoint(config) === `GET /events/${publishedEvent.id}/seats`) {
        return response<EventSeatLayout>(config, 200, availableSeatLayout(9))
      }

      return rejectedResponse(config, 500, 'Unexpected request')
    })

    render(<App />)

    for (let seatNumber = 1; seatNumber <= 8; seatNumber += 1) {
      await user.click(await screen.findByRole('button', { name: new RegExp(`Seat A${seatNumber}, available`, 'i') }))
    }
    await user.click(screen.getByRole('button', { name: /Seat A9, available/i }))

    expect(screen.getByText('8 of 8 selected')).toBeInTheDocument()
    expect(screen.getByText('400,000')).toBeInTheDocument()
    expect(screen.getByText('You can select up to 8 seats.')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: /Seat A9, available/i })).toHaveAttribute('aria-pressed', 'false')
  }, 10000)

  it('creates a hold from selected seats and prevents duplicate submission', async () => {
    const user = userEvent.setup()
    const hold = activeSeatHold()
    const createHoldSpy = vi.fn()
    window.history.pushState({}, '', ROUTES.eventDetail(publishedEvent.id))
    installApiMock((config) => {
      if (endpoint(config) === 'POST /auth/refresh') {
        return response<LoginResponse>(config, 200, loginResponse)
      }

      if (endpoint(config) === 'GET /users/me') {
        return response<AuthUser>(config, 200, authUser)
      }

      if (endpoint(config) === `GET /events/${publishedEvent.id}`) {
        return response<PublicEvent>(config, 200, publishedEvent)
      }

      if (endpoint(config) === `GET /events/${publishedEvent.id}/seats`) {
        return response<EventSeatLayout>(config, 200, publicSeatLayout)
      }

      if (endpoint(config) === `POST /events/${publishedEvent.id}/holds`) {
        createHoldSpy(requestBody(config))
        return response<SeatHold>(config, 201, hold)
      }

      if (endpoint(config) === `GET /holds/${hold.holdId}`) {
        return response<SeatHold>(config, 200, hold)
      }

      if (endpoint(config) === `GET /events/${publishedEvent.id}`) {
        return response<PublicEvent>(config, 200, publishedEvent)
      }

      if (endpoint(config) === `GET /events/${publishedEvent.id}/seats`) {
        return response<EventSeatLayout>(config, 200, publicSeatLayout)
      }

      return rejectedResponse(config, 500, 'Unexpected request')
    })

    render(<App />)

    await user.click(await screen.findByRole('button', { name: /Seat A1, available/i }))
    await user.click(screen.getByRole('button', { name: /Seat A2, available/i }))
    await user.dblClick(screen.getByRole('button', { name: 'Continue' }))

    await waitFor(() =>
      expect(createHoldSpy).toHaveBeenCalledWith({
        eventSeatIds: hold.eventSeatIds,
      }),
    )
    expect(createHoldSpy).toHaveBeenCalledOnce()
    expect(await screen.findByRole('heading', { name: 'Checkout' })).toBeInTheDocument()
    expect(screen.getByText(`Hold ${hold.holdId}`)).toBeInTheDocument()
    expect(screen.getByText('Hold expires in')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Pay now' })).toBeEnabled()
  }, 10000)

  it('removes unavailable selections when hold creation conflicts', async () => {
    const user = userEvent.setup()
    let seatLayoutCalls = 0
    const conflictedSeatLayout: EventSeatLayout = {
      ...publicSeatLayout,
      sections: publicSeatLayout.sections.map((section) => ({
        ...section,
        rows: section.rows.map((row) => ({
          ...row,
          seats: row.seats.map((seat) =>
            seat.seatLabel === 'A1' ? { ...seat, status: 'HELD' as const } : seat,
          ),
        })),
      })),
    }
    window.history.pushState({}, '', ROUTES.eventDetail(publishedEvent.id))
    installApiMock((config) => {
      if (endpoint(config) === 'POST /auth/refresh') {
        return response<LoginResponse>(config, 200, loginResponse)
      }

      if (endpoint(config) === 'GET /users/me') {
        return response<AuthUser>(config, 200, authUser)
      }

      if (endpoint(config) === `GET /events/${publishedEvent.id}`) {
        return response<PublicEvent>(config, 200, publishedEvent)
      }

      if (endpoint(config) === `GET /events/${publishedEvent.id}/seats`) {
        seatLayoutCalls += 1
        return response<EventSeatLayout>(
          config,
          200,
          seatLayoutCalls === 1 ? publicSeatLayout : conflictedSeatLayout,
        )
      }

      if (endpoint(config) === `POST /events/${publishedEvent.id}/holds`) {
        return rejectedResponse(config, 409, 'Seat hold conflict')
      }

      return rejectedResponse(config, 500, 'Unexpected request')
    })

    render(<App />)

    await user.click(await screen.findByRole('button', { name: /Seat A1, available/i }))
    await user.click(screen.getByRole('button', { name: /Seat A2, available/i }))
    await user.click(screen.getByRole('button', { name: 'Continue' }))

    expect(await screen.findByText('Some selected seats are no longer available. Review the updated seat map.')).toBeInTheDocument()
    const summaryCard = screen.getByText('Selection summary').closest('.ant-card') as HTMLElement
    expect(screen.getByText('1 of 8 selected')).toBeInTheDocument()
    expect(within(summaryCard).queryByText('A1')).not.toBeInTheDocument()
    expect(within(summaryCard).getByText('A2')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: /Seat A1, held/i })).toBeDisabled()
  }, 10000)

  it('shows a distinct network error when hold creation cannot reach the server', async () => {
    const user = userEvent.setup()
    window.history.pushState({}, '', ROUTES.eventDetail(publishedEvent.id))
    installApiMock((config) => {
      if (endpoint(config) === 'POST /auth/refresh') {
        return response<LoginResponse>(config, 200, loginResponse)
      }

      if (endpoint(config) === 'GET /users/me') {
        return response<AuthUser>(config, 200, authUser)
      }

      if (endpoint(config) === `GET /events/${publishedEvent.id}`) {
        return response<PublicEvent>(config, 200, publishedEvent)
      }

      if (endpoint(config) === `GET /events/${publishedEvent.id}/seats`) {
        return response<EventSeatLayout>(config, 200, publicSeatLayout)
      }

      if (endpoint(config) === `POST /events/${publishedEvent.id}/holds`) {
        return Promise.reject(new AxiosError('Network Error', 'ERR_NETWORK', config))
      }

      return rejectedResponse(config, 500, 'Unexpected request')
    })

    render(<App />)

    await user.click(await screen.findByRole('button', { name: /Seat A1, available/i }))
    await user.click(screen.getByRole('button', { name: 'Continue' }))

    const alerts = await screen.findAllByText('Network error. Check your connection and try again.')
    expect(alerts.length).toBeGreaterThan(0)
    expect(screen.getByText('1 of 8 selected')).toBeInTheDocument()
  }, 10000)

  it('restores a hold from checkout URL after refresh', async () => {
    const hold = activeSeatHold()
    const getHoldSpy = vi.fn()
    window.history.pushState({}, '', ROUTES.checkout(hold.holdId))
    installApiMock((config) => {
      if (endpoint(config) === 'POST /auth/refresh') {
        return response<LoginResponse>(config, 200, loginResponse)
      }

      if (endpoint(config) === 'GET /users/me') {
        return response<AuthUser>(config, 200, authUser)
      }

      if (endpoint(config) === `GET /holds/${hold.holdId}`) {
        getHoldSpy()
        return response<SeatHold>(config, 200, hold)
      }

      if (endpoint(config) === `GET /events/${publishedEvent.id}`) {
        return response<PublicEvent>(config, 200, publishedEvent)
      }

      if (endpoint(config) === `GET /events/${publishedEvent.id}/seats`) {
        return response<EventSeatLayout>(config, 200, publicSeatLayout)
      }

      return rejectedResponse(config, 500, 'Unexpected request')
    })

    render(<App />)

    expect(await screen.findByRole('heading', { name: 'Checkout' })).toBeInTheDocument()
    expect(screen.getByText(`Hold ${hold.holdId}`)).toBeInTheDocument()
    expect(screen.getByText('Hold expires in')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Pay now' })).toBeEnabled()
    expect(getHoldSpy).toHaveBeenCalledOnce()
  })

  it('disables checkout when the server hold is expired', async () => {
    const hold = activeSeatHold(new Date(Date.now() - 1000).toISOString())
    window.history.pushState({}, '', ROUTES.checkout(hold.holdId))
    installApiMock((config) => {
      if (endpoint(config) === 'POST /auth/refresh') {
        return response<LoginResponse>(config, 200, loginResponse)
      }

      if (endpoint(config) === 'GET /users/me') {
        return response<AuthUser>(config, 200, authUser)
      }

      if (endpoint(config) === `GET /holds/${hold.holdId}`) {
        return response<SeatHold>(config, 200, hold)
      }

      if (endpoint(config) === `GET /events/${publishedEvent.id}`) {
        return response<PublicEvent>(config, 200, publishedEvent)
      }

      if (endpoint(config) === `GET /events/${publishedEvent.id}/seats`) {
        return response<EventSeatLayout>(config, 200, publicSeatLayout)
      }

      return rejectedResponse(config, 500, 'Unexpected request')
    })

    render(<App />)

    expect(await screen.findByText('Hold expired')).toBeInTheDocument()
    expect(screen.getByText('Payment is disabled because the server hold has expired.')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Pay now' })).toBeDisabled()
  })

  it('releases a hold and returns to the seat map', async () => {
    const user = userEvent.setup()
    const hold = activeSeatHold()
    const releaseSpy = vi.fn()
    window.history.pushState({}, '', ROUTES.checkout(hold.holdId))
    installApiMock((config) => {
      if (endpoint(config) === 'POST /auth/refresh') {
        return response<LoginResponse>(config, 200, loginResponse)
      }

      if (endpoint(config) === 'GET /users/me') {
        return response<AuthUser>(config, 200, authUser)
      }

      if (endpoint(config) === `GET /holds/${hold.holdId}`) {
        return response<SeatHold>(config, 200, hold)
      }

      if (endpoint(config) === `GET /events/${publishedEvent.id}`) {
        return response<PublicEvent>(config, 200, publishedEvent)
      }

      if (endpoint(config) === `GET /events/${publishedEvent.id}/seats`) {
        return response<EventSeatLayout>(config, 200, publicSeatLayout)
      }

      if (endpoint(config) === `DELETE /holds/${hold.holdId}`) {
        releaseSpy()
        return response(config, 204, undefined)
      }

      if (endpoint(config) === `GET /events/${publishedEvent.id}`) {
        return response<PublicEvent>(config, 200, publishedEvent)
      }

      if (endpoint(config) === `GET /events/${publishedEvent.id}/seats`) {
        return response<EventSeatLayout>(config, 200, publicSeatLayout)
      }

      return rejectedResponse(config, 500, 'Unexpected request')
    })

    render(<App />)

    expect(await screen.findByRole('heading', { name: 'Checkout' })).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: 'Release hold' }))
    const dialog = await screen.findByRole('dialog')
    await user.click(within(dialog).getByRole('button', { name: 'Release hold' }))

    await waitFor(() => expect(releaseSpy).toHaveBeenCalledOnce())
    expect(await screen.findByRole('heading', { name: publishedEvent.name })).toBeInTheDocument()
    expect(await screen.findByText('Hold released')).toBeInTheDocument()
  }, 10000)

  it('completes checkout successfully and shows issued tickets', async () => {
    const user = userEvent.setup()
    const paymentSpy = vi.fn<(config: InternalAxiosRequestConfig) => void>()
    const { hold, order } = installCheckoutMock({ paymentSpy })
    window.history.pushState({}, '', ROUTES.checkout(hold.holdId))

    render(<App />)

    expect(await screen.findByRole('heading', { name: 'Checkout' })).toBeInTheDocument()
    expect(await screen.findByText('Reservation summary')).toBeInTheDocument()
    expect(screen.getByText('Payment simulator')).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: 'Pay now' }))

    expect(await screen.findByText('Payment succeeded')).toBeInTheDocument()
    expect(await screen.findByText('Your tickets have been issued.')).toBeInTheDocument()
    expect(await screen.findByText(publishedEvent.name)).toBeInTheDocument()
    expect(screen.getByText('Orchestra A1')).toBeInTheDocument()
    expect(paymentSpy).toHaveBeenCalledOnce()
    const paymentConfig = paymentSpy.mock.calls[0][0] as InternalAxiosRequestConfig
    expect(requestBody(paymentConfig)).toEqual({ token: 'tok_success' })
    expect(paymentConfig.headers.get?.('Idempotency-Key')).toBeTruthy()
    expect(order.id).toBeTruthy()
  }, 10000)

  it('shows a declined payment without issuing tickets', async () => {
    const user = userEvent.setup()
    const paymentSpy = vi.fn<(config: InternalAxiosRequestConfig) => void>()
    const { hold } = installCheckoutMock({ status: 'DECLINED', paymentSpy })
    window.history.pushState({}, '', ROUTES.checkout(hold.holdId))

    render(<App />)

    expect(await screen.findByRole('heading', { name: 'Checkout' })).toBeInTheDocument()
    await user.click(screen.getByText('Decline card'))
    await user.click(screen.getByRole('button', { name: 'Pay now' }))

    expect(await screen.findByText(/declined the payment/i)).toBeInTheDocument()
    expect(screen.getAllByText('Payment declined').length).toBeGreaterThan(0)
    expect(screen.getByText('No tickets were issued for this payment attempt.')).toBeInTheDocument()
    expect(paymentSpy).toHaveBeenCalledOnce()
    expect(requestBody(paymentSpy.mock.calls[0][0] as InternalAxiosRequestConfig)).toEqual({
      token: 'tok_declined',
    })
  }, 10000)

  it('shows a timed-out payment clearly without issuing tickets', async () => {
    const user = userEvent.setup()
    const paymentSpy = vi.fn<(config: InternalAxiosRequestConfig) => void>()
    const { hold } = installCheckoutMock({ status: 'TIMED_OUT', paymentSpy })
    window.history.pushState({}, '', ROUTES.checkout(hold.holdId))

    render(<App />)

    expect(await screen.findByRole('heading', { name: 'Checkout' })).toBeInTheDocument()
    await user.click(screen.getByText('Provider timeout'))
    await user.click(screen.getByRole('button', { name: 'Pay now' }))

    expect(await screen.findByText(/did not respond in time/i)).toBeInTheDocument()
    expect(screen.getAllByText('Payment timed out').length).toBeGreaterThan(0)
    expect(screen.getByText('No tickets were issued for this payment attempt.')).toBeInTheDocument()
    expect(paymentSpy).toHaveBeenCalledOnce()
    expect(requestBody(paymentSpy.mock.calls[0][0] as InternalAxiosRequestConfig)).toEqual({
      token: 'tok_timeout',
    })
  }, 10000)

  it('submits one payment for duplicate clicks and restores the result after refresh', async () => {
    const user = userEvent.setup()
    const paymentSpy = vi.fn<(config: InternalAxiosRequestConfig) => void>()
    const { hold } = installCheckoutMock({ paymentSpy })
    window.history.pushState({}, '', ROUTES.checkout(hold.holdId))

    render(<App />)

    expect(await screen.findByRole('heading', { name: 'Checkout' })).toBeInTheDocument()
    await user.dblClick(screen.getByRole('button', { name: 'Pay now' }))

    expect((await screen.findAllByText('Payment succeeded')).length).toBeGreaterThan(0)
    expect(paymentSpy).toHaveBeenCalledOnce()

    const resultPath = window.location.pathname
    cleanup()
    window.history.pushState({}, '', resultPath)
    render(<App />)

    expect((await screen.findAllByText('Payment succeeded')).length).toBeGreaterThan(0)
    expect(screen.getByText('Refreshing this page will not repeat the purchase.')).toBeInTheDocument()
    expect(paymentSpy).toHaveBeenCalledOnce()
  }, 10000)

  it('displays the ticket list and ticket detail QR data', async () => {
    const user = userEvent.setup()
    const hold = activeSeatHold()
    const reservation = reservationResponse(hold)
    const order = orderResponse(reservation, 'PAID')
    const ticket = ticketForOrder(order)
    window.history.pushState({}, '', ROUTES.tickets)
    installApiMock((config) => {
      if (endpoint(config) === 'POST /auth/refresh') {
        return response<LoginResponse>(config, 200, loginResponse)
      }

      if (endpoint(config) === 'GET /users/me') {
        return response<AuthUser>(config, 200, authUser)
      }

      if (endpoint(config) === 'GET /users/me/tickets') {
        return response<Ticket[]>(config, 200, [ticket])
      }

      if (endpoint(config) === `GET /tickets/${ticket.id}`) {
        return response<Ticket>(config, 200, ticket)
      }

      return rejectedResponse(config, 500, 'Unexpected request')
    })

    render(<App />)

    expect(await screen.findByRole('heading', { name: 'Tickets' })).toBeInTheDocument()
    expect(await screen.findByText(publishedEvent.name)).toBeInTheDocument()
    expect(screen.getByText('Orchestra A1')).toBeInTheDocument()
    await user.click(screen.getByRole('link', { name: publishedEvent.name }))

    expect(await screen.findByText(ticket.ticketCode)).toBeInTheDocument()
    expect(screen.getByText(ticket.qrData)).toBeInTheDocument()
    expect(screen.getByText(/row A, seat A1/)).toBeInTheDocument()
  }, 10000)

  it('validates required login fields', async () => {
    const user = userEvent.setup()
    window.history.pushState({}, '', ROUTES.login)

    render(<App />)

    await user.click(screen.getByRole('button', { name: /log in/i }))

    expect(await screen.findByText('Email is required')).toBeInTheDocument()
    expect(await screen.findByText('Password is required')).toBeInTheDocument()
  })

  it('registers a user and returns to login', async () => {
    const user = userEvent.setup()
    const registerSpy = vi.fn()
    window.history.pushState({}, '', ROUTES.register)
    installApiMock((config) => {
      if (endpoint(config) === 'POST /auth/refresh') {
        return rejectedResponse(config, 401, 'Invalid refresh token')
      }

      if (endpoint(config) === 'POST /auth/register') {
        registerSpy(requestBody(config))
        return response<RegisterResponse>(config, 201, {
          id: authUser.id,
          email: authUser.email,
          role: authUser.role,
          status: authUser.status,
          createdAt: authUser.createdAt,
        })
      }

      return rejectedResponse(config, 500, 'Unexpected request')
    })

    render(<App />)

    await user.type(screen.getByLabelText('Email'), 'user@example.com')
    await user.type(screen.getByLabelText('Password'), 'StrongPassword123!')
    await user.click(screen.getByRole('button', { name: /create account/i }))

    expect(await screen.findByRole('heading', { name: 'Login' })).toBeInTheDocument()
    expect(await screen.findByText('Account created')).toBeInTheDocument()
    expect(registerSpy).toHaveBeenCalledWith({
      email: 'user@example.com',
      password: 'StrongPassword123!',
    })
  })

  it('validates weak registration passwords', async () => {
    const user = userEvent.setup()
    window.history.pushState({}, '', ROUTES.register)

    render(<App />)

    await user.type(screen.getByLabelText('Email'), 'user@example.com')
    await user.type(screen.getByLabelText('Password'), 'weak')
    await user.click(screen.getByRole('button', { name: /create account/i }))

    expect(await screen.findByText('Password must be at least 12 characters')).toBeInTheDocument()
  })

  it('logs in and sends the access token to the current-user query', async () => {
    const user = userEvent.setup()
    const currentUserSpy = vi.fn()
    window.history.pushState({}, '', ROUTES.login)
    installApiMock((config) => {
      if (endpoint(config) === 'POST /auth/refresh') {
        return rejectedResponse(config, 401, 'Invalid refresh token')
      }

      if (endpoint(config) === 'POST /auth/login') {
        return response<LoginResponse>(config, 200, loginResponse)
      }

      if (endpoint(config) === 'GET /users/me') {
        currentUserSpy(authorization(config))
        return response<AuthUser>(config, 200, authUser)
      }

      if (endpoint(config) === 'GET /events') {
        return publicEventsResponse(config)
      }

      return rejectedResponse(config, 500, 'Unexpected request')
    })

    render(<App />)

    await user.type(screen.getByLabelText('Email'), 'user@example.com')
    await user.type(screen.getByLabelText('Password'), 'StrongPassword123!')
    await user.click(screen.getByRole('button', { name: /log in/i }))

    expect(await screen.findByRole('heading', { name: 'Events' })).toBeInTheDocument()
    expect(currentUserSpy).toHaveBeenCalledWith('Bearer access-token')
  })

  it('shows an alert for failed login', async () => {
    const user = userEvent.setup()
    window.history.pushState({}, '', ROUTES.login)
    installApiMock((config) => {
      if (endpoint(config) === 'POST /auth/refresh') {
        return rejectedResponse(config, 401, 'Invalid refresh token')
      }

      if (endpoint(config) === 'POST /auth/login') {
        return rejectedResponse(config, 401, 'Invalid email or password')
      }

      return rejectedResponse(config, 500, 'Unexpected request')
    })

    render(<App />)

    await user.type(screen.getByLabelText('Email'), 'user@example.com')
    await user.type(screen.getByLabelText('Password'), 'WrongPassword123!')
    await user.click(screen.getByRole('button', { name: /log in/i }))

    expect(await screen.findByText('Invalid email or password')).toBeInTheDocument()
    expect(screen.getByRole('heading', { name: 'Login' })).toBeInTheDocument()
  })

  it('restores a session from the refresh cookie', async () => {
    const currentUserSpy = vi.fn()
    window.history.pushState({}, '', ROUTES.events)
    installApiMock((config) => {
      if (endpoint(config) === 'POST /auth/refresh') {
        return response<LoginResponse>(config, 200, {
          ...loginResponse,
          accessToken: 'restored-token',
        })
      }

      if (endpoint(config) === 'GET /users/me') {
        currentUserSpy(authorization(config))
        return response<AuthUser>(config, 200, authUser)
      }

      if (endpoint(config) === 'GET /events') {
        return publicEventsResponse(config)
      }

      return rejectedResponse(config, 500, 'Unexpected request')
    })

    render(<App />)

    expect(await screen.findByRole('heading', { name: 'Events' })).toBeInTheDocument()
    expect(currentUserSpy).toHaveBeenCalledWith('Bearer restored-token')
  })

  it('redirects protected routes to login', async () => {
    window.history.pushState({}, '', ROUTES.admin)

    render(<App />)

    expect(await screen.findByRole('heading', { name: 'Login' })).toBeInTheDocument()
  })

  it('hides admin navigation from normal users', async () => {
    window.history.pushState({}, '', ROUTES.events)
    installApiMock((config) => {
      if (endpoint(config) === 'POST /auth/refresh') {
        return response<LoginResponse>(config, 200, loginResponse)
      }

      if (endpoint(config) === 'GET /users/me') {
        return response<AuthUser>(config, 200, authUser)
      }

      if (endpoint(config) === 'GET /events') {
        return publicEventsResponse(config)
      }

      return rejectedResponse(config, 500, 'Unexpected request')
    })

    render(<App />)

    expect(await screen.findByRole('heading', { name: 'Events' })).toBeInTheDocument()
    expect(screen.queryByRole('link', { name: /admin/i })).not.toBeInTheDocument()
  })

  it('shows a 403 result when a normal user opens an admin page directly', async () => {
    window.history.pushState({}, '', ROUTES.adminVenueNew)
    installApiMock((config) => {
      if (endpoint(config) === 'POST /auth/refresh') {
        return response<LoginResponse>(config, 200, loginResponse)
      }

      if (endpoint(config) === 'GET /users/me') {
        return response<AuthUser>(config, 200, authUser)
      }

      return rejectedResponse(config, 500, 'Unexpected request')
    })

    render(<App />)

    expect(await screen.findByText('403')).toBeInTheDocument()
    expect(screen.getByText('You are not authorized to access this page.')).toBeInTheDocument()
    expect(screen.queryByRole('heading', { name: 'Create venue' })).not.toBeInTheDocument()
  })

  it('shows admin navigation and route content to admins', async () => {
    window.history.pushState({}, '', ROUTES.admin)
    installApiMock((config) => {
      if (endpoint(config) === 'POST /auth/refresh') {
        return response<LoginResponse>(config, 200, loginResponse)
      }

      if (endpoint(config) === 'GET /users/me') {
        return response<AuthUser>(config, 200, adminUser)
      }

      if (endpoint(config) === 'GET /admin/venues') {
        return response<VenuePage>(config, 200, venuePage([]))
      }

      return rejectedResponse(config, 500, 'Unexpected request')
    })

    render(<App />)

    expect(await screen.findByRole('heading', { name: 'Venues' })).toBeInTheDocument()
    expect(screen.getByRole('link', { name: /admin/i })).toBeInTheDocument()
  })

  it('validates required venue form fields', async () => {
    const user = userEvent.setup()
    window.history.pushState({}, '', ROUTES.adminVenueNew)
    installApiMock((config) => {
      if (endpoint(config) === 'POST /auth/refresh') {
        return response<LoginResponse>(config, 200, loginResponse)
      }

      if (endpoint(config) === 'GET /users/me') {
        return response<AuthUser>(config, 200, adminUser)
      }

      return rejectedResponse(config, 500, 'Unexpected request')
    })

    render(<App />)

    expect(await screen.findByRole('heading', { name: 'Create venue' })).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: /create venue/i }))

    expect(await screen.findByText('Venue name is required')).toBeInTheDocument()
    expect(screen.getByText('Address is required')).toBeInTheDocument()
    expect(screen.getByText('City is required')).toBeInTheDocument()
    expect(screen.getByText('Country is required')).toBeInTheDocument()
    expect(screen.getByRole('combobox', { name: 'Timezone' })).toBeInTheDocument()
  })

  it('creates a venue and opens the edit page', async () => {
    const user = userEvent.setup()
    const createSpy = vi.fn()
    window.history.pushState({}, '', ROUTES.adminVenueNew)
    installApiMock((config) => {
      if (endpoint(config) === 'POST /auth/refresh') {
        return response<LoginResponse>(config, 200, loginResponse)
      }

      if (endpoint(config) === 'GET /users/me') {
        return response<AuthUser>(config, 200, adminUser)
      }

      if (endpoint(config) === 'POST /admin/venues') {
        createSpy(requestBody(config))
        return response<Venue>(config, 201, venue)
      }

      if (endpoint(config) === `GET /admin/venues/${venue.id}`) {
        return response<Venue>(config, 200, venue)
      }

      if (endpoint(config) === `GET /admin/venues/${venue.id}/seat-layout`) {
        return response<SeatLayout>(config, 200, emptySeatLayout)
      }

      return rejectedResponse(config, 500, 'Unexpected request')
    })

    render(<App />)

    expect(await screen.findByRole('heading', { name: 'Create venue' })).toBeInTheDocument()
    await user.type(screen.getByLabelText('Venue name'), venue.name)
    await user.type(screen.getByLabelText('Address'), venue.address)
    await user.type(screen.getByLabelText('City'), venue.city)
    await user.type(screen.getByLabelText('Country'), venue.country)
    await user.click(screen.getByRole('combobox', { name: 'Timezone' }))
    await user.type(screen.getByRole('combobox', { name: 'Timezone' }), venue.timezone)
    await user.click(await screen.findByTitle(venue.timezone))
    await user.click(screen.getByRole('button', { name: /create venue/i }))

    await waitFor(() =>
      expect(createSpy).toHaveBeenCalledWith({
        name: venue.name,
        address: venue.address,
        city: venue.city,
        country: venue.country,
        timezone: venue.timezone,
      }),
    )
    expect(await screen.findByRole('heading', { name: 'Edit venue' })).toBeInTheDocument()
  })

  it('adds a section to an existing venue', async () => {
    const user = userEvent.setup()
    const sectionSpy = vi.fn()
    let layout: SeatLayout = emptySeatLayout
    const section = {
      id: '07e4de90-b0fa-4f16-a74d-8dcb5743f5bb',
      venueId: venue.id,
      name: 'Balcony',
      displayOrder: 2,
      createdAt: '2026-07-27T00:00:00Z',
      seats: [],
    }
    window.history.pushState({}, '', ROUTES.adminVenueEdit(venue.id))
    installApiMock((config) => {
      if (endpoint(config) === 'POST /auth/refresh') {
        return response<LoginResponse>(config, 200, loginResponse)
      }

      if (endpoint(config) === 'GET /users/me') {
        return response<AuthUser>(config, 200, adminUser)
      }

      if (endpoint(config) === `GET /admin/venues/${venue.id}`) {
        return response<Venue>(config, 200, venue)
      }

      if (endpoint(config) === `GET /admin/venues/${venue.id}/seat-layout`) {
        return response<SeatLayout>(config, 200, layout)
      }

      if (endpoint(config) === `POST /admin/venues/${venue.id}/sections`) {
        sectionSpy(requestBody(config))
        layout = { venueId: venue.id, sections: [section] }
        return response(config, 201, section)
      }

      return rejectedResponse(config, 500, 'Unexpected request')
    })

    render(<App />)

    expect(await screen.findByRole('heading', { name: 'Edit venue' })).toBeInTheDocument()
    await screen.findByLabelText('Section name')
    await user.type(screen.getByLabelText('Section name'), section.name)
    await user.clear(screen.getByLabelText('Display order'))
    await user.type(screen.getByLabelText('Display order'), String(section.displayOrder))
    await user.click(screen.getByRole('button', { name: /add section/i }))

    await waitFor(() =>
      expect(sectionSpy).toHaveBeenCalledWith({
        name: section.name,
        displayOrder: section.displayOrder,
      }),
    )
    expect(await screen.findAllByText('Balcony')).toHaveLength(2)
  })

  it('submits bulk seats and refreshes the layout preview', async () => {
    const user = userEvent.setup()
    const bulkSeatSpy = vi.fn()
    const section = {
      id: 'cb2fa4a3-e4f9-4d5e-a757-511c5acf4281',
      venueId: venue.id,
      name: 'Main',
      displayOrder: 0,
      createdAt: '2026-07-27T00:00:00Z',
      seats: [],
    }
    let layout: SeatLayout = { venueId: venue.id, sections: [section] }
    window.history.pushState({}, '', ROUTES.adminVenueEdit(venue.id))
    installApiMock((config) => {
      if (endpoint(config) === 'POST /auth/refresh') {
        return response<LoginResponse>(config, 200, loginResponse)
      }

      if (endpoint(config) === 'GET /users/me') {
        return response<AuthUser>(config, 200, adminUser)
      }

      if (endpoint(config) === `GET /admin/venues/${venue.id}`) {
        return response<Venue>(config, 200, venue)
      }

      if (endpoint(config) === `GET /admin/venues/${venue.id}/seat-layout`) {
        return response<SeatLayout>(config, 200, layout)
      }

      if (endpoint(config) === `POST /admin/sections/${section.id}/seats/bulk`) {
        const body = requestBody<{ seats: SeatCreateRequest[] }>(config)
        bulkSeatSpy(body)
        layout = {
          venueId: venue.id,
          sections: [
            {
              ...section,
              seats: body.seats.map((seat, index) => ({
                id: `seat-${index}`,
                sectionId: section.id,
                createdAt: '2026-07-27T00:00:00Z',
                ...seat,
              })),
            },
          ],
        }
        return response(config, 201, layout.sections[0].seats)
      }

      return rejectedResponse(config, 500, 'Unexpected request')
    })

    render(<App />)

    expect(await screen.findByRole('heading', { name: 'Edit venue' })).toBeInTheDocument()
    await user.click(await screen.findByRole('button', { name: 'Add seats to Main' }))
    await user.type(screen.getByLabelText('Row label'), 'A')
    await user.clear(screen.getByLabelText('Starting seat number'))
    await user.type(screen.getByLabelText('Starting seat number'), '1')
    await user.clear(screen.getByLabelText('Seat count'))
    await user.type(screen.getByLabelText('Seat count'), '3')
    await user.click(screen.getByLabelText('Accessible seats'))
    await user.click(screen.getByRole('button', { name: /create seats/i }))

    await waitFor(() =>
      expect(bulkSeatSpy).toHaveBeenCalledWith({
        seats: [
          { rowLabel: 'A', seatNumber: 1, seatLabel: 'A1', accessible: true },
          { rowLabel: 'A', seatNumber: 2, seatLabel: 'A2', accessible: true },
          { rowLabel: 'A', seatNumber: 3, seatLabel: 'A3', accessible: true },
        ],
      }),
    )
    expect(await screen.findByLabelText('Seat A1')).toBeInTheDocument()
  }, 10000)

  it('requires confirmation before archiving a venue', async () => {
    const archiveSpy = vi.fn()
    let archived = false
    window.history.pushState({}, '', ROUTES.admin)
    installApiMock((config) => {
      if (endpoint(config) === 'POST /auth/refresh') {
        return response<LoginResponse>(config, 200, loginResponse)
      }

      if (endpoint(config) === 'GET /users/me') {
        return response<AuthUser>(config, 200, adminUser)
      }

      if (endpoint(config) === 'GET /admin/venues') {
        return response<VenuePage>(config, 200, venuePage([
          { ...venue, status: archived ? 'ARCHIVED' : 'ACTIVE' },
        ]))
      }

      if (endpoint(config) === `POST /admin/venues/${venue.id}/archive`) {
        archiveSpy()
        archived = true
        return response<Venue>(config, 200, { ...venue, status: 'ARCHIVED' })
      }

      return rejectedResponse(config, 500, 'Unexpected request')
    })

    render(<App />)

    expect(await screen.findByRole('heading', { name: 'Venues' })).toBeInTheDocument()
    fireEvent.click(await screen.findByRole('button', { name: 'Archive Concert Hall' }))

    expect(await screen.findByText(/Archive Concert Hall/)).toBeInTheDocument()
    expect(archiveSpy).not.toHaveBeenCalled()

    fireEvent.click(screen.getByRole('button', { name: 'Archive venue' }))

    await waitFor(() => expect(archiveSpy).toHaveBeenCalledOnce())
  }, 10000)

  it('validates required event form fields', async () => {
    const user = userEvent.setup()
    window.history.pushState({}, '', ROUTES.adminEventNew)
    installApiMock((config) => {
      if (endpoint(config) === 'POST /auth/refresh') {
        return response<LoginResponse>(config, 200, loginResponse)
      }

      if (endpoint(config) === 'GET /users/me') {
        return response<AuthUser>(config, 200, adminUser)
      }

      if (endpoint(config) === 'GET /admin/venues') {
        return response<VenuePage>(config, 200, venuePage([]))
      }

      return rejectedResponse(config, 500, 'Unexpected request')
    })

    render(<App />)

    expect(await screen.findByRole('heading', { name: 'Create event' })).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: /create event/i }))

    expect(await screen.findByText('Venue is required')).toBeInTheDocument()
    expect(screen.getByText('Event name is required')).toBeInTheDocument()
    expect(screen.getByText('Event start is required')).toBeInTheDocument()
    expect(screen.getByText('Sales start is required')).toBeInTheDocument()
    expect(screen.getByText('Sales end is required')).toBeInTheDocument()
  })

  it('creates an event with UTC schedule values', async () => {
    const user = userEvent.setup()
    const createEventSpy = vi.fn()
    window.history.pushState({}, '', ROUTES.adminEventNew)
    installApiMock((config) => {
      if (endpoint(config) === 'POST /auth/refresh') {
        return response<LoginResponse>(config, 200, loginResponse)
      }

      if (endpoint(config) === 'GET /users/me') {
        return response<AuthUser>(config, 200, adminUser)
      }

      if (endpoint(config) === 'GET /admin/venues') {
        return response<VenuePage>(config, 200, venuePage([venue]))
      }

      if (endpoint(config) === 'POST /admin/events') {
        createEventSpy(requestBody(config))
        return response<Event>(config, 201, event)
      }

      if (endpoint(config) === `GET /admin/events/${event.id}`) {
        return response<Event>(config, 200, event)
      }

      if (endpoint(config) === `GET /admin/venues/${venue.id}/seat-layout`) {
        return response<SeatLayout>(config, 200, emptySeatLayout)
      }

      if (endpoint(config) === `GET /admin/events/${event.id}/sections`) {
        return response<EventSectionConfiguration>(config, 200, emptyEventSections)
      }

      return rejectedResponse(config, 500, 'Unexpected request')
    })

    render(<App />)

    expect(await screen.findByRole('heading', { name: 'Create event' })).toBeInTheDocument()
    expect(await screen.findByText(venue.timezone)).toBeInTheDocument()
    await user.type(screen.getByLabelText('Event name'), event.name)
    await user.type(screen.getByLabelText('Description'), event.description ?? '')
    await fillDate(user, 'Event start', '2026-09-01 19:00')
    await fillDate(user, 'Sales start', '2026-08-01 00:00')
    await fillDate(user, 'Sales end', '2026-09-01 18:00')
    await user.click(screen.getByRole('button', { name: /create event/i }))

    await waitFor(() =>
      expect(createEventSpy).toHaveBeenCalledWith({
        venueId: venue.id,
        name: event.name,
        description: event.description,
        startTime: '2026-09-01T12:00:00.000Z',
        salesStartTime: '2026-07-31T17:00:00.000Z',
        salesEndTime: '2026-09-01T11:00:00.000Z',
      }),
    )
    expect(await screen.findByRole('heading', { name: 'Edit event' })).toBeInTheDocument()
  }, 10000)

  it('saves section pricing for a draft event', async () => {
    const user = userEvent.setup()
    const pricingSpy = vi.fn()
    window.history.pushState({}, '', ROUTES.adminEventEdit(event.id))
    installApiMock((config) => {
      if (endpoint(config) === 'POST /auth/refresh') {
        return response<LoginResponse>(config, 200, loginResponse)
      }

      if (endpoint(config) === 'GET /users/me') {
        return response<AuthUser>(config, 200, adminUser)
      }

      if (endpoint(config) === 'GET /admin/venues') {
        return response<VenuePage>(config, 200, venuePage([venue]))
      }

      if (endpoint(config) === `GET /admin/events/${event.id}`) {
        return response<Event>(config, 200, event)
      }

      if (endpoint(config) === `GET /admin/venues/${venue.id}/seat-layout`) {
        return response<SeatLayout>(config, 200, layoutWithSection)
      }

      if (endpoint(config) === `GET /admin/events/${event.id}/sections`) {
        return response<EventSectionConfiguration>(config, 200, emptyEventSections)
      }

      if (endpoint(config) === `PUT /admin/events/${event.id}/sections`) {
        pricingSpy(requestBody(config))
        return response<EventSectionConfiguration>(config, 200, {
          eventId: event.id,
          sections: [
            {
              id: '5f121e8e-2f1d-4b10-b564-1567ff78e65d',
              eventId: event.id,
              venueSectionId: layoutWithSection.sections[0].id,
              price: 125000,
              salesEnabled: true,
              createdAt: '2026-08-03T00:00:00Z',
              updatedAt: '2026-08-03T00:00:00Z',
            },
          ],
        })
      }

      return rejectedResponse(config, 500, 'Unexpected request')
    })

    render(<App />)

    expect(await screen.findByRole('heading', { name: 'Edit event' })).toBeInTheDocument()
    expect(await screen.findByText('Orchestra')).toBeInTheDocument()
    await user.type(screen.getByRole('spinbutton'), '125000')
    await user.click(screen.getByRole('button', { name: /save pricing/i }))

    await waitFor(() =>
      expect(pricingSpy).toHaveBeenCalledWith({
        sections: [
          {
            venueSectionId: layoutWithSection.sections[0].id,
            price: 125000,
            salesEnabled: true,
          },
        ],
      }),
    )
  }, 10000)

  it('requires confirmation before publishing an event', async () => {
    const publishSpy = vi.fn()
    window.history.pushState({}, '', ROUTES.adminEventEdit(event.id))
    installApiMock((config) => {
      if (endpoint(config) === 'POST /auth/refresh') {
        return response<LoginResponse>(config, 200, loginResponse)
      }

      if (endpoint(config) === 'GET /users/me') {
        return response<AuthUser>(config, 200, adminUser)
      }

      if (endpoint(config) === 'GET /admin/venues') {
        return response<VenuePage>(config, 200, venuePage([venue]))
      }

      if (endpoint(config) === `GET /admin/events/${event.id}`) {
        return response<Event>(config, 200, event)
      }

      if (endpoint(config) === `GET /admin/venues/${venue.id}/seat-layout`) {
        return response<SeatLayout>(config, 200, layoutWithSection)
      }

      if (endpoint(config) === `GET /admin/events/${event.id}/sections`) {
        return response<EventSectionConfiguration>(config, 200, emptyEventSections)
      }

      if (endpoint(config) === `POST /admin/events/${event.id}/publish`) {
        publishSpy()
        return response<EventPublishResponse>(config, 200, {
          eventId: event.id,
          status: 'PUBLISHED',
          inventoryCount: 1,
        })
      }

      return rejectedResponse(config, 500, 'Unexpected request')
    })

    render(<App />)

    expect(await screen.findByRole('heading', { name: 'Edit event' })).toBeInTheDocument()
    await userEvent.click(await screen.findByRole('button', { name: /publish event/i }))

    expect(await screen.findByText(/Publish Opening Night/)).toBeInTheDocument()
    expect(publishSpy).not.toHaveBeenCalled()

    const dialog = screen.getByRole('dialog')
    await userEvent.click(within(dialog).getByRole('button', { name: 'Publish event' }))

    await waitFor(() => expect(publishSpy).toHaveBeenCalledOnce())
  }, 10000)

  it('displays backend publish errors', async () => {
    window.history.pushState({}, '', ROUTES.adminEventEdit(event.id))
    installApiMock((config) => {
      if (endpoint(config) === 'POST /auth/refresh') {
        return response<LoginResponse>(config, 200, loginResponse)
      }

      if (endpoint(config) === 'GET /users/me') {
        return response<AuthUser>(config, 200, adminUser)
      }

      if (endpoint(config) === 'GET /admin/venues') {
        return response<VenuePage>(config, 200, venuePage([venue]))
      }

      if (endpoint(config) === `GET /admin/events/${event.id}`) {
        return response<Event>(config, 200, event)
      }

      if (endpoint(config) === `GET /admin/venues/${venue.id}/seat-layout`) {
        return response<SeatLayout>(config, 200, layoutWithSection)
      }

      if (endpoint(config) === `GET /admin/events/${event.id}/sections`) {
        return response<EventSectionConfiguration>(config, 200, emptyEventSections)
      }

      if (endpoint(config) === `POST /admin/events/${event.id}/publish`) {
        return rejectedResponse(config, 409, 'Event section pricing is incomplete')
      }

      return rejectedResponse(config, 500, 'Unexpected request')
    })

    render(<App />)

    expect(await screen.findByRole('heading', { name: 'Edit event' })).toBeInTheDocument()
    await userEvent.click(await screen.findByRole('button', { name: /publish event/i }))
    const dialog = screen.getByRole('dialog')
    await userEvent.click(within(dialog).getByRole('button', { name: 'Publish event' }))

    expect(await screen.findByText('Event section pricing is incomplete')).toBeInTheDocument()
  }, 10000)

  it('blocks normal users from event administration pages', async () => {
    window.history.pushState({}, '', ROUTES.adminEvents)
    installApiMock((config) => {
      if (endpoint(config) === 'POST /auth/refresh') {
        return response<LoginResponse>(config, 200, loginResponse)
      }

      if (endpoint(config) === 'GET /users/me') {
        return response<AuthUser>(config, 200, authUser)
      }

      return rejectedResponse(config, 500, 'Unexpected request')
    })

    render(<App />)

    expect(await screen.findByText('403')).toBeInTheDocument()
    expect(screen.getByText('You are not authorized to access this page.')).toBeInTheDocument()
    expect(screen.queryByRole('heading', { name: 'Events' })).not.toBeInTheDocument()
  })

  it('logs out and clears the protected session', async () => {
    const user = userEvent.setup()
    const logoutSpy = vi.fn()
    window.history.pushState({}, '', ROUTES.events)
    installApiMock((config) => {
      if (endpoint(config) === 'POST /auth/refresh') {
        return response<LoginResponse>(config, 200, loginResponse)
      }

      if (endpoint(config) === 'GET /users/me') {
        return response<AuthUser>(config, 200, authUser)
      }

      if (endpoint(config) === 'GET /events') {
        return publicEventsResponse(config)
      }

      if (endpoint(config) === 'POST /auth/logout') {
        logoutSpy()
        return response(config, 204, undefined)
      }

      return rejectedResponse(config, 500, 'Unexpected request')
    })

    render(<App />)

    expect(await screen.findByRole('heading', { name: 'Events' })).toBeInTheDocument()
    await user.click(screen.getByText('Logout'))

    expect(await screen.findByRole('heading', { name: 'Login' })).toBeInTheDocument()
    expect(await screen.findByText('Logged out')).toBeInTheDocument()
    expect(logoutSpy).toHaveBeenCalledOnce()
  })

  it('stops after one refresh retry when session restoration fails', async () => {
    let refreshCalls = 0
    let currentUserCalls = 0
    window.history.pushState({}, '', ROUTES.admin)
    installApiMock((config) => {
      if (endpoint(config) === 'POST /auth/refresh') {
        refreshCalls += 1

        if (refreshCalls === 1) {
          return response<LoginResponse>(config, 200, {
            ...loginResponse,
            accessToken: 'restored-token',
          })
        }

        return rejectedResponse(config, 401, 'Invalid refresh token')
      }

      if (endpoint(config) === 'GET /users/me') {
        currentUserCalls += 1
        return rejectedResponse(config, 401, 'Invalid access token')
      }

      return rejectedResponse(config, 500, 'Unexpected request')
    })

    render(<App />)

    expect(await screen.findByRole('heading', { name: 'Login' })).toBeInTheDocument()
    expect(await screen.findByText('Session expired')).toBeInTheDocument()
    expect(refreshCalls).toBe(2)
    expect(currentUserCalls).toBe(1)
  })
})
