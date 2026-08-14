import '@testing-library/jest-dom/vitest'
import { cleanup } from '@testing-library/react'
import { AxiosError } from 'axios'
import type { InternalAxiosRequestConfig } from 'axios'
import { afterEach, beforeEach, vi } from 'vitest'

import { apiClient, clearAccessToken } from '../shared/api/httpClient'

beforeEach(() => {
  clearAccessToken()
  window.sessionStorage.clear()
  apiClient.defaults.adapter = async (config) =>
    Promise.reject(
      new AxiosError('Invalid refresh token', undefined, config, {}, {
        data: {
          success: false,
          message: 'Invalid refresh token',
          data: null,
          timestamp: new Date().toISOString(),
        },
        status: 401,
        statusText: 'Unauthorized',
        headers: {},
        config: config as InternalAxiosRequestConfig,
        request: {},
      }),
    )
})

afterEach(() => {
  cleanup()
  window.sessionStorage.clear()
  window.history.pushState({}, '', '/')
})

Object.defineProperty(window, 'matchMedia', {
  writable: true,
  value: vi.fn().mockImplementation((query: string) => ({
    matches: false,
    media: query,
    onchange: null,
    addListener: vi.fn(),
    removeListener: vi.fn(),
    addEventListener: vi.fn(),
    removeEventListener: vi.fn(),
    dispatchEvent: vi.fn(),
  })),
})

class ResizeObserverMock {
  observe() {}
  unobserve() {}
  disconnect() {}
}

window.ResizeObserver = ResizeObserverMock
