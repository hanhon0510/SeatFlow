import { isAxiosError } from 'axios'

type BackendErrorPayload = {
  title?: string
  code?: string
  detail?: string
  correlationId?: string
  message?: string
  errors?: unknown
}

const CODE_MESSAGES: Record<string, string> = {
  VALIDATION_FAILED: 'Please check the highlighted fields.',
  UNAUTHORIZED: 'Please sign in to continue.',
  AUTHENTICATION_FAILED: 'Invalid email or password.',
  INVALID_REFRESH_TOKEN: 'Your session expired. Please sign in again.',
  FORBIDDEN: 'You do not have permission to perform this action.',
  USER_ALREADY_EXISTS: 'An account with that email already exists.',
  SEAT_ALREADY_HELD: 'One or more selected seats are no longer available.',
  SEAT_HOLD_NOT_FOUND: 'This seat hold is no longer available.',
  RESERVATION_CONFLICT: 'The reservation cannot be completed.',
  ORDER_CONFLICT: 'The order cannot be completed.',
  PAYMENT_CONFLICT: 'The payment cannot be completed.',
  INVALID_PAYMENT_TOKEN: 'Choose a valid payment option.',
  RATE_LIMIT_EXCEEDED: 'Too many attempts. Please wait and try again.',
  TICKET_NOT_FOUND: 'Ticket not found.',
}

export function apiErrorMessage(error: unknown, fallback: string) {
  if (!isAxiosError<BackendErrorPayload>(error)) {
    return fallback
  }

  const payload = error.response?.data
  const details = validationDetails(payload?.errors)
  const codedMessage = payload?.code ? CODE_MESSAGES[payload.code] : undefined
  const baseMessage = codedMessage ?? payload?.detail ?? payload?.title ?? payload?.message

  if (baseMessage && details.length > 0) {
    return `${baseMessage}: ${details.join(', ')}`
  }

  return baseMessage ?? details[0] ?? fallback
}

export function apiErrorCode(error: unknown) {
  if (!isAxiosError<BackendErrorPayload>(error)) {
    return null
  }

  return error.response?.data?.code ?? null
}

export function apiErrorCorrelationId(error: unknown) {
  if (!isAxiosError<BackendErrorPayload>(error)) {
    return null
  }

  return error.response?.data?.correlationId ?? null
}

function validationDetails(errors: unknown) {
  if (!errors) {
    return []
  }

  if (Array.isArray(errors)) {
    return errors
      .map((error) => {
        if (typeof error === 'string') {
          return error
        }

        if (isMessageShape(error)) {
          return fieldMessage(error)
        }

        return null
      })
      .filter((error): error is string => Boolean(error))
  }

  if (typeof errors === 'object') {
    return Object.entries(errors).flatMap(([field, messages]) => {
      if (typeof messages === 'string') {
        return `${field}: ${messages}`
      }

      if (Array.isArray(messages)) {
        return messages
          .filter((message): message is string => typeof message === 'string')
          .map((message) => `${field}: ${message}`)
      }

      return []
    })
  }

  return []
}

function isMessageShape(value: unknown): value is { message: string } {
  return (
    typeof value === 'object' &&
    value !== null &&
    'message' in value &&
    typeof value.message === 'string'
  )
}

function fieldMessage(error: { message: string; field?: unknown }) {
  if (typeof error.field === 'string' && error.field.length > 0) {
    return `${error.field}: ${error.message}`
  }

  return error.message
}
