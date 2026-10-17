import { apiClient } from '../../../../shared/api/httpClient'
import type { CheckInResult, CheckInSummary } from '../types'

export const checkInQueryKeys = {
  all: ['admin', 'check-in'] as const,
  summary: (eventId: string) => [...checkInQueryKeys.all, 'summary', eventId] as const,
}

export async function getCheckInSummary(eventId: string) {
  const response = await apiClient.get<CheckInSummary>(`/admin/events/${eventId}/check-in`)
  return response.data
}

export async function checkInTicket(eventId: string, code: string) {
  const response = await apiClient.post<CheckInResult>(`/admin/events/${eventId}/check-in`, { code })
  return response.data
}
