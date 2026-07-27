import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { AxiosError } from 'axios'
import type { AxiosResponse, InternalAxiosRequestConfig } from 'axios'
import { describe, expect, it, vi } from 'vitest'

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

const loginResponse: LoginResponse = {
  accessToken: 'access-token',
  tokenType: 'Bearer',
  expiresAt: '2026-07-27T01:00:00Z',
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
    window.history.pushState({}, '', ROUTES.events)
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
