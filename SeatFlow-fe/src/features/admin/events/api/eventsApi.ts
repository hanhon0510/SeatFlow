import { apiClient } from '../../../../shared/api/httpClient'
import type {
  Event,
  EventFormValues,
  EventPage,
  EventPublishResponse,
  EventRequest,
  EventSectionConfiguration,
  EventSectionPriceRequest,
  EventSectionReplaceRequest,
} from '../types'

export const eventQueryKeys = {
  all: ['admin', 'events'] as const,
  list: (page: number, size: number) =>
    [...eventQueryKeys.all, 'list', { page, size }] as const,
  detail: (eventId: string) => [...eventQueryKeys.all, 'detail', eventId] as const,
  sections: (eventId: string) => [...eventQueryKeys.all, 'sections', eventId] as const,
}

export async function listEvents(page: number, size: number) {
  const response = await apiClient.get<EventPage>('/admin/events', {
    params: { page, size },
  })
  return response.data
}

export async function getEvent(eventId: string) {
  const response = await apiClient.get<Event>(`/admin/events/${eventId}`)
  return response.data
}

export async function createEvent(values: EventFormValues) {
  const response = await apiClient.post<Event>('/admin/events', eventRequest(values))
  return response.data
}

export async function updateEvent(eventId: string, values: EventFormValues) {
  const response = await apiClient.put<Event>(`/admin/events/${eventId}`, eventRequest(values))
  return response.data
}

export async function getEventSections(eventId: string) {
  const response = await apiClient.get<EventSectionConfiguration>(`/admin/events/${eventId}/sections`)
  return response.data
}

export async function replaceEventSections(eventId: string, sections: EventSectionPriceRequest[]) {
  const request: EventSectionReplaceRequest = { sections }
  const response = await apiClient.put<EventSectionConfiguration>(
    `/admin/events/${eventId}/sections`,
    request,
  )
  return response.data
}

export async function publishEvent(eventId: string) {
  const response = await apiClient.post<EventPublishResponse>(`/admin/events/${eventId}/publish`)
  return response.data
}

function eventRequest(values: EventFormValues): EventRequest {
  const description = values.description?.trim()

  return {
    venueId: values.venueId,
    name: values.name.trim(),
    description: description ? description : null,
    startTime: values.startTime.toDate().toISOString(),
    salesStartTime: values.salesStartTime.toDate().toISOString(),
    salesEndTime: values.salesEndTime.toDate().toISOString(),
  }
}
