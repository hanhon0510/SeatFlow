import { apiClient } from '../../../shared/api/httpClient'
import type { SeatHold } from '../types'

export const seatHoldQueryKeys = {
  all: ['seat-holds'] as const,
  detail: (holdId: string) => [...seatHoldQueryKeys.all, 'detail', holdId] as const,
}

export async function createSeatHold(eventId: string, eventSeatIds: string[]) {
  const response = await apiClient.post<SeatHold>(`/events/${eventId}/holds`, {
    eventSeatIds,
  })
  return response.data
}

export async function getSeatHold(holdId: string) {
  const response = await apiClient.get<SeatHold>(`/holds/${holdId}`)
  return response.data
}

export async function releaseSeatHold(holdId: string) {
  await apiClient.delete(`/holds/${holdId}`)
}

