import { isAxiosError } from 'axios'

type BackendErrorPayload = {
  message?: string
  errors?: unknown
}

export function apiErrorMessage(error: unknown, fallback: string) {
  if (!isAxiosError<BackendErrorPayload>(error)) {
    return fallback
  }

  const payload = error.response?.data
  const details = validationDetails(payload?.errors)

  if (payload?.message && details.length > 0) {
    return `${payload.message}: ${details.join(', ')}`
  }

  return payload?.message ?? details[0] ?? fallback
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
          return error.message
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
