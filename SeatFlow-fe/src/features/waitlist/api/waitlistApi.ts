import { apiClient } from '../../../shared/api/httpClient'
import type { WaitlistStatus } from '../types'

export const waitlistQueryKeys = {
  all: ['waitlist'] as const,
  status: (eventId: string) => [...waitlistQueryKeys.all, 'status', eventId] as const,
}

export async function getWaitlistStatus(eventId: string) {
  const response = await apiClient.get<WaitlistStatus>(`/events/${eventId}/waitlist`)
  return response.data
}

export async function joinWaitlist(eventId: string, seatCount: number) {
  const response = await apiClient.post<WaitlistStatus>(`/events/${eventId}/waitlist`, { seatCount })
  return response.data
}

export async function leaveWaitlist(eventId: string) {
  await apiClient.delete(`/events/${eventId}/waitlist`)
}
