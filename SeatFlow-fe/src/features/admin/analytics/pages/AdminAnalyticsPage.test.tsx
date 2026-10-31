import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import dayjs from 'dayjs'
import { MemoryRouter } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import type { AnalyticsReport } from '../types'
import { AdminAnalyticsPage } from './AdminAnalyticsPage'

const api = vi.hoisted(() => ({
  getAnalytics: vi.fn(),
  downloadAnalyticsCsv: vi.fn(),
  listEvents: vi.fn(),
}))

vi.mock('../api/analyticsApi', async (importOriginal) => ({
  ...(await importOriginal<typeof import('../api/analyticsApi')>()),
  getAnalytics: api.getAnalytics,
  downloadAnalyticsCsv: api.downloadAnalyticsCsv,
}))
vi.mock('../../events/api/eventsApi', async (importOriginal) => ({
  ...(await importOriginal<typeof import('../../events/api/eventsApi')>()),
  listEvents: api.listEvents,
}))

const report: AnalyticsReport = {
  from: dayjs().subtract(29, 'day').format('YYYY-MM-DD'),
  to: dayjs().format('YYYY-MM-DD'),
  eventId: null,
  totals: [{ currency: 'USD', orders: 4, seats: 9, gross: 400, refunded: 100, refundedOrders: 1 }],
  daily: [{ date: dayjs().format('YYYY-MM-DD'), currency: 'USD', orders: 4, seats: 9, gross: 400, refunded: 100 }],
  topEvents: [
    { eventId: 'event-1', eventName: 'Opening Night', currency: 'USD', orders: 4, seats: 9, gross: 400, refunded: 100 },
  ],
  checkout: { started: 20, paid: 5, pending: 2, expired: 8, failed: 3, cancelled: 2 },
  waitlist: { joined: 10, offered: 4, fulfilled: 3, expired: 1, cancelled: 2 },
  generatedAt: new Date().toISOString(),
}

function renderPage() {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter>
        <AdminAnalyticsPage />
      </MemoryRouter>
    </QueryClientProvider>,
  )
}

describe('AdminAnalyticsPage', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    api.listEvents.mockResolvedValue({ items: [], page: 0, size: 100, totalItems: 0, totalPages: 0 })
  })

  it('asks for the last 30 days and shows sales, conversion and the top events', async () => {
    api.getAnalytics.mockResolvedValue(report)

    renderPage()

    const salesCard = (await screen.findByText('Sales in USD')).closest('.ant-card') as HTMLElement
    expect(within(salesCard).getByText('Net').closest('.ant-statistic')).toHaveTextContent('300.00')
    expect(within(salesCard).getByText('Refund rate').closest('.ant-statistic')).toHaveTextContent('25.0%')
    expect(screen.getByText('Started to paid').closest('.ant-statistic')).toHaveTextContent('25.0%')
    expect(screen.getByText('Offered to took seats').closest('.ant-statistic')).toHaveTextContent('75.0%')
    expect(screen.getByRole('link', { name: 'Opening Night' })).toHaveAttribute('href', '/admin/events/event-1')
    expect(screen.getByRole('img', { name: 'Net sales per day in USD' })).toBeInTheDocument()
    expect(api.getAnalytics).toHaveBeenCalledWith({
      from: report.from,
      to: report.to,
      eventId: undefined,
    })
  })

  it('says so when nothing sold in the range', async () => {
    api.getAnalytics.mockResolvedValue({
      ...report,
      totals: [],
      daily: [],
      topEvents: [],
      checkout: { started: 0, paid: 0, pending: 0, expired: 0, failed: 0, cancelled: 0 },
      waitlist: { joined: 0, offered: 0, fulfilled: 0, expired: 0, cancelled: 0 },
    })

    renderPage()

    expect(await screen.findByText('No sales in this range')).toBeInTheDocument()
    expect(screen.getByText('Started to paid').closest('.ant-statistic')).toHaveTextContent('—')
  })

  it('exports the range that is on screen', async () => {
    api.getAnalytics.mockResolvedValue(report)
    api.downloadAnalyticsCsv.mockResolvedValue(undefined)
    const user = userEvent.setup()
    renderPage()
    await screen.findByText('Sales in USD')

    await user.click(screen.getByRole('button', { name: /export csv/i }))

    await waitFor(() =>
      expect(api.downloadAnalyticsCsv).toHaveBeenCalledWith({
        from: report.from,
        to: report.to,
        eventId: undefined,
      }),
    )
  })

  it('shows why the report could not be loaded', async () => {
    api.getAnalytics.mockRejectedValue(new Error('boom'))

    renderPage()

    expect(await screen.findByText(/could not load the analytics/i)).toBeInTheDocument()
  })
})
