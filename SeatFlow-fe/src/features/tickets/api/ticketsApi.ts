import { apiClient } from '../../../shared/api/httpClient'
import type { Ticket } from '../types'

export const ticketQueryKeys = {
  all: ['tickets'] as const,
  list: () => [...ticketQueryKeys.all, 'list'] as const,
  detail: (ticketId: string) => [...ticketQueryKeys.all, 'detail', ticketId] as const,
}

export async function listTickets() {
  const response = await apiClient.get<Ticket[]>('/users/me/tickets')
  return response.data
}

export async function getTicket(ticketId: string) {
  const response = await apiClient.get<Ticket>(`/tickets/${ticketId}`)
  return response.data
}
