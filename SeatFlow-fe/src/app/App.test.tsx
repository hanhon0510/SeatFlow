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
  AdminSeatMap,
  Event,
  EventCancellation,
  EventReminder,
  EventPublishResponse,
  EventSalesReport,
  EventSectionConfiguration,
} from '../features/admin/events/types'
import type { AdminDashboard } from '../features/admin/dashboard/types'
import type { CheckInResult, CheckInSummary } from '../features/admin/check-in/types'
import type { RefundResponse } from '../features/admin/orders/types'
import type { OrderHistoryItem, OrderHistoryPage } from '../features/orders/types'
import type { EventSeatLayout, PublicEvent, PublicEventPage } from '../features/events/types'
import type { AuthUser, LoginResponse, RegisterResponse } from '../features/auth/types'
import type {
  OrderResponse,
  PaymentResponse,
  PaymentStatus,
  PromoCodeValidationRequest,
  PromoCodeValidationResponse,
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

const adminDashboard: AdminDashboard = {
  venues: { total: 2, active: 2, archived: 0, sections: 5, seats: 150 },
  events: {
    total: 3,
    draft: 1,
    published: 2,
    cancelled: 0,
    completed: 0,
    onSaleNow: 1,
    startingSoon: 1,
  },
  sales: {
    paidOrders: 9,
    pendingOrders: 2,
    ticketsIssued: 11,
    ticketsUsed: 4,
    revenue: [{ currency: 'VND', amount: 1500000, orderCount: 9 }],
  },
  upcomingEvents: [
    {
      eventId: '1a8d39c3-5639-491b-a9d8-b9b8fde28a65',
      name: 'Countdown 2027',
      venueName: 'Hoan Kiem Lake',
      startTime: '2026-12-31T13:00:00.000Z',
      salesEndTime: '2026-12-26T17:00:00.000Z',
      status: 'PUBLISHED',
      seatsTotal: 90,
      seatsSold: 18,
    },
  ],
  generatedAt: '2026-09-01T08:00:00.000Z',
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
  salesStatus: 'ON_SALE',
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

const eventSalesReport: EventSalesReport = {
  event: {
    id: event.id,
    venueId: venue.id,
    venueName: venue.name,
    venueTimezone: venue.timezone,
    name: event.name,
    description: event.description,
    startTime: event.startTime,
    salesStartTime: event.salesStartTime,
    salesEndTime: event.salesEndTime,
    status: 'PUBLISHED',
    salesStatus: 'ON_SALE',
  },
  inventory: {
    seatsTotal: 120,
    seatsAvailable: 70,
    seatsSold: 45,
    seatsBlocked: 5,
    seatsInCheckout: 3,
    inventoryValue: 12000000,
    soldValue: 4500000,
  },
  revenue: [
    {
      currency: 'VND',
      paidAmount: 4500000,
      paidOrders: 21,
      pendingAmount: 300000,
      pendingOrders: 2,
      refundedAmount: 0,
      refundedOrders: 0,
    },
  ],
  orders: {
    counts: { total: 25, paid: 21, pending: 2, failed: 1, cancelled: 1, refunded: 0 },
    recent: [
      {
        orderId: 'cbe8dcf4-2b0a-4c4b-9d3e-8b3e0f2a1f10',
        buyerEmail: 'buyer@example.com',
        status: 'PAID',
        totalAmount: 200000,
        currency: 'VND',
        seatCount: 2,
        createdAt: '2026-08-30T10:00:00.000Z',
        updatedAt: '2026-08-30T10:05:00.000Z',
      },
    ],
  },
  tickets: { issued: 45, active: 40, used: 5, cancelled: 0 },
  sections: [
    {
      venueSectionId: '1f89f6d1-3bc4-4d46-8c85-24d6db537d87',
      name: 'Orchestra',
      price: 100000,
      salesEnabled: true,
      seatsTotal: 120,
      seatsAvailable: 70,
      seatsSold: 45,
      seatsBlocked: 5,
      soldValue: 4500000,
    },
  ],
  dailySales: [{ date: '2026-08-30', paidOrders: 21, seatsSold: 45 }],
  generatedAt: '2026-09-01T08:00:00.000Z',
}

const adminSeatMap: AdminSeatMap = {
  eventId: event.id,
  sections: [
    {
      id: '1f89f6d1-3bc4-4d46-8c85-24d6db537d87',
      name: 'Orchestra',
      displayOrder: 1,
      rows: [
        {
          rowLabel: 'A',
          seats: [
            {
              eventSeatId: 'a7ff2e1f-0d1c-4c2a-9c6f-1c2c1d9a0001',
              seatLabel: 'A1',
              seatNumber: 1,
              price: 100000,
              permanentStatus: 'SOLD',
              status: 'SOLD',
              accessible: false,
            },
            {
              eventSeatId: 'a7ff2e1f-0d1c-4c2a-9c6f-1c2c1d9a0002',
              seatLabel: 'A2',
              seatNumber: 2,
              price: 100000,
              permanentStatus: 'AVAILABLE',
              status: 'HELD',
              accessible: false,
            },
            {
              eventSeatId: 'a7ff2e1f-0d1c-4c2a-9c6f-1c2c1d9a0003',
              seatLabel: 'A3',
              seatNumber: 3,
              price: 100000,
              permanentStatus: 'AVAILABLE',
              status: 'AVAILABLE',
              accessible: true,
            },
            {
              eventSeatId: 'a7ff2e1f-0d1c-4c2a-9c6f-1c2c1d9a0004',
              seatLabel: 'A4',
              seatNumber: 4,
              price: 100000,
              permanentStatus: 'BLOCKED',
              status: 'BLOCKED',
              accessible: false,
            },
          ],
        },
      ],
    },
  ],
  orders: [
    {
      eventSeatId: 'a7ff2e1f-0d1c-4c2a-9c6f-1c2c1d9a0001',
      orderId: 'cbe8dcf4-2b0a-4c4b-9d3e-8b3e0f2a1f10',
      buyerEmail: 'buyer@example.com',
      orderStatus: 'PAID',
      orderedAt: '2026-08-30T10:00:00.000Z',
    },
  ],
  generatedAt: '2026-09-01T08:00:00.000Z',
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

function rejectedWithCode(config: InternalAxiosRequestConfig, status: number, code: string, detail: string) {
  return Promise.reject(
    new AxiosError(detail, undefined, config, {}, response(config, status, { code, detail, title: detail })),
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
  orderSpy = vi.fn<(config: InternalAxiosRequestConfig) => void>(),
  cancelSpy = vi.fn<(config: InternalAxiosRequestConfig) => void>(),
  releaseSpy = vi.fn<(config: InternalAxiosRequestConfig) => void>(),
  paymentUnavailable = false,
  tickets,
}: {
  hold?: SeatHold
  status?: PaymentStatus
  paymentSpy?: PaymentSpy
  orderSpy?: PaymentSpy
  cancelSpy?: PaymentSpy
  releaseSpy?: PaymentSpy
  paymentUnavailable?: boolean
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
      orderSpy(config)
      return response<OrderResponse>(config, 201, order)
    }

    if (endpoint(config) === `POST /orders/${order.id}/payments`) {
      paymentSpy(config)
      if (paymentUnavailable) {
        return rejectedResponse(config, 503, 'Payment service unavailable')
      }
      return response<PaymentResponse>(config, 201, paymentResponse(order, status))
    }

    if (endpoint(config) === `POST /orders/${order.id}/cancel`) {
      cancelSpy(config)
      return response<OrderResponse>(config, 200, { ...order, status: 'CANCELLED' })
    }

    if (endpoint(config) === `DELETE /holds/${hold.holdId}`) {
      releaseSpy(config)
      return response(config, 204, undefined)
    }

    if (endpoint(config) === 'POST /promo-codes/validate') {
      const request = requestBody<PromoCodeValidationRequest>(config)
      if (request.code.toUpperCase() !== 'SAVE10') {
        return rejectedWithCode(config, 422, 'PROMO_CODE_REJECTED', 'rejected')
      }
      const discountAmount = request.subtotal / 10
      return response<PromoCodeValidationResponse>(config, 200, {
        code: 'SAVE10',
        discountType: 'PERCENT',
        discountValue: 10,
        discountAmount,
        total: request.subtotal - discountAmount,
      })
    }

    if (endpoint(config) === 'GET /users/me/tickets') {
      return response<Ticket[]>(config, 200, issuedTickets)
    }

    return rejectedResponse(config, 500, 'Unexpected request')
  })

  return { hold, reservation, order, tickets: issuedTickets, paymentSpy, orderSpy, cancelSpy, releaseSpy }
}

const refundableOrderId = eventSalesReport.orders.recent[0].orderId

function reportWithOrderStatus(status: EventSalesReport['orders']['recent'][number]['status']): EventSalesReport {
  return {
    ...eventSalesReport,
    orders: {
      ...eventSalesReport.orders,
      recent: eventSalesReport.orders.recent.map((order) => ({ ...order, status })),
    },
  }
}

const upcomingStartTime = '2099-09-01T19:00:00.000Z'

function reportForCancellation(
  status: EventSalesReport['event']['status'],
  paidOrders: number,
  startTime = upcomingStartTime,
): EventSalesReport {
  return {
    ...eventSalesReport,
    event: { ...eventSalesReport.event, status, startTime },
    orders: {
      ...eventSalesReport.orders,
      counts: { ...eventSalesReport.orders.counts, paid: paidOrders, pending: 2 },
    },
  }
}

function reminder(overrides: Partial<EventReminder> = {}): EventReminder {
  return {
    eventId: event.id,
    enabled: true,
    hoursBefore: 24,
    usingDefault: true,
    remindersSent: 0,
    remindAt: '2099-08-31T19:00:00.000Z',
    ...overrides,
  }
}

function cancellation(overrides: Partial<EventCancellation> = {}): EventCancellation {
  return {
    eventId: event.id,
    status: 'CANCELLED',
    cancelledAt: '2026-09-01T09:00:00.000Z',
    reason: null,
    refundedOrders: 0,
    remainingRefunds: 0,
    ordersNeedingAttention: 0,
    ...overrides,
  }
}

function installAdminEventDetailMock(
  report: () => EventSalesReport,
  refund: ApiHandler = (config) => rejectedResponse(config, 500, 'Unexpected request'),
  cancel: ApiHandler = (config) => rejectedResponse(config, 500, 'Unexpected request'),
  currentReminder: () => EventReminder = () => reminder(),
  updateReminder: ApiHandler = (config) => rejectedResponse(config, 500, 'Unexpected request'),
) {
  installApiMock((config) => {
    if (endpoint(config) === 'POST /auth/refresh') {
      return response<LoginResponse>(config, 200, loginResponse)
    }

    if (endpoint(config) === 'GET /users/me') {
      return response<AuthUser>(config, 200, adminUser)
    }

    if (endpoint(config) === `GET /admin/events/${event.id}/sales`) {
      return response<EventSalesReport>(config, 200, report())
    }

    if (endpoint(config) === `GET /admin/events/${event.id}/seat-map`) {
      return response<AdminSeatMap>(config, 200, adminSeatMap)
    }

    if (endpoint(config) === `POST /admin/orders/${refundableOrderId}/refund`) {
      return refund(config)
    }

    if (endpoint(config) === `POST /admin/events/${event.id}/cancel`) {
      return cancel(config)
    }

    if (endpoint(config) === `GET /admin/events/${event.id}/reminder`) {
      return response<EventReminder>(config, 200, currentReminder())
    }

    if (endpoint(config) === `PUT /admin/events/${event.id}/reminder`) {
      return updateReminder(config)
    }

    return rejectedResponse(config, 500, 'Unexpected request')
  })
}

function checkInSummary(checkedIn: number): CheckInSummary {
  return {
    eventId: event.id,
    eventName: event.name,
    valid: 10,
    checkedIn,
    remaining: 10 - checkedIn,
    recent: checkedIn === 0
      ? []
      : [{
        ticketId: 'f2287e38-c265-4841-b20b-57d2d7ba1b9d',
        sectionName: 'Orchestra',
        rowLabel: 'A',
        seatLabel: 'A1',
        buyerEmail: 'holder@example.com',
        checkedInAt: '2026-09-01T09:00:00.000Z',
      }],
  }
}

function checkInResult(outcome: CheckInResult['outcome'], overrides: Partial<CheckInResult> = {}): CheckInResult {
  return {
    outcome,
    ticketId: 'f2287e38-c265-4841-b20b-57d2d7ba1b9d',
    sectionName: 'Orchestra',
    rowLabel: 'A',
    seatLabel: 'A1',
    buyerEmail: 'holder@example.com',
    usedAt: '2026-09-01T09:00:00.000Z',
    ...overrides,
  }
}

function installCheckInMock(scan: ApiHandler, summary: () => CheckInSummary = () => checkInSummary(0)) {
  installApiMock((config) => {
    if (endpoint(config) === 'POST /auth/refresh') {
      return response<LoginResponse>(config, 200, loginResponse)
    }

    if (endpoint(config) === 'GET /users/me') {
      return response<AuthUser>(config, 200, adminUser)
    }

    if (endpoint(config) === `GET /admin/events/${event.id}/check-in`) {
      return response<CheckInSummary>(config, 200, summary())
    }

    if (endpoint(config) === `POST /admin/events/${event.id}/check-in`) {
      return scan(config)
    }

    return rejectedResponse(config, 500, 'Unexpected request')
  })
}

function orderHistoryItem(overrides: Partial<OrderHistoryItem> = {}): OrderHistoryItem {
  return {
    orderId: '2ae13b6b-df9b-4e08-99ae-f6b13f1997b0',
    status: 'PAID',
    totalAmount: 200000,
    discountAmount: 0,
    promoCode: null,
    currency: 'VND',
    createdAt: '2026-08-20T09:00:00.000Z',
    updatedAt: '2026-08-20T09:00:00.000Z',
    event: {
      id: publishedEvent.id,
      name: 'Opening Night',
      startTime: '2026-12-05T12:30:00.000Z',
      venueName: 'Concert Hall',
      venueTimezone: 'Asia/Ho_Chi_Minh',
      cancelled: false,
      cancellationReason: null,
    },
    seats: [
      { ticketId: 'f2287e38-c265-4841-b20b-57d2d7ba1b9d', ticketStatus: 'ACTIVE', sectionName: 'Orchestra', rowLabel: 'A', seatLabel: 'A1' },
      { ticketId: '0b2f4c1e-0a39-4c43-9c0d-57e8ac1c3b11', ticketStatus: 'ACTIVE', sectionName: 'Orchestra', rowLabel: 'A', seatLabel: 'A2' },
    ],
    refund: null,
    ...overrides,
  }
}

function orderHistoryPage(items: OrderHistoryItem[], overrides: Partial<OrderHistoryPage> = {}): OrderHistoryPage {
  return { items, page: 0, size: 10, totalItems: items.length, totalPages: items.length === 0 ? 0 : 1, ...overrides }
}

function installCustomerMock(handler: ApiHandler) {
  installApiMock((config) => {
    if (endpoint(config) === 'POST /auth/refresh') {
      return response<LoginResponse>(config, 200, loginResponse)
    }

    if (endpoint(config) === 'GET /users/me') {
      return response<AuthUser>(config, 200, authUser)
    }

    return handler(config)
  })
}

describe('App', () => {
  it('sends a signed-out visitor from the landing page to the catalogue', async () => {
    window.history.pushState({}, '', ROUTES.home)
    installApiMock((config) => {
      if (endpoint(config) === 'POST /auth/refresh') {
        return rejectedResponse(config, 401, 'Invalid refresh token')
      }

      if (endpoint(config) === 'GET /events') {
        return publicEventsResponse(config)
      }

      return rejectedResponse(config, 500, 'Unexpected request')
    })

    render(<App />)

    expect(await screen.findByRole('heading', { name: 'Events' })).toBeInTheDocument()
    expect(window.location.pathname).toBe(ROUTES.events)
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

  it('tags the sales status on catalogue cards and filters by it', async () => {
    const user = userEvent.setup()
    const listParams: unknown[] = []
    window.history.pushState({}, '', ROUTES.events)
    installApiMock((config) => {
      if (endpoint(config) === 'POST /auth/refresh') {
        return rejectedResponse(config, 401, 'Invalid refresh token')
      }

      if (endpoint(config) === 'GET /events') {
        listParams.push(config.params)
        return publicEventsResponse(config, [publishedEvent])
      }

      return rejectedResponse(config, 500, 'Unexpected request')
    })

    render(<App />)

    expect(await screen.findByText('On sale')).toBeInTheDocument()
    await waitFor(() =>
      expect(listParams).toContainEqual(expect.objectContaining({ sort: 'recommended' })),
    )
    // No status selected: the server applies its own default and drops events that have ended.
    expect(listParams.every((params) => !(params as { status?: string }).status)).toBe(true)

    await user.click(screen.getByRole('combobox', { name: 'Sales status' }))
    await user.click(await screen.findByTitle('Ended'))

    await waitFor(() =>
      expect(listParams).toContainEqual(expect.objectContaining({ status: 'ENDED' })),
    )
  }, 10000)

  it('replaces the seat map with a notice once an event has ended', async () => {
    const seatSpy = vi.fn()
    window.history.pushState({}, '', ROUTES.eventDetail(publishedEvent.id))
    installApiMock((config) => {
      if (endpoint(config) === 'POST /auth/refresh') {
        return rejectedResponse(config, 401, 'Invalid refresh token')
      }

      if (endpoint(config) === `GET /events/${publishedEvent.id}`) {
        return response<PublicEvent>(config, 200, { ...publishedEvent, salesStatus: 'ENDED' })
      }

      if (endpoint(config) === `GET /events/${publishedEvent.id}/seats`) {
        seatSpy()
        return response<EventSeatLayout>(config, 200, publicSeatLayout)
      }

      return rejectedResponse(config, 500, 'Unexpected request')
    })

    render(<App />)

    expect(await screen.findByRole('heading', { name: publishedEvent.name })).toBeInTheDocument()
    expect(screen.getByText('Ended')).toBeInTheDocument()
    expect(screen.getByText('This event has already taken place.')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /Seat A1/i })).not.toBeInTheDocument()
    expect(seatSpy).not.toHaveBeenCalled()
  })

  it('withholds seat selection until sales open', async () => {
    window.history.pushState({}, '', ROUTES.eventDetail(publishedEvent.id))
    installApiMock((config) => {
      if (endpoint(config) === 'POST /auth/refresh') {
        return rejectedResponse(config, 401, 'Invalid refresh token')
      }

      if (endpoint(config) === `GET /events/${publishedEvent.id}`) {
        return response<PublicEvent>(config, 200, { ...publishedEvent, salesStatus: 'UPCOMING' })
      }

      return rejectedResponse(config, 500, 'Unexpected request')
    })

    render(<App />)

    expect(await screen.findByText('Coming soon')).toBeInTheDocument()
    expect(screen.getByText(/Seat selection opens when sales start on/)).toBeInTheDocument()
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
    expect(screen.getByText('Wheelchair accessible')).toBeInTheDocument()
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
        return new Promise((resolve) => {
          setTimeout(() => resolve(response<SeatHold>(config, 201, hold)), 300)
        })
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

  it('refunds a paid order after the admin confirms and refreshes the report', async () => {
    const user = userEvent.setup()
    const refundSpy = vi.fn<(config: InternalAxiosRequestConfig) => void>()
    let refunded = false
    window.history.pushState({}, '', ROUTES.adminEventDetail(event.id))
    installAdminEventDetailMock(
      () => reportWithOrderStatus(refunded ? 'REFUNDED' : 'PAID'),
      (config) => {
        refundSpy(config)
        refunded = true
        return response<RefundResponse>(config, 200, {
          id: 'f3a4b0f5-6d1c-4c0e-8f58-0e1d6f6a9a01',
          orderId: refundableOrderId,
          orderStatus: 'REFUNDED',
          amount: 200000,
          currency: 'VND',
          reason: 'Duplicate purchase',
          refundedAt: '2026-09-01T09:00:00.000Z',
        })
      },
    )

    render(<App />)

    await user.click(await screen.findByRole('button', { name: 'Refund order for buyer@example.com' }))
    const dialog = await screen.findByRole('dialog')
    expect(within(dialog).getByText('This cannot be undone')).toBeInTheDocument()
    expect(refundSpy).not.toHaveBeenCalled()

    await user.type(within(dialog).getByLabelText('Reason (optional)'), '  Duplicate purchase ')
    await user.click(within(dialog).getByRole('button', { name: 'Refund 200,000 VND' }))

    await waitFor(() => expect(refundSpy).toHaveBeenCalledOnce())
    expect(requestBody(refundSpy.mock.calls[0][0] as InternalAxiosRequestConfig)).toEqual({
      reason: 'Duplicate purchase',
    })
    expect(await screen.findByText('Order refunded')).toBeInTheDocument()
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument())
    expect(await screen.findByText('REFUNDED')).toBeInTheDocument()
    expect(
      screen.queryByRole('button', { name: 'Refund order for buyer@example.com' }),
    ).not.toBeInTheDocument()
  }, 20000)

  it('only offers a refund for paid orders', async () => {
    window.history.pushState({}, '', ROUTES.adminEventDetail(event.id))
    installAdminEventDetailMock(() => reportWithOrderStatus('PENDING'))

    render(<App />)

    expect(await screen.findByText('PENDING')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /Refund order for/ })).not.toBeInTheDocument()
  }, 15000)

  it('keeps the refund dialog open and explains why when the refund is refused', async () => {
    const user = userEvent.setup()
    window.history.pushState({}, '', ROUTES.adminEventDetail(event.id))
    installAdminEventDetailMock(
      () => reportWithOrderStatus('PAID'),
      (config) => rejectedWithCode(config, 409, 'ORDER_NOT_REFUNDABLE', 'not refundable'),
    )

    render(<App />)

    await user.click(await screen.findByRole('button', { name: 'Refund order for buyer@example.com' }))
    const dialog = await screen.findByRole('dialog')
    await user.click(within(dialog).getByRole('button', { name: 'Refund 200,000 VND' }))

    expect(await within(dialog).findByText(/cannot be refunded/i)).toBeInTheDocument()
    expect(screen.getByRole('dialog')).toBeInTheDocument()

    await user.click(within(dialog).getByRole('button', { name: 'Keep order' }))
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument())
  }, 20000)

  it('cancels an event after the admin types its name and refunds in batches', async () => {
    const user = userEvent.setup()
    const cancelSpy = vi.fn<(config: InternalAxiosRequestConfig) => void>()
    const batches = [
      cancellation({ reason: 'Venue flooded', refundedOrders: 10, remainingRefunds: 11 }),
      cancellation({ reason: 'Venue flooded', refundedOrders: 21, remainingRefunds: 0 }),
    ]
    let calls = 0
    window.history.pushState({}, '', ROUTES.adminEventDetail(event.id))
    installAdminEventDetailMock(
      () => reportForCancellation(calls === 0 ? 'PUBLISHED' : 'CANCELLED', calls === 0 ? 21 : 0),
      undefined,
      (config) => {
        cancelSpy(config)
        return response<EventCancellation>(config, 200, batches[Math.min(calls++, batches.length - 1)])
      },
    )

    render(<App />)

    await user.click(await screen.findByRole('button', { name: 'Cancel event' }))
    const dialog = await screen.findByRole('dialog')
    expect(within(dialog).getByText(/21 paid orders will be refunded in full/)).toBeInTheDocument()
    expect(within(dialog).getByText(/2 unpaid checkouts will be closed/)).toBeInTheDocument()
    const confirm = within(dialog).getByRole('button', { name: 'Cancel event and refund' })
    expect(confirm).toBeDisabled()

    await user.type(within(dialog).getByLabelText(/Reason/), '  Venue flooded ')
    await user.type(within(dialog).getByLabelText(/to confirm/), 'Opening')
    expect(confirm).toBeDisabled()
    await user.type(within(dialog).getByLabelText(/to confirm/), ' Night')
    expect(confirm).toBeEnabled()
    await user.click(confirm)

    expect(await within(dialog).findByText('21 of 21 paid orders refunded.')).toBeInTheDocument()
    expect(within(dialog).getByText('The event is cancelled and everyone has been refunded')).toBeInTheDocument()
    expect(cancelSpy).toHaveBeenCalledTimes(2)
    expect(requestBody(cancelSpy.mock.calls[0][0] as InternalAxiosRequestConfig)).toEqual({
      reason: 'Venue flooded',
    })

    await user.click(within(dialog).getByRole('button', { name: 'Done' }))
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument())
    expect(await screen.findByText(/This event was cancelled/)).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Cancel event' })).not.toBeInTheDocument()
    expect(screen.queryByRole('link', { name: 'Edit event' })).not.toBeInTheDocument()
  }, 30000)

  it('stops and offers to continue when a batch refunds nothing', async () => {
    const user = userEvent.setup()
    const cancelSpy = vi.fn<(config: InternalAxiosRequestConfig) => void>()
    window.history.pushState({}, '', ROUTES.adminEventDetail(event.id))
    installAdminEventDetailMock(
      () => reportForCancellation('PUBLISHED', 3),
      undefined,
      (config) => {
        cancelSpy(config)
        return response<EventCancellation>(config, 200, cancellation({ refundedOrders: 1, remainingRefunds: 2 }))
      },
    )

    render(<App />)

    await user.click(await screen.findByRole('button', { name: 'Cancel event' }))
    const dialog = await screen.findByRole('dialog')
    await user.type(within(dialog).getByLabelText(/to confirm/), event.name)
    await user.click(within(dialog).getByRole('button', { name: 'Cancel event and refund' }))

    expect(await within(dialog).findByText('2 orders could not be refunded yet')).toBeInTheDocument()
    // One batch to start, one more that made no progress, then it gives up instead of looping.
    expect(cancelSpy).toHaveBeenCalledTimes(2)
    expect(within(dialog).getByText('1 of 3 paid orders refunded.')).toBeInTheDocument()
    expect(within(dialog).getByRole('button', { name: 'Continue refunds' })).toBeEnabled()
  }, 30000)

  it('flags orders whose tickets were already scanned', async () => {
    const user = userEvent.setup()
    window.history.pushState({}, '', ROUTES.adminEventDetail(event.id))
    installAdminEventDetailMock(
      () => reportForCancellation('PUBLISHED', 3),
      undefined,
      (config) => response<EventCancellation>(
        config,
        200,
        cancellation({ refundedOrders: 2, remainingRefunds: 0, ordersNeedingAttention: 1 }),
      ),
    )

    render(<App />)

    await user.click(await screen.findByRole('button', { name: 'Cancel event' }))
    const dialog = await screen.findByRole('dialog')
    await user.type(within(dialog).getByLabelText(/to confirm/), event.name)
    await user.click(within(dialog).getByRole('button', { name: 'Cancel event and refund' }))

    expect(await within(dialog).findByText('1 order has tickets that were already scanned')).toBeInTheDocument()
    expect(within(dialog).getByText('The event is cancelled')).toBeInTheDocument()
  }, 20000)

  it('keeps the form and explains when the event cannot be cancelled', async () => {
    const user = userEvent.setup()
    window.history.pushState({}, '', ROUTES.adminEventDetail(event.id))
    installAdminEventDetailMock(
      () => reportForCancellation('PUBLISHED', 0),
      undefined,
      (config) => rejectedWithCode(config, 409, 'EVENT_STATE_CONFLICT', 'The event state does not allow this operation.'),
    )

    render(<App />)

    await user.click(await screen.findByRole('button', { name: 'Cancel event' }))
    const dialog = await screen.findByRole('dialog')
    expect(within(dialog).getByText(/No orders have been paid yet/)).toBeInTheDocument()
    await user.type(within(dialog).getByLabelText(/to confirm/), event.name)
    await user.click(within(dialog).getByRole('button', { name: 'Cancel event and refund' }))

    expect(await within(dialog).findByText('The event state does not allow this operation.')).toBeInTheDocument()
    expect(within(dialog).getByRole('button', { name: 'Cancel event and refund' })).toBeEnabled()
  }, 20000)

  it('does not offer cancellation for an event that has already started', async () => {
    window.history.pushState({}, '', ROUTES.adminEventDetail(event.id))
    installAdminEventDetailMock(() => reportForCancellation('PUBLISHED', 3, '2020-01-01T19:00:00.000Z'))

    render(<App />)

    expect(await screen.findByRole('link', { name: /Check-in/ })).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Cancel event' })).not.toBeInTheDocument()
  }, 15000)

  it('lets an admin finish the refunds of an event that is already cancelled', async () => {
    const user = userEvent.setup()
    const cancelSpy = vi.fn<(config: InternalAxiosRequestConfig) => void>()
    let finished = false
    window.history.pushState({}, '', ROUTES.adminEventDetail(event.id))
    installAdminEventDetailMock(
      () => reportForCancellation('CANCELLED', finished ? 0 : 4),
      undefined,
      (config) => {
        cancelSpy(config)
        finished = true
        return response<EventCancellation>(config, 200, cancellation({ refundedOrders: 4, remainingRefunds: 0 }))
      },
    )

    render(<App />)

    expect(await screen.findByText(/4 paid orders are still waiting for a refund/)).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Cancel event' })).not.toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: 'Finish refunds' }))

    const dialog = await screen.findByRole('dialog')
    expect(within(dialog).queryByLabelText(/to confirm/)).not.toBeInTheDocument()
    await user.click(within(dialog).getByRole('button', { name: 'Refund remaining orders' }))

    expect(await within(dialog).findByText('4 of 4 paid orders refunded.')).toBeInTheDocument()
    expect(cancelSpy).toHaveBeenCalledOnce()
    await user.click(within(dialog).getByRole('button', { name: 'Done' }))
    expect(await screen.findByText(/its orders have been refunded/)).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Finish refunds' })).not.toBeInTheDocument()
  }, 30000)

  it('cancels the unpaid order before releasing the hold', async () => {
    const user = userEvent.setup()
    const { hold, cancelSpy, releaseSpy } = installCheckoutMock({ paymentUnavailable: true })
    window.history.pushState({}, '', ROUTES.checkout(hold.holdId))

    render(<App />)

    await screen.findByText('Reservation summary')
    await user.click(screen.getByRole('button', { name: 'Pay now' }))
    // Shown both inline and as a notification.
    expect((await screen.findAllByText('Payment service unavailable')).length).toBeGreaterThan(0)

    await user.click(screen.getByRole('button', { name: 'Release hold' }))
    const dialog = await screen.findByRole('dialog')
    await user.click(within(dialog).getByRole('button', { name: 'Release hold' }))

    await waitFor(() => expect(releaseSpy).toHaveBeenCalledOnce())
    expect(cancelSpy).toHaveBeenCalledOnce()
    expect(cancelSpy.mock.invocationCallOrder[0]).toBeLessThan(releaseSpy.mock.invocationCallOrder[0])
  }, 20000)

  it('releases a hold without cancelling anything when no order was started', async () => {
    const user = userEvent.setup()
    const { hold, cancelSpy, releaseSpy } = installCheckoutMock()
    window.history.pushState({}, '', ROUTES.checkout(hold.holdId))

    render(<App />)

    await screen.findByText('Reservation summary')
    await user.click(screen.getByRole('button', { name: 'Release hold' }))
    const dialog = await screen.findByRole('dialog')
    await user.click(within(dialog).getByRole('button', { name: 'Release hold' }))

    await waitFor(() => expect(releaseSpy).toHaveBeenCalledOnce())
    expect(cancelSpy).not.toHaveBeenCalled()
  }, 20000)

  it('applies a promo code at checkout and sends it with the order', async () => {
    const user = userEvent.setup()
    const { hold, orderSpy } = installCheckoutMock()
    window.history.pushState({}, '', ROUTES.checkout(hold.holdId))

    render(<App />)

    await screen.findByText('Reservation summary')
    await user.type(screen.getByLabelText('Promo code'), 'save10')
    await user.click(screen.getByRole('button', { name: 'Apply' }))

    expect(await screen.findByText(/SAVE10 applied, you save/)).toBeInTheDocument()
    expect(screen.getByText('Subtotal')).toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: 'Pay now' }))
    await waitFor(() => expect(orderSpy).toHaveBeenCalledOnce())
    expect(requestBody(orderSpy.mock.calls[0][0] as InternalAxiosRequestConfig)).toMatchObject({
      promoCode: 'SAVE10',
    })
  }, 20000)

  it('explains a rejected promo code and still lets the buyer pay in full', async () => {
    const user = userEvent.setup()
    const { hold, orderSpy } = installCheckoutMock()
    window.history.pushState({}, '', ROUTES.checkout(hold.holdId))

    render(<App />)

    await screen.findByText('Reservation summary')
    await user.type(screen.getByLabelText('Promo code'), 'nope')
    await user.click(screen.getByRole('button', { name: 'Apply' }))

    expect(await screen.findByText('This promo code cannot be applied to your purchase.')).toBeInTheDocument()
    expect(screen.queryByText('Subtotal')).not.toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: 'Pay now' }))
    await waitFor(() => expect(orderSpy).toHaveBeenCalledOnce())
    expect(requestBody<{ promoCode?: string }>(orderSpy.mock.calls[0][0] as InternalAxiosRequestConfig).promoCode)
      .toBeUndefined()
  }, 20000)

  it('shows a refunded ticket as no longer valid', async () => {
    const hold = activeSeatHold()
    const ticket: Ticket = {
      ...ticketForOrder(orderResponse(reservationResponse(hold), 'REFUNDED')),
      status: 'CANCELLED',
    }
    window.history.pushState({}, '', ROUTES.ticketDetail(ticket.id))
    installApiMock((config) => {
      if (endpoint(config) === 'POST /auth/refresh') {
        return response<LoginResponse>(config, 200, loginResponse)
      }

      if (endpoint(config) === 'GET /users/me') {
        return response<AuthUser>(config, 200, authUser)
      }

      if (endpoint(config) === `GET /tickets/${ticket.id}`) {
        return response<Ticket>(config, 200, ticket)
      }

      return rejectedResponse(config, 500, 'Unexpected request')
    })

    render(<App />)

    expect(await screen.findByText('This ticket is no longer valid')).toBeInTheDocument()
    expect(screen.getByText('CANCELLED')).toBeInTheDocument()
  }, 15000)

  it('admits a valid ticket, clears the field for the next scan and updates the count', async () => {
    const user = userEvent.setup()
    const scanSpy = vi.fn<(config: InternalAxiosRequestConfig) => void>()
    let checkedIn = 0
    window.history.pushState({}, '', ROUTES.adminEventCheckIn(event.id))
    installCheckInMock(
      (config) => {
        scanSpy(config)
        checkedIn = 1
        return response<CheckInResult>(config, 200, checkInResult('CHECKED_IN'))
      },
      () => checkInSummary(checkedIn),
    )

    render(<App />)

    const input = await screen.findByLabelText('Ticket code')
    expect(screen.getByText('of 10')).toBeInTheDocument()
    await user.type(input, '  seatflow:ticket:abc{Enter}')

    expect(await screen.findByText('Admit')).toBeInTheDocument()
    expect(within(screen.getByRole('status')).getByText('Orchestra, row A, seat A1')).toBeInTheDocument()
    expect(requestBody(scanSpy.mock.calls[0][0] as InternalAxiosRequestConfig)).toEqual({
      code: 'seatflow:ticket:abc',
    })
    expect(input).toHaveValue('')
    await waitFor(() => expect(input).toHaveFocus())
    // The progress and the latest-admissions list follow the scan.
    await waitFor(() => expect(screen.getAllByText('Orchestra, row A, seat A1')).toHaveLength(2))
    expect(screen.getByText('Still expected')).toBeInTheDocument()
  }, 20000)

  it.each([
    ['ALREADY_USED', 'Already checked in', /Do not admit/, /First scanned at/],
    ['CANCELLED', 'Ticket cancelled', /refunded/, null],
    ['WRONG_EVENT', 'Wrong event', /different event/, null],
    ['NOT_FOUND', 'Ticket not recognised', /Scan again/, null],
  ] as const)('tells the door staff not to admit on %s', async (outcome, headline, advice, detail) => {
    const user = userEvent.setup()
    window.history.pushState({}, '', ROUTES.adminEventCheckIn(event.id))
    const refused = outcome === 'WRONG_EVENT' || outcome === 'NOT_FOUND'
    installCheckInMock((config) => response<CheckInResult>(
      config,
      200,
      refused
        ? checkInResult(outcome, {
          ticketId: null, sectionName: null, rowLabel: null, seatLabel: null, buyerEmail: null, usedAt: null,
        })
        : checkInResult(outcome),
    ))

    render(<App />)

    await user.type(await screen.findByLabelText('Ticket code'), 'x'.repeat(43))
    await user.click(screen.getByRole('button', { name: 'Check in' }))

    expect(await screen.findByText(headline)).toBeInTheDocument()
    expect(screen.getByText(advice)).toBeInTheDocument()
    if (detail) {
      expect(screen.getByText(detail)).toBeInTheDocument()
    }
    expect(screen.getByRole('status')).toHaveTextContent(headline)
  }, 20000)

  it('does not send a blank scan', async () => {
    const user = userEvent.setup()
    const scanSpy = vi.fn<(config: InternalAxiosRequestConfig) => void>()
    window.history.pushState({}, '', ROUTES.adminEventCheckIn(event.id))
    installCheckInMock((config) => {
      scanSpy(config)
      return response<CheckInResult>(config, 200, checkInResult('CHECKED_IN'))
    })

    render(<App />)

    const input = await screen.findByLabelText('Ticket code')
    expect(screen.getByRole('button', { name: 'Check in' })).toBeDisabled()
    await user.type(input, '   {Enter}')

    expect(scanSpy).not.toHaveBeenCalled()
  }, 15000)

  it('keeps what was typed and says so when a scan cannot be processed', async () => {
    const user = userEvent.setup()
    window.history.pushState({}, '', ROUTES.adminEventCheckIn(event.id))
    installCheckInMock((config) => rejectedResponse(config, 503, 'Scanning is unavailable'))

    render(<App />)

    const input = await screen.findByLabelText('Ticket code')
    await user.type(input, 'abc{Enter}')

    expect(await screen.findByText('Scanning is unavailable')).toBeInTheDocument()
    expect(input).toHaveValue('abc')
    expect(screen.queryByRole('status')).not.toBeInTheDocument()
  }, 15000)

  it('links to check-in from a published event', async () => {
    window.history.pushState({}, '', ROUTES.adminEventDetail(event.id))
    installAdminEventDetailMock(() => eventSalesReport)

    render(<App />)

    const link = await screen.findByRole('link', { name: /Check-in/ })
    expect(link).toHaveAttribute('href', ROUTES.adminEventCheckIn(event.id))
  }, 15000)

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

  it('links to password reset from the login page', async () => {
    const user = userEvent.setup()
    window.history.pushState({}, '', ROUTES.login)

    render(<App />)

    await user.click(screen.getByRole('link', { name: 'Forgot your password?' }))

    expect(await screen.findByRole('heading', { name: 'Forgot your password?' })).toBeInTheDocument()
  })

  it('asks for a reset link and says the same thing whether or not the account exists', async () => {
    const user = userEvent.setup()
    const forgotSpy = vi.fn()
    window.history.pushState({}, '', ROUTES.forgotPassword)
    installApiMock((config) => {
      if (endpoint(config) === 'POST /auth/refresh') {
        return rejectedResponse(config, 401, 'Invalid refresh token')
      }

      if (endpoint(config) === 'POST /auth/forgot-password') {
        forgotSpy(requestBody(config))
        return response<null>(config, 202, null)
      }

      return rejectedResponse(config, 500, 'Unexpected request')
    })

    render(<App />)

    await user.type(screen.getByLabelText('Email'), '  someone@example.com ')
    await user.click(screen.getByRole('button', { name: 'Send reset link' }))

    expect(await screen.findByText(/If an account exists for someone@example.com/)).toBeInTheDocument()
    expect(forgotSpy).toHaveBeenCalledWith({ email: 'someone@example.com' })
    expect(screen.getByRole('link', { name: 'Back to log in' })).toBeInTheDocument()
  })

  it('validates the email before asking for a reset link', async () => {
    const user = userEvent.setup()
    window.history.pushState({}, '', ROUTES.forgotPassword)

    render(<App />)

    await user.click(screen.getByRole('button', { name: 'Send reset link' }))

    expect(await screen.findByText('Email is required')).toBeInTheDocument()
  })

  it('tells the visitor when too many reset links were requested', async () => {
    const user = userEvent.setup()
    window.history.pushState({}, '', ROUTES.forgotPassword)
    installApiMock((config) => {
      if (endpoint(config) === 'POST /auth/refresh') {
        return rejectedResponse(config, 401, 'Invalid refresh token')
      }

      return rejectedWithCode(config, 429, 'RATE_LIMIT_EXCEEDED', 'slow down')
    })

    render(<App />)

    await user.type(screen.getByLabelText('Email'), 'someone@example.com')
    await user.click(screen.getByRole('button', { name: 'Send reset link' }))

    expect(await screen.findByText(/Too many attempts/)).toBeInTheDocument()
  })

  it('chooses a new password from the emailed link', async () => {
    const user = userEvent.setup()
    const resetSpy = vi.fn()
    window.history.pushState({}, '', `${ROUTES.resetPassword}?token=abc_DEF-123`)
    installApiMock((config) => {
      if (endpoint(config) === 'POST /auth/refresh') {
        return rejectedResponse(config, 401, 'Invalid refresh token')
      }

      if (endpoint(config) === 'POST /auth/reset-password') {
        resetSpy(requestBody(config))
        return response<null>(config, 204, null)
      }

      return rejectedResponse(config, 500, 'Unexpected request')
    })

    render(<App />)

    await user.type(screen.getByLabelText('New password'), 'BrandNewPassw0rd!')
    await user.type(screen.getByLabelText('Confirm new password'), 'BrandNewPassw0rd!')
    await user.click(screen.getByRole('button', { name: 'Change password' }))

    expect(await screen.findByText('Your password has been changed')).toBeInTheDocument()
    expect(resetSpy).toHaveBeenCalledWith({ token: 'abc_DEF-123', password: 'BrandNewPassw0rd!' })
    expect(screen.getByRole('link', { name: 'Log in' })).toHaveAttribute('href', ROUTES.login)
  })

  it('does not send mismatching or weak new passwords', async () => {
    const user = userEvent.setup()
    const resetSpy = vi.fn()
    window.history.pushState({}, '', `${ROUTES.resetPassword}?token=abc`)
    installApiMock((config) => {
      if (endpoint(config) === 'POST /auth/refresh') {
        return rejectedResponse(config, 401, 'Invalid refresh token')
      }
      resetSpy()
      return rejectedResponse(config, 500, 'Unexpected request')
    })

    render(<App />)

    await user.type(screen.getByLabelText('New password'), 'weak')
    await user.type(screen.getByLabelText('Confirm new password'), 'different')
    await user.click(screen.getByRole('button', { name: 'Change password' }))

    expect(await screen.findByText('Password must be at least 12 characters')).toBeInTheDocument()
    expect(await screen.findByText('The passwords do not match')).toBeInTheDocument()
    expect(resetSpy).not.toHaveBeenCalled()
  })

  it('explains an expired link and offers a new one', async () => {
    const user = userEvent.setup()
    window.history.pushState({}, '', `${ROUTES.resetPassword}?token=old`)
    installApiMock((config) => {
      if (endpoint(config) === 'POST /auth/refresh') {
        return rejectedResponse(config, 401, 'Invalid refresh token')
      }

      return rejectedWithCode(config, 400, 'INVALID_PASSWORD_RESET_TOKEN', 'expired')
    })

    render(<App />)

    await user.type(screen.getByLabelText('New password'), 'BrandNewPassw0rd!')
    await user.type(screen.getByLabelText('Confirm new password'), 'BrandNewPassw0rd!')
    await user.click(screen.getByRole('button', { name: 'Change password' }))

    expect(await screen.findByText('This password reset link is invalid or has expired.')).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Send me a new link' })).toHaveAttribute(
      'href',
      ROUTES.forgotPassword,
    )
  })

  it('shows a helpful message when the reset link has no token', async () => {
    window.history.pushState({}, '', ROUTES.resetPassword)

    render(<App />)

    expect(await screen.findByText('This link is incomplete')).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Send me a new link' })).toBeInTheDocument()
  })

  it('shows the default reminder time and lets an admin change how many hours before the show', async () => {
    const user = userEvent.setup()
    const updateSpy = vi.fn()
    let saved: EventReminder = reminder()
    window.history.pushState({}, '', ROUTES.adminEventDetail(event.id))
    installAdminEventDetailMock(
      () => eventSalesReport,
      undefined,
      undefined,
      () => saved,
      (config) => {
        const body = requestBody(config) as { enabled: boolean; hoursBefore: number }
        updateSpy(body)
        saved = reminder({ ...body, usingDefault: false })
        return response<EventReminder>(config, 200, saved)
      },
    )

    render(<App />)

    const hours = await screen.findByLabelText('Hours before the event starts')
    expect(hours).toHaveValue('24')
    expect(screen.getByText(/the default, until you save a choice/)).toBeInTheDocument()
    const save = screen.getByRole('button', { name: 'Save reminder' })
    expect(save).toBeDisabled()

    await user.clear(hours)
    await user.type(hours, '72')
    expect(save).toBeEnabled()
    await user.click(save)

    await waitFor(() => expect(updateSpy).toHaveBeenCalledWith({ enabled: true, hoursBefore: 72 }))
    expect(await screen.findByText('Reminder saved')).toBeInTheDocument()
    expect(await screen.findByText(/72 hours \(3 days\) before the start/)).toBeInTheDocument()
    expect(screen.queryByText(/the default, until you save a choice/)).not.toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Save reminder' })).toBeDisabled()
  }, 30000)

  it('lets an admin turn reminders off for an event', async () => {
    const user = userEvent.setup()
    const updateSpy = vi.fn()
    window.history.pushState({}, '', ROUTES.adminEventDetail(event.id))
    installAdminEventDetailMock(
      () => eventSalesReport,
      undefined,
      undefined,
      () => reminder({ remindersSent: 4 }),
      (config) => {
        updateSpy(requestBody(config))
        return response<EventReminder>(config, 200, reminder({ enabled: false, usingDefault: false }))
      },
    )

    render(<App />)

    expect(await screen.findByText(/4 sent so far/)).toBeInTheDocument()
    await user.click(await screen.findByRole('switch', { name: 'Send a reminder email before the event' }))
    expect(screen.getByLabelText('Hours before the event starts')).toBeDisabled()
    await user.click(screen.getByRole('button', { name: 'Save reminder' }))

    await waitFor(() => expect(updateSpy).toHaveBeenCalledWith({ enabled: false, hoursBefore: 24 }))
    expect(await screen.findByText('Buyers will not get a reminder for this event.')).toBeInTheDocument()
  }, 30000)

  it('will not save a reminder time outside the allowed range', async () => {
    const user = userEvent.setup()
    window.history.pushState({}, '', ROUTES.adminEventDetail(event.id))
    installAdminEventDetailMock(() => eventSalesReport)

    render(<App />)

    const hours = await screen.findByLabelText('Hours before the event starts')
    await user.clear(hours)

    expect(await screen.findByText(/Enter a whole number of hours between 1 and 336/)).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Save reminder' })).toBeDisabled()
  }, 20000)

  it('does not offer reminders for a cancelled event', async () => {
    window.history.pushState({}, '', ROUTES.adminEventDetail(event.id))
    installAdminEventDetailMock(() => reportForCancellation('CANCELLED', 0))

    render(<App />)

    expect(await screen.findByText(/This event was cancelled/)).toBeInTheDocument()
    expect(screen.queryByText('Reminder email')).not.toBeInTheDocument()
  }, 15000)

  it('lists the buyer orders with their status, seats and totals', async () => {
    window.history.pushState({}, '', ROUTES.orders)
    installCustomerMock((config) => {
      if (endpoint(config) === 'GET /users/me/order-history') {
        return response<OrderHistoryPage>(config, 200, orderHistoryPage([
          orderHistoryItem({ discountAmount: 20000, promoCode: 'SPRING', totalAmount: 180000 }),
          orderHistoryItem({
            orderId: 'aaaaaaaa-0000-4000-8000-000000000001',
            status: 'PENDING',
            seats: [{ ticketId: null, ticketStatus: null, sectionName: 'Balcony', rowLabel: 'B', seatLabel: 'B7' }],
          }),
        ]))
      }

      return rejectedResponse(config, 500, 'Unexpected request')
    })

    render(<App />)

    expect(await screen.findByRole('heading', { name: 'My orders' })).toBeInTheDocument()
    const paid = await screen.findByLabelText('Order 2AE13B6B')
    expect(within(paid).getByText('Opening Night')).toBeInTheDocument()
    expect(within(paid).getByText('Paid')).toBeInTheDocument()
    expect(within(paid).getByText(/Orchestra, row A, seat A1/)).toBeInTheDocument()
    expect(within(paid).getByText(/Orchestra, row A, seat A2/)).toBeInTheDocument()
    expect(within(paid).getByText('180,000 VND')).toBeInTheDocument()
    expect(within(paid).getByText(/saved 20,000 VND with SPRING/)).toBeInTheDocument()
    expect(within(paid).getByRole('link', { name: 'View tickets' })).toHaveAttribute('href', ROUTES.tickets)

    const pending = screen.getByLabelText('Order AAAAAAAA')
    expect(within(pending).getByText('Awaiting payment')).toBeInTheDocument()
    expect(within(pending).getByText('This order is waiting for payment')).toBeInTheDocument()
    expect(within(pending).queryByRole('button', { name: 'Receipt' })).not.toBeInTheDocument()
  }, 20000)

  it('explains a refund, including one caused by a cancelled event', async () => {
    window.history.pushState({}, '', ROUTES.orders)
    installCustomerMock((config) => {
      if (endpoint(config) === 'GET /users/me/order-history') {
        return response<OrderHistoryPage>(config, 200, orderHistoryPage([
          orderHistoryItem({
            status: 'REFUNDED',
            refund: { amount: 200000, reason: 'Duplicate purchase', refundedAt: '2026-08-25T09:00:00.000Z' },
            seats: [{ ticketId: 't1', ticketStatus: 'CANCELLED', sectionName: 'Orchestra', rowLabel: 'A', seatLabel: 'A1' }],
          }),
          orderHistoryItem({
            orderId: 'bbbbbbbb-0000-4000-8000-000000000002',
            status: 'REFUNDED',
            event: {
              id: 'e2', name: 'Summer Show', startTime: '2026-12-06T12:30:00.000Z', venueName: 'Open Air',
              venueTimezone: 'Asia/Ho_Chi_Minh', cancelled: true, cancellationReason: 'Venue flooded',
            },
            refund: { amount: 100000, reason: 'Event cancelled: Venue flooded', refundedAt: '2026-08-26T09:00:00.000Z' },
          }),
          orderHistoryItem({
            orderId: 'cccccccc-0000-4000-8000-000000000003',
            status: 'PAID',
            event: {
              id: 'e3', name: 'Waiting Show', startTime: '2026-12-07T12:30:00.000Z', venueName: 'Hall',
              venueTimezone: 'Asia/Ho_Chi_Minh', cancelled: true, cancellationReason: null,
            },
          }),
        ]))
      }

      return rejectedResponse(config, 500, 'Unexpected request')
    })

    render(<App />)

    const manual = await screen.findByLabelText('Order 2AE13B6B')
    expect(within(manual).getByText('Refunded')).toBeInTheDocument()
    expect(within(manual).getByText(/Refunded 200,000 VND on/)).toBeInTheDocument()
    expect(within(manual).getByText('Reason: Duplicate purchase')).toBeInTheDocument()
    expect(within(manual).getByText('CANCELLED')).toBeInTheDocument()

    const cancelled = screen.getByLabelText('Order BBBBBBBB')
    expect(within(cancelled).getByText('This event was cancelled')).toBeInTheDocument()
    expect(within(cancelled).getByText(/You were refunded 100,000 VND on .*Reason: Venue flooded\./)).toBeInTheDocument()

    const waiting = screen.getByLabelText('Order CCCCCCCC')
    expect(within(waiting).getByText('Your refund is being processed.')).toBeInTheDocument()
  }, 20000)

  it('invites a buyer with no orders to browse events', async () => {
    window.history.pushState({}, '', ROUTES.orders)
    installCustomerMock((config) => {
      if (endpoint(config) === 'GET /users/me/order-history') {
        return response<OrderHistoryPage>(config, 200, orderHistoryPage([]))
      }

      return rejectedResponse(config, 500, 'Unexpected request')
    })

    render(<App />)

    expect(await screen.findByText('You have not placed any orders yet')).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Browse events' })).toHaveAttribute('href', ROUTES.events)
  }, 15000)

  it('moves between pages of orders', async () => {
    const user = userEvent.setup()
    const pageSpy = vi.fn()
    window.history.pushState({}, '', ROUTES.orders)
    installCustomerMock((config) => {
      if (endpoint(config) === 'GET /users/me/order-history') {
        const page = Number((config.params as { page: number }).page)
        pageSpy(page)
        return response<OrderHistoryPage>(config, 200, orderHistoryPage(
          [orderHistoryItem({ orderId: page === 0 ? '11111111-0000-4000-8000-000000000000' : '22222222-0000-4000-8000-000000000000' })],
          { page, totalItems: 11, totalPages: 2 },
        ))
      }

      return rejectedResponse(config, 500, 'Unexpected request')
    })

    render(<App />)

    expect(await screen.findByLabelText('Order 11111111')).toBeInTheDocument()
    await user.click(screen.getByTitle('2'))

    expect(await screen.findByLabelText('Order 22222222')).toBeInTheDocument()
    expect(pageSpy).toHaveBeenCalledWith(1)
  }, 20000)

  it('shows a receipt that can be printed', async () => {
    const user = userEvent.setup()
    const printSpy = vi.fn()
    window.print = printSpy
    window.history.pushState({}, '', ROUTES.orders)
    installCustomerMock((config) => {
      if (endpoint(config) === 'GET /users/me/order-history') {
        return response<OrderHistoryPage>(config, 200, orderHistoryPage([
          orderHistoryItem({ discountAmount: 20000, promoCode: 'SPRING', totalAmount: 180000 }),
        ]))
      }

      return rejectedResponse(config, 500, 'Unexpected request')
    })

    render(<App />)

    await user.click(await screen.findByRole('button', { name: 'Receipt' }))
    const dialog = await screen.findByRole('dialog')
    expect(within(dialog).getByText('SeatFlow receipt')).toBeInTheDocument()
    expect(within(dialog).getByText('Subtotal')).toBeInTheDocument()
    expect(within(dialog).getByText('200,000 VND')).toBeInTheDocument()
    expect(within(dialog).getByText('Discount (SPRING)')).toBeInTheDocument()
    expect(within(dialog).getByText('-20,000 VND')).toBeInTheDocument()
    expect(within(dialog).getByText('180,000 VND')).toBeInTheDocument()

    await user.click(within(dialog).getByRole('button', { name: /Print/ }))
    expect(printSpy).toHaveBeenCalledOnce()
  }, 20000)

  it('says so when the orders cannot be loaded', async () => {
    window.history.pushState({}, '', ROUTES.orders)
    installCustomerMock((config) => rejectedResponse(config, 503, 'Orders are unavailable'))

    render(<App />)

    expect(await screen.findByText('Orders are unavailable')).toBeInTheDocument()
  }, 15000)

  it('links to orders and the account from the customer menu', async () => {
    window.history.pushState({}, '', ROUTES.events)
    installCustomerMock((config) => {
      if (endpoint(config) === 'GET /events') {
        return response<PublicEventPage>(config, 200, publicEventPage([publishedEvent]))
      }

      return rejectedResponse(config, 500, 'Unexpected request')
    })

    render(<App />)

    const nav = (await screen.findAllByRole('menu'))[0]
    expect(await within(nav).findByRole('link', { name: 'Orders' })).toHaveAttribute('href', ROUTES.orders)
    expect(within(nav).getByRole('link', { name: 'Account' })).toHaveAttribute('href', ROUTES.account)
  }, 20000)

  it('offers admins the account page but not the buyer order list', async () => {
    window.history.pushState({}, '', ROUTES.admin)
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

    // The menu collapses into an overflow list in jsdom, so hidden links count too. Wait for an
    // admin-only entry first: the menu briefly shows the customer entries while the user loads.
    await screen.findByRole('link', { name: 'Event setup', hidden: true })
    expect(await screen.findByRole('link', { name: 'Account', hidden: true })).toHaveAttribute('href', ROUTES.account)
    expect(screen.queryByRole('link', { name: 'Orders', hidden: true })).not.toBeInTheDocument()
  }, 20000)

  it('changes the password and clears the form', async () => {
    const user = userEvent.setup()
    const changeSpy = vi.fn()
    window.history.pushState({}, '', ROUTES.account)
    installCustomerMock((config) => {
      if (endpoint(config) === 'POST /users/me/password') {
        changeSpy(requestBody(config))
        return response<null>(config, 204, null)
      }

      return rejectedResponse(config, 500, 'Unexpected request')
    })

    render(<App />)

    expect(await screen.findByText(authUser.email)).toBeInTheDocument()
    await user.type(screen.getByLabelText('Current password'), 'StrongPassword123!')
    await user.type(screen.getByLabelText('New password'), 'BrandNewPassw0rd!')
    await user.type(screen.getByLabelText('Confirm new password'), 'BrandNewPassw0rd!')
    await user.click(screen.getByRole('button', { name: 'Change password' }))

    expect(await screen.findByText('Your password has been changed')).toBeInTheDocument()
    expect(changeSpy).toHaveBeenCalledWith({
      currentPassword: 'StrongPassword123!',
      newPassword: 'BrandNewPassw0rd!',
    })
    expect(screen.getByLabelText('Current password')).toHaveValue('')
  }, 20000)

  it('explains when the current password is wrong', async () => {
    const user = userEvent.setup()
    window.history.pushState({}, '', ROUTES.account)
    installCustomerMock((config) => {
      if (endpoint(config) === 'POST /users/me/password') {
        return rejectedWithCode(config, 400, 'INVALID_CURRENT_PASSWORD', 'wrong')
      }

      return rejectedResponse(config, 500, 'Unexpected request')
    })

    render(<App />)

    await user.type(await screen.findByLabelText('Current password'), 'NotMyPassw0rd!x')
    await user.type(screen.getByLabelText('New password'), 'BrandNewPassw0rd!')
    await user.type(screen.getByLabelText('Confirm new password'), 'BrandNewPassw0rd!')
    await user.click(screen.getByRole('button', { name: 'Change password' }))

    expect(await screen.findByText('Your current password is incorrect.')).toBeInTheDocument()
    expect(screen.queryByText('Your password has been changed')).not.toBeInTheDocument()
  }, 20000)

  it('does not send a weak or mismatching new password', async () => {
    const user = userEvent.setup()
    const changeSpy = vi.fn()
    window.history.pushState({}, '', ROUTES.account)
    installCustomerMock((config) => {
      if (endpoint(config) === 'POST /users/me/password') {
        changeSpy()
      }

      return rejectedResponse(config, 500, 'Unexpected request')
    })

    render(<App />)

    await user.type(await screen.findByLabelText('Current password'), 'StrongPassword123!')
    await user.type(screen.getByLabelText('New password'), 'weak')
    await user.type(screen.getByLabelText('Confirm new password'), 'different')
    await user.click(screen.getByRole('button', { name: 'Change password' }))

    expect(await screen.findByText('Password must be at least 12 characters')).toBeInTheDocument()
    expect(await screen.findByText('The passwords do not match')).toBeInTheDocument()
    expect(changeSpy).not.toHaveBeenCalled()
  }, 20000)

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

  it('shows the dashboard on the admin landing page', async () => {
    window.history.pushState({}, '', ROUTES.home)
    installApiMock((config) => {
      if (endpoint(config) === 'POST /auth/refresh') {
        return response<LoginResponse>(config, 200, loginResponse)
      }

      if (endpoint(config) === 'GET /users/me') {
        return response<AuthUser>(config, 200, adminUser)
      }

      if (endpoint(config) === 'GET /admin/dashboard') {
        return response<AdminDashboard>(config, 200, adminDashboard)
      }

      return rejectedResponse(config, 500, 'Unexpected request')
    })

    render(<App />)

    expect(await screen.findByRole('heading', { name: 'Dashboard' })).toBeInTheDocument()
    expect(screen.getByText('5 sections, 150 seats')).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Countdown 2027' })).toBeInTheDocument()
    // The catalogue redirect for everyone else must not catch an admin.
    expect(window.location.pathname).toBe(ROUTES.home)
  })

  it('keeps admins out of the seat booking flow', async () => {
    window.history.pushState({}, '', ROUTES.eventDetail(publishedEvent.id))
    installApiMock((config) => {
      if (endpoint(config) === 'POST /auth/refresh') {
        return response<LoginResponse>(config, 200, loginResponse)
      }

      if (endpoint(config) === 'GET /users/me') {
        return response<AuthUser>(config, 200, adminUser)
      }

      if (endpoint(config) === 'GET /admin/dashboard') {
        return response<AdminDashboard>(config, 200, adminDashboard)
      }

      return rejectedResponse(config, 500, 'Unexpected request')
    })

    render(<App />)

    expect(await screen.findByRole('heading', { name: 'Dashboard' })).toBeInTheDocument()
    expect(screen.queryByText('Select seats')).not.toBeInTheDocument()
    expect(screen.queryByRole('link', { name: 'Events' })).not.toBeInTheDocument()
    expect(screen.queryByRole('link', { name: 'Tickets' })).not.toBeInTheDocument()
  })

  it('sends an admin away from the buyer ticket list', async () => {
    window.history.pushState({}, '', ROUTES.tickets)
    installApiMock((config) => {
      if (endpoint(config) === 'POST /auth/refresh') {
        return response<LoginResponse>(config, 200, loginResponse)
      }

      if (endpoint(config) === 'GET /users/me') {
        return response<AuthUser>(config, 200, adminUser)
      }

      if (endpoint(config) === 'GET /admin/dashboard') {
        return response<AdminDashboard>(config, 200, adminDashboard)
      }

      return rejectedResponse(config, 500, 'Unexpected request')
    })

    render(<App />)

    expect(await screen.findByRole('heading', { name: 'Dashboard' })).toBeInTheDocument()
    expect(screen.queryByRole('heading', { name: 'Tickets' })).not.toBeInTheDocument()
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
    // The accessibility switch is left untouched: new seats are accessible by default.
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
    expect(
      await screen.findByRole('button', { name: /Seat A1, wheelchair accessible/ }),
    ).toBeInTheDocument()
  }, 30000)

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
  }, 30000)

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

  it('shows how a single event is selling on its admin detail screen', async () => {
    window.history.pushState({}, '', ROUTES.adminEventDetail(event.id))
    installApiMock((config) => {
      if (endpoint(config) === 'POST /auth/refresh') {
        return response<LoginResponse>(config, 200, loginResponse)
      }

      if (endpoint(config) === 'GET /users/me') {
        return response<AuthUser>(config, 200, adminUser)
      }

      if (endpoint(config) === `GET /admin/events/${event.id}/sales`) {
        return response<EventSalesReport>(config, 200, eventSalesReport)
      }

      if (endpoint(config) === `GET /admin/events/${event.id}/seat-map`) {
        return response<AdminSeatMap>(config, 200, adminSeatMap)
      }

      return rejectedResponse(config, 500, 'Unexpected request')
    })

    render(<App />)

    expect(await screen.findByRole('heading', { name: event.name })).toBeInTheDocument()
    expect(screen.getByText('On sale')).toBeInTheDocument()
    expect(screen.getByText('Seats sold')).toBeInTheDocument()
    expect(screen.getByText('of 120')).toBeInTheDocument()
    expect(screen.getByText('3 in checkout, 5 blocked')).toBeInTheDocument()
    expect(screen.getByText('5 checked in, 0 cancelled')).toBeInTheDocument()
    // Scoped to the pricing table: the seat map below names its sections too.
    expect(screen.getByRole('cell', { name: 'Orchestra' })).toBeInTheDocument()
    expect(screen.getByText('buyer@example.com')).toBeInTheDocument()
    expect(screen.getByText('45 seats, 21 orders')).toBeInTheDocument()
  }, 10000)

  it('shows the status of every seat and who ordered it', async () => {
    window.history.pushState({}, '', ROUTES.adminEventDetail(event.id))
    installApiMock((config) => {
      if (endpoint(config) === 'POST /auth/refresh') {
        return response<LoginResponse>(config, 200, loginResponse)
      }

      if (endpoint(config) === 'GET /users/me') {
        return response<AuthUser>(config, 200, adminUser)
      }

      if (endpoint(config) === `GET /admin/events/${event.id}/sales`) {
        return response<EventSalesReport>(config, 200, eventSalesReport)
      }

      if (endpoint(config) === `GET /admin/events/${event.id}/seat-map`) {
        return response<AdminSeatMap>(config, 200, adminSeatMap)
      }

      return rejectedResponse(config, 500, 'Unexpected request')
    })

    render(<App />)

    expect(await screen.findByLabelText('Orchestra seat map')).toBeInTheDocument()

    // Each seat spells out its own status, so the reading never rests on the fill alone.
    expect(
      screen.getByLabelText('Seat A1 - Sold - 100,000 - buyer@example.com (order paid)'),
    ).toBeInTheDocument()
    expect(screen.getByLabelText('Seat A2 - In checkout - 100,000')).toBeInTheDocument()
    expect(
      screen.getByLabelText('Seat A3 - Available - 100,000 - wheelchair accessible'),
    ).toBeInTheDocument()
    expect(screen.getByLabelText('Seat A4 - Blocked - 100,000')).toBeInTheDocument()

    expect(screen.getByText('Sold 1')).toBeInTheDocument()
    expect(screen.getByText('In checkout 1')).toBeInTheDocument()
    expect(screen.getByText('Blocked 1')).toBeInTheDocument()
  }, 15000)

  it('reports a failure to load the seat map without breaking the rest of the report', async () => {
    window.history.pushState({}, '', ROUTES.adminEventDetail(event.id))
    installApiMock((config) => {
      if (endpoint(config) === 'POST /auth/refresh') {
        return response<LoginResponse>(config, 200, loginResponse)
      }

      if (endpoint(config) === 'GET /users/me') {
        return response<AuthUser>(config, 200, adminUser)
      }

      if (endpoint(config) === `GET /admin/events/${event.id}/sales`) {
        return response<EventSalesReport>(config, 200, eventSalesReport)
      }

      return rejectedResponse(config, 500, 'Seat map is unavailable')
    })

    render(<App />)

    expect(await screen.findByText('Seat map is unavailable')).toBeInTheDocument()
    // The surrounding report still renders.
    expect(screen.getByRole('heading', { name: event.name })).toBeInTheDocument()
  }, 15000)

  it('reports a failure to load the sales report', async () => {
    window.history.pushState({}, '', ROUTES.adminEventDetail(event.id))
    installApiMock((config) => {
      if (endpoint(config) === 'POST /auth/refresh') {
        return response<LoginResponse>(config, 200, loginResponse)
      }

      if (endpoint(config) === 'GET /users/me') {
        return response<AuthUser>(config, 200, adminUser)
      }

      return rejectedResponse(config, 500, 'Sales report is unavailable')
    })

    render(<App />)

    expect(await screen.findByText('Sales report is unavailable')).toBeInTheDocument()
  }, 10000)

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
        // Typed in the browser's time zone, so the UTC values depend on where the test runs.
        startTime: new Date(2026, 8, 1, 19, 0).toISOString(),
        salesStartTime: new Date(2026, 7, 1, 0, 0).toISOString(),
        salesEndTime: new Date(2026, 8, 1, 18, 0).toISOString(),
      }),
    )
    expect(await screen.findByRole('heading', { name: 'Edit event' })).toBeInTheDocument()
  }, 30000)

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
