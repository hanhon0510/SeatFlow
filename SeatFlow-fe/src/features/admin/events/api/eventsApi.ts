import { apiClient } from '../../../../shared/api/httpClient'
import type {
  CancelEventRequest,
  Event,
  EventCancellation,
  EventReminder,
  EventReminderRequest,
  EventWaitlist,
  EventFormValues,
  EventPage,
  EventPublishResponse,
  EventRequest,
  EventSectionConfiguration,
  EventSectionPriceRequest,
  AdminSeatMap,
  EventSalesReport,
  EventSectionReplaceRequest,
} from '../types'

export const eventQueryKeys = {
  all: ['admin', 'events'] as const,
  list: (page: number, size: number) =>
    [...eventQueryKeys.all, 'list', { page, size }] as const,
  detail: (eventId: string) => [...eventQueryKeys.all, 'detail', eventId] as const,
  sections: (eventId: string) => [...eventQueryKeys.all, 'sections', eventId] as const,
  sales: (eventId: string) => [...eventQueryKeys.all, 'sales', eventId] as const,
  seatMap: (eventId: string) => [...eventQueryKeys.all, 'seat-map', eventId] as const,
  reminder: (eventId: string) => [...eventQueryKeys.all, 'reminder', eventId] as const,
  waitlist: (eventId: string) => [...eventQueryKeys.all, 'waitlist', eventId] as const,
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

export async function getEventSales(eventId: string) {
  const response = await apiClient.get<EventSalesReport>(`/admin/events/${eventId}/sales`)
  return response.data
}

export async function getEventSeatMap(eventId: string) {
  const response = await apiClient.get<AdminSeatMap>(`/admin/events/${eventId}/seat-map`)
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

export async function cancelEvent(eventId: string, request: CancelEventRequest) {
  const response = await apiClient.post<EventCancellation>(`/admin/events/${eventId}/cancel`, request)
  return response.data
}

/**
 * Cancels the event and keeps asking for the next batch of refunds until none are left. Repeating
 * the request is safe, so this is also how an interrupted cancellation is finished. It gives up
 * once a batch refunds nothing, so an order that cannot be refunded does not spin forever.
 */
export async function cancelEventAndRefund(
  eventId: string,
  request: CancelEventRequest,
  onProgress: (progress: EventCancellation) => void,
) {
  let latest = await cancelEvent(eventId, request)
  onProgress(latest)

  while (latest.remainingRefunds > 0) {
    const refundedBefore = latest.refundedOrders
    latest = await cancelEvent(eventId, request)
    onProgress(latest)
    if (latest.refundedOrders <= refundedBefore) {
      break
    }
  }

  return latest
}

export async function getEventReminder(eventId: string) {
  const response = await apiClient.get<EventReminder>(`/admin/events/${eventId}/reminder`)
  return response.data
}

export async function updateEventReminder(eventId: string, request: EventReminderRequest) {
  const response = await apiClient.put<EventReminder>(`/admin/events/${eventId}/reminder`, request)
  return response.data
}

export async function getEventWaitlist(eventId: string) {
  const response = await apiClient.get<EventWaitlist>(`/admin/events/${eventId}/waitlist`)
  return response.data
}
