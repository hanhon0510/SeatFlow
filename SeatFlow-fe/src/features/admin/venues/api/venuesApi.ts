import { apiClient } from '../../../../shared/api/httpClient'
import type {
  Seat,
  SeatCreateRequest,
  SeatLayout,
  SectionFormValues,
  Venue,
  VenueFormValues,
  VenuePage,
} from '../types'

export const venueQueryKeys = {
  all: ['admin', 'venues'] as const,
  list: (page: number, size: number) =>
    [...venueQueryKeys.all, 'list', { page, size }] as const,
  detail: (venueId: string) => [...venueQueryKeys.all, 'detail', venueId] as const,
  layout: (venueId: string) => [...venueQueryKeys.all, 'layout', venueId] as const,
}

export async function listVenues(page: number, size: number) {
  const response = await apiClient.get<VenuePage>('/admin/venues', {
    params: { page, size },
  })
  return response.data
}

export async function getVenue(venueId: string) {
  const response = await apiClient.get<Venue>(`/admin/venues/${venueId}`)
  return response.data
}

export async function createVenue(request: VenueFormValues) {
  const response = await apiClient.post<Venue>('/admin/venues', request)
  return response.data
}

export async function updateVenue(venueId: string, request: VenueFormValues) {
  const response = await apiClient.put<Venue>(`/admin/venues/${venueId}`, request)
  return response.data
}

export async function archiveVenue(venueId: string) {
  const response = await apiClient.post<Venue>(`/admin/venues/${venueId}/archive`)
  return response.data
}

export async function createSection(venueId: string, request: SectionFormValues) {
  const response = await apiClient.post(`/admin/venues/${venueId}/sections`, request)
  return response.data
}

export async function createSeatsBulk(sectionId: string, seats: SeatCreateRequest[]) {
  const response = await apiClient.post<Seat[]>(`/admin/sections/${sectionId}/seats/bulk`, {
    seats,
  })
  return response.data
}

export async function getSeatLayout(venueId: string) {
  const response = await apiClient.get<SeatLayout>(`/admin/venues/${venueId}/seat-layout`)
  return response.data
}
