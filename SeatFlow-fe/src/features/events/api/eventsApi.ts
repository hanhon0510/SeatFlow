import { apiClient } from '../../../shared/api/httpClient'
import type { EventCatalogFilters, EventSeatLayout, PublicEvent, PublicEventPage } from '../types'

export const publicEventQueryKeys = {
  all: ['events'] as const,
  list: (filters: EventCatalogFilters) => [...publicEventQueryKeys.all, 'list', filters] as const,
  venueOptions: () => [...publicEventQueryKeys.all, 'venue-options'] as const,
  detail: (eventId: string) => [...publicEventQueryKeys.all, 'detail', eventId] as const,
  seatLayout: (eventId: string) => [...publicEventQueryKeys.all, 'seat-layout', eventId] as const,
}

export async function listPublicEvents(filters: EventCatalogFilters) {
  const { statuses, ...rest } = filters
  const response = await apiClient.get<PublicEventPage>('/events', {
    // Comma separated rather than repeated: Spring binds either, and axios would otherwise
    // serialise the array as status[]=..., which the controller does not read.
    params: cleanParams({ ...rest, status: statuses.join(',') }),
  })
  return response.data
}

export async function getPublicEvent(eventId: string) {
  const response = await apiClient.get<PublicEvent>(`/events/${eventId}`)
  return response.data
}

export async function getEventSeatLayout(eventId: string) {
  const response = await apiClient.get<EventSeatLayout>(`/events/${eventId}/seats`)
  return response.data
}

function cleanParams(filters: Record<string, unknown>) {
  return Object.fromEntries(
    Object.entries(filters).filter(([, value]) => value !== undefined && value !== ''),
  )
}
