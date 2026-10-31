import { apiClient } from '../../../../shared/api/httpClient'
import type { AnalyticsQuery, AnalyticsReport } from '../types'

export const analyticsQueryKeys = {
  all: ['admin', 'analytics'] as const,
  report: (query: AnalyticsQuery) =>
    [...analyticsQueryKeys.all, query.from, query.to, query.eventId ?? 'all'] as const,
}

function params(query: AnalyticsQuery) {
  return { from: query.from, to: query.to, eventId: query.eventId || undefined }
}

export async function getAnalytics(query: AnalyticsQuery) {
  const response = await apiClient.get<AnalyticsReport>('/admin/analytics', { params: params(query) })
  return response.data
}

/** The export needs the bearer token, so it is fetched and saved rather than opened as a link. */
export async function downloadAnalyticsCsv(query: AnalyticsQuery) {
  const response = await apiClient.get<Blob>('/admin/analytics/export', {
    params: params(query),
    responseType: 'blob',
  })
  const url = URL.createObjectURL(response.data)
  const link = document.createElement('a')
  link.href = url
  link.download = `seatflow-sales-${query.from}-to-${query.to}.csv`
  document.body.appendChild(link)
  link.click()
  link.remove()
  URL.revokeObjectURL(url)
}
