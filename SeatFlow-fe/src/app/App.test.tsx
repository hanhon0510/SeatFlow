import { fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { AxiosError } from 'axios'
import type { AxiosResponse, InternalAxiosRequestConfig } from 'axios'
import { describe, expect, it, vi } from 'vitest'

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
import type { PublicEvent, PublicEventPage } from '../features/events/types'
import type { AuthUser, LoginResponse, RegisterResponse } from '../features/auth/types'
import { apiClient } from '../shared/api/httpClient'
import { ROUTES } from '../shared/constants/routes'
import App from './App'

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

async function fillDate(user: ReturnType<typeof userEvent.setup>, label: string, value: string) {
  const input = screen.getByLabelText(label)
  await user.click(input)
  await user.clear(input)
  await user.type(input, value)
  fireEvent.keyDown(input, { key: 'Enter', code: 'Enter' })
  fireEvent.blur(input)
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

      return rejectedResponse(config, 500, 'Unexpected request')
    })

    render(<App />)

    expect(await screen.findByRole('heading', { name: publishedEvent.name })).toBeInTheDocument()
    expect(screen.getByText(venue.name)).toBeInTheDocument()
    expect(screen.getByText(venue.timezone)).toBeInTheDocument()
    expect(screen.getByText(/From 50,000/)).toBeInTheDocument()
  })

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
    expect(screen.getByText('Timezone is required')).toBeInTheDocument()
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
    await user.type(screen.getByLabelText('Timezone'), venue.timezone)
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
