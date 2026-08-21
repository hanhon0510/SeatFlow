import { describe, expect, it } from 'vitest'
import type { AxiosError } from 'axios'

import { apiErrorCode, apiErrorCorrelationId, apiErrorMessage } from './apiError'

describe('apiError helpers', () => {
  it('maps backend error codes to user-friendly messages', () => {
    const error = axiosError({
      code: 'SEAT_ALREADY_HELD',
      title: 'Seat hold conflict',
      detail: 'One or more seats are unavailable.',
      correlationId: '6d19c287-bf22-4fb0-bf5b-f2bdd0f11292',
    })

    expect(apiErrorMessage(error, 'Fallback')).toBe(
      'One or more selected seats are no longer available.',
    )
    expect(apiErrorCode(error)).toBe('SEAT_ALREADY_HELD')
    expect(apiErrorCorrelationId(error)).toBe('6d19c287-bf22-4fb0-bf5b-f2bdd0f11292')
  })

  it('includes field validation details', () => {
    const error = axiosError({
      code: 'VALIDATION_FAILED',
      title: 'Invalid request',
      errors: [
        {
          field: 'email',
          message: 'must be a well-formed email address',
          code: 'Email',
        },
      ],
    })

    expect(apiErrorMessage(error, 'Fallback')).toBe(
      'Please check the highlighted fields.: email: must be a well-formed email address',
    )
  })

  it('keeps compatibility with legacy message payloads', () => {
    expect(apiErrorMessage(axiosError({ message: 'Legacy failure' }), 'Fallback')).toBe(
      'Legacy failure',
    )
  })
})

function axiosError(data: unknown) {
  return {
    isAxiosError: true,
    response: { data },
  } as AxiosError
}
