import { useQuery } from '@tanstack/react-query'
import { Alert, Button, Card, Empty, Progress, Space, Spin, Statistic, Table, Tag, Typography } from 'antd'
import type { ColumnsType } from 'antd/es/table'
import { Link } from 'react-router-dom'

import { apiErrorMessage } from '../../../../shared/api/apiError'
import { ROUTES } from '../../../../shared/constants/routes'
import { EventStatusTag } from '../../events/components/EventStatusTag'
import { dashboardQueryKeys, getAdminDashboard } from '../api/dashboardApi'
import type { AdminDashboard, DashboardRevenue, DashboardUpcomingEvent } from '../types'

export function AdminDashboardPage() {
  const dashboardQuery = useQuery({
    queryKey: dashboardQueryKeys.all,
    queryFn: getAdminDashboard,
  })

  if (dashboardQuery.isLoading) {
    return (
      <section className="page-surface">
        <div className="catalog-loading">
          <Spin />
        </div>
      </section>
    )
  }

  if (dashboardQuery.isError || !dashboardQuery.data) {
    return (
      <section className="page-surface">
        <Alert
          showIcon
          type="error"
          message={apiErrorMessage(dashboardQuery.error, 'Could not load the dashboard')}
        />
      </section>
    )
  }

  const { venues, events, sales, upcomingEvents, generatedAt } = dashboardQuery.data

  return (
    <section className="page-surface admin-dashboard">
      <div className="admin-section-header">
        <div>
          <Typography.Title level={1}>Dashboard</Typography.Title>
          <Typography.Text type="secondary">As of {formatDateTime(generatedAt)}</Typography.Text>
        </div>
        <Space wrap>
          <Link to={ROUTES.adminEventNew}>
            <Button type="primary">New event</Button>
          </Link>
          <Link to={ROUTES.adminVenueNew}>
            <Button>New venue</Button>
          </Link>
        </Space>
      </div>

      {needsAttention(dashboardQuery.data) ? (
        <Alert
          showIcon
          className="admin-inline-alert"
          type="info"
          message={attentionMessage(dashboardQuery.data)}
        />
      ) : null}

      <div className="dashboard-tiles">
        <Card title="On sale now">
          <Statistic value={events.onSaleNow} suffix={`of ${events.published} published`} />
          <Typography.Text type="secondary">
            {events.startingSoon} starting in the next 7 days
          </Typography.Text>
        </Card>

        <Card title="Tickets issued">
          <Statistic value={sales.ticketsIssued} />
          <Typography.Text type="secondary">{sales.ticketsUsed} checked in</Typography.Text>
        </Card>

        <Card title="Paid orders">
          <Statistic value={sales.paidOrders} />
          <Typography.Text type="secondary">{sales.pendingOrders} awaiting payment</Typography.Text>
        </Card>

        <Card title="Revenue">
          {sales.revenue.length === 0 ? (
            <>
              <Statistic value={0} />
              <Typography.Text type="secondary">No paid orders yet</Typography.Text>
            </>
          ) : (
            <Space orientation="vertical" size={4}>
              {sales.revenue.map((entry) => (
                <RevenueLine key={entry.currency} revenue={entry} />
              ))}
            </Space>
          )}
        </Card>
      </div>

      <div className="dashboard-tiles">
        <Card title="Venues">
          <Statistic value={venues.active} suffix={`of ${venues.total} active`} />
          <Typography.Text type="secondary">
            {venues.sections} sections, {venues.seats} seats
            {venues.archived > 0 ? `, ${venues.archived} archived` : ''}
          </Typography.Text>
        </Card>

        <Card title="Events">
          <Statistic value={events.total} />
          <Space size={4} wrap>
            <Tag color="blue">{events.draft} draft</Tag>
            <Tag color="green">{events.published} published</Tag>
            {events.cancelled > 0 ? <Tag color="red">{events.cancelled} cancelled</Tag> : null}
            {events.completed > 0 ? <Tag>{events.completed} completed</Tag> : null}
          </Space>
        </Card>
      </div>

      <Card
        className="dashboard-upcoming"
        title="Next events"
        extra={<Link to={ROUTES.adminEvents}>All events</Link>}
      >
        {upcomingEvents.length === 0 ? (
          <Empty description="No upcoming events" image={Empty.PRESENTED_IMAGE_SIMPLE} />
        ) : (
          <Table<DashboardUpcomingEvent>
            columns={upcomingColumns}
            dataSource={upcomingEvents}
            pagination={false}
            rowKey="eventId"
            size="middle"
          />
        )}
      </Card>
    </section>
  )
}

const upcomingColumns: ColumnsType<DashboardUpcomingEvent> = [
  {
    title: 'Event',
    key: 'name',
    render: (_, event) => (
      <Space orientation="vertical" size={0}>
        <Link to={ROUTES.adminEventEdit(event.eventId)}>{event.name}</Link>
        <Typography.Text type="secondary">{event.venueName}</Typography.Text>
      </Space>
    ),
  },
  {
    title: 'Starts',
    dataIndex: 'startTime',
    key: 'startTime',
    responsive: ['md'],
    render: (value: string) => formatDateTime(value),
  },
  {
    title: 'Sales close',
    dataIndex: 'salesEndTime',
    key: 'salesEndTime',
    responsive: ['lg'],
    render: (value: string) => formatDateTime(value),
  },
  {
    title: 'Status',
    dataIndex: 'status',
    key: 'status',
    render: (status: DashboardUpcomingEvent['status']) => <EventStatusTag status={status} />,
  },
  {
    title: 'Sold',
    key: 'sold',
    render: (_, event) => <SeatsSold event={event} />,
  },
]

function SeatsSold({ event }: { event: DashboardUpcomingEvent }) {
  if (event.seatsTotal === 0) {
    return <Typography.Text type="secondary">No seats yet</Typography.Text>
  }

  return (
    <Space orientation="vertical" size={0}>
      <Typography.Text>
        {event.seatsSold} / {event.seatsTotal}
      </Typography.Text>
      <Progress
        aria-label={`${event.name} seats sold`}
        percent={Math.round((event.seatsSold / event.seatsTotal) * 100)}
        size="small"
      />
    </Space>
  )
}

function RevenueLine({ revenue }: { revenue: DashboardRevenue }) {
  return (
    <Typography.Text strong>
      {formatMoney(revenue.amount, revenue.currency)}
      <Typography.Text type="secondary"> · {revenue.orderCount} orders</Typography.Text>
    </Typography.Text>
  )
}

/** A draft event or an unpriced venue is work the admin still has to finish. */
function needsAttention(dashboard: AdminDashboard) {
  return dashboard.events.draft > 0 || dashboard.venues.seats === 0
}

function attentionMessage(dashboard: AdminDashboard) {
  if (dashboard.venues.seats === 0) {
    return 'No seats exist yet. Add sections and seats to a venue before publishing an event.'
  }
  return `${dashboard.events.draft} event${dashboard.events.draft === 1 ? '' : 's'} still in draft and not on sale.`
}

function formatDateTime(value: string) {
  return new Intl.DateTimeFormat(undefined, {
    dateStyle: 'medium',
    timeStyle: 'short',
  }).format(new Date(value))
}

function formatMoney(value: number, currency: string) {
  return new Intl.NumberFormat(undefined, { maximumFractionDigits: 2 }).format(value) + ` ${currency}`
}
