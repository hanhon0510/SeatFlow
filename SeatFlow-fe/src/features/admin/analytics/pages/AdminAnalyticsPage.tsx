import { useMemo, useState } from 'react'
import { useMutation, useQuery } from '@tanstack/react-query'
import { Alert, Button, Card, DatePicker, Empty, Segmented, Select, Space, Spin, Statistic, Table, Typography } from 'antd'
import type { ColumnsType } from 'antd/es/table'
import dayjs from 'dayjs'
import type { Dayjs } from 'dayjs'
import { Link } from 'react-router-dom'

import { apiErrorMessage } from '../../../../shared/api/apiError'
import { ROUTES } from '../../../../shared/constants/routes'
import { eventQueryKeys, listEvents } from '../../events/api/eventsApi'
import { analyticsQueryKeys, downloadAnalyticsCsv, getAnalytics } from '../api/analyticsApi'
import type {
  AnalyticsCurrencyTotal,
  AnalyticsDailyPoint,
  AnalyticsEventTotal,
  AnalyticsQuery,
  AnalyticsReport,
} from '../types'

const DATE_FORMAT = 'YYYY-MM-DD'
const DEFAULT_DAYS = 30

const presets = [
  { label: 'Last 7 days', value: [dayjs().subtract(6, 'day'), dayjs()] as [Dayjs, Dayjs] },
  { label: 'Last 30 days', value: [dayjs().subtract(29, 'day'), dayjs()] as [Dayjs, Dayjs] },
  { label: 'Last 90 days', value: [dayjs().subtract(89, 'day'), dayjs()] as [Dayjs, Dayjs] },
]

export function AdminAnalyticsPage() {
  const [range, setRange] = useState<[Dayjs, Dayjs]>(() => [dayjs().subtract(DEFAULT_DAYS - 1, 'day'), dayjs()])
  const [eventId, setEventId] = useState<string | undefined>()
  const query: AnalyticsQuery = {
    from: range[0].format(DATE_FORMAT),
    to: range[1].format(DATE_FORMAT),
    eventId,
  }

  const reportQuery = useQuery({
    queryKey: analyticsQueryKeys.report(query),
    queryFn: () => getAnalytics(query),
    placeholderData: (previous) => previous,
  })
  const eventsQuery = useQuery({
    queryKey: eventQueryKeys.list(0, 100),
    queryFn: () => listEvents(0, 100),
  })
  const exportMutation = useMutation({ mutationFn: () => downloadAnalyticsCsv(query) })

  return (
    <section className="page-surface analytics-page">
      <div className="admin-section-header">
        <div>
          <Typography.Title level={1}>Sales analytics</Typography.Title>
          <Typography.Text type="secondary">
            Days are UTC days. Orders count on the day they were placed, so a refund never moves a sale to another
            day.
          </Typography.Text>
        </div>
        <Button loading={exportMutation.isPending} onClick={() => exportMutation.mutate()}>
          Export CSV
        </Button>
      </div>

      <Space className="analytics-filters" size="middle" wrap>
        <DatePicker.RangePicker
          allowClear={false}
          presets={presets}
          value={range}
          onChange={(value) => {
            if (value?.[0] && value[1]) {
              setRange([value[0], value[1]])
            }
          }}
        />
        <Select
          allowClear
          aria-label="Event"
          className="analytics-event-filter"
          loading={eventsQuery.isLoading}
          options={(eventsQuery.data?.items ?? []).map((event) => ({ value: event.id, label: event.name }))}
          placeholder="All events"
          showSearch={{ optionFilterProp: 'label' }}
          value={eventId}
          onChange={(value) => setEventId(value)}
        />
      </Space>

      {exportMutation.isError ? (
        <Alert
          showIcon
          className="admin-inline-alert"
          type="error"
          title={apiErrorMessage(exportMutation.error, 'Could not export the report')}
        />
      ) : null}

      {reportQuery.isLoading ? (
        <div className="catalog-loading">
          <Spin />
        </div>
      ) : reportQuery.isError || !reportQuery.data ? (
        <Alert showIcon type="error" title={apiErrorMessage(reportQuery.error, 'Could not load the analytics')} />
      ) : (
        <Report report={reportQuery.data} />
      )}
    </section>
  )
}

function Report({ report }: { report: AnalyticsReport }) {
  return (
    <Space orientation="vertical" size="large" style={{ width: '100%' }}>
      {report.totals.length === 0 ? (
        <Card title="Sales">
          <Empty description="No sales in this range" image={Empty.PRESENTED_IMAGE_SIMPLE} />
        </Card>
      ) : (
        <>
          {report.totals.map((total) => (
            <TotalsCard key={total.currency} total={total} />
          ))}
          <DailyChart report={report} />
        </>
      )}

      <div className="dashboard-tiles">
        <CheckoutCard report={report} />
        <WaitlistCard report={report} />
      </div>

      {report.topEvents.length > 0 ? (
        <Card title="Top events by net revenue">
          <Table<AnalyticsEventTotal>
            columns={eventColumns}
            dataSource={report.topEvents}
            pagination={false}
            rowKey={(event) => `${event.eventId}-${event.currency}`}
            size="middle"
          />
        </Card>
      ) : null}
    </Space>
  )
}

function TotalsCard({ total }: { total: AnalyticsCurrencyTotal }) {
  return (
    <Card title={`Sales in ${total.currency}`}>
      <div className="analytics-stats">
        <Statistic precision={2} title="Gross" value={total.gross} />
        <Statistic precision={2} title="Refunded" value={total.refunded} />
        <Statistic precision={2} title="Net" value={total.gross - total.refunded} />
        <Statistic title="Orders" value={total.orders} />
        <Statistic title="Seats sold" value={total.seats} />
        <Statistic title="Refund rate" value={percent(total.refundedOrders, total.orders)} />
      </div>
    </Card>
  )
}

function CheckoutCard({ report }: { report: AnalyticsReport }) {
  const { checkout } = report
  return (
    <Card title="Checkout">
      <Statistic title="Started to paid" value={percent(checkout.paid, checkout.started)} />
      <Typography.Text type="secondary">
        {checkout.started} started, {checkout.paid} paid, {checkout.expired} timed out, {checkout.failed} payment
        failed, {checkout.cancelled} cancelled
        {checkout.pending > 0 ? `, ${checkout.pending} still open` : ''}
      </Typography.Text>
    </Card>
  )
}

function WaitlistCard({ report }: { report: AnalyticsReport }) {
  const { waitlist } = report
  return (
    <Card title="Waitlist">
      <Statistic title="Offered to took seats" value={percent(waitlist.fulfilled, waitlist.offered)} />
      <Typography.Text type="secondary">
        {waitlist.joined} joined, {waitlist.offered} offered seats, {waitlist.fulfilled} took them,{' '}
        {waitlist.expired} let the offer lapse, {waitlist.cancelled} left
      </Typography.Text>
    </Card>
  )
}

/** Net sales per day as plain bars, so the page needs no chart library. */
function DailyChart({ report }: { report: AnalyticsReport }) {
  const currencies = report.totals.map((total) => total.currency)
  const [chosen, setChosen] = useState<string | null>(null)
  const currency = chosen && currencies.includes(chosen) ? chosen : currencies[0]
  const days = useMemo(() => netByDay(report, currency), [report, currency])
  const peak = Math.max(...days.map((day) => day.net), 0)

  return (
    <Card
      title="Net sales per day"
      extra={
        currencies.length > 1 ? (
          <Segmented options={currencies} value={currency} onChange={(value) => setChosen(value)} />
        ) : null
      }
    >
      <div aria-label={`Net sales per day in ${currency}`} className="analytics-bars" role="img">
        {days.map((day) => (
          <div
            key={day.date}
            className="analytics-bar"
            style={{ height: peak > 0 ? `${Math.max((day.net / peak) * 100, day.net > 0 ? 2 : 0)}%` : 0 }}
            title={`${day.date}: ${formatMoney(day.net, currency)}, ${day.orders} orders`}
          />
        ))}
      </div>
      <div className="analytics-axis">
        <Typography.Text type="secondary">{report.from}</Typography.Text>
        <Typography.Text type="secondary">Peak day {formatMoney(peak, currency)}</Typography.Text>
        <Typography.Text type="secondary">{report.to}</Typography.Text>
      </div>
    </Card>
  )
}

function netByDay(report: AnalyticsReport, currency: string) {
  const byDate = new Map<string, AnalyticsDailyPoint>()
  for (const point of report.daily) {
    if (point.currency === currency) {
      byDate.set(point.date, point)
    }
  }
  const days: { date: string; net: number; orders: number }[] = []
  const last = dayjs(report.to)
  for (let day = dayjs(report.from); !day.isAfter(last); day = day.add(1, 'day')) {
    const date = day.format(DATE_FORMAT)
    const point = byDate.get(date)
    days.push({ date, net: point ? point.gross - point.refunded : 0, orders: point?.orders ?? 0 })
  }
  return days
}

const eventColumns: ColumnsType<AnalyticsEventTotal> = [
  {
    title: 'Event',
    key: 'name',
    render: (_, event) => <Link to={ROUTES.adminEventDetail(event.eventId)}>{event.eventName}</Link>,
  },
  { title: 'Orders', dataIndex: 'orders', key: 'orders', align: 'right' },
  { title: 'Seats', dataIndex: 'seats', key: 'seats', align: 'right', responsive: ['md'] },
  {
    title: 'Gross',
    key: 'gross',
    align: 'right',
    responsive: ['md'],
    render: (_, event) => formatMoney(event.gross, event.currency),
  },
  {
    title: 'Refunded',
    key: 'refunded',
    align: 'right',
    responsive: ['md'],
    render: (_, event) => formatMoney(event.refunded, event.currency),
  },
  {
    title: 'Net',
    key: 'net',
    align: 'right',
    render: (_, event) => formatMoney(event.gross - event.refunded, event.currency),
  },
]

function percent(part: number, whole: number) {
  return whole === 0 ? '—' : `${((part / whole) * 100).toFixed(1)}%`
}

function formatMoney(value: number, currency: string) {
  return new Intl.NumberFormat(undefined, { maximumFractionDigits: 2 }).format(value) + ` ${currency}`
}
