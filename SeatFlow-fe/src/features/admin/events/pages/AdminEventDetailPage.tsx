import {
  ArrowLeftOutlined,
  CloseCircleOutlined,
  EditOutlined,
  ReloadOutlined,
  RollbackOutlined,
  ScanOutlined,
} from '@ant-design/icons'
import { useQuery } from '@tanstack/react-query'
import {
  Alert,
  Button,
  Card,
  Descriptions,
  Empty,
  Progress,
  Space,
  Spin,
  Statistic,
  Table,
  Tag,
  Typography,
} from 'antd'
import type { ColumnsType } from 'antd/es/table'
import { useState } from 'react'
import { Link, Navigate, useParams } from 'react-router-dom'

import { apiErrorMessage } from '../../../../shared/api/apiError'
import { ROUTES } from '../../../../shared/constants/routes'
import { RefundOrderModal } from '../../orders/components/RefundOrderModal'
import type { RefundableOrder } from '../../orders/types'
import { eventQueryKeys, getEventSales } from '../api/eventsApi'
import { EventSalesStatusTag } from '../components/EventSalesStatusTag'
import { CancelEventModal } from '../components/CancelEventModal'
import { EventReminderCard } from '../components/EventReminderCard'
import { EventStatusTag } from '../components/EventStatusTag'
import { AdminSeatMap } from '../components/AdminSeatMap'
import { SalesTrendChart } from '../components/SalesTrendChart'
import type { EventSalesRecentOrder, EventSalesSection } from '../types'

const orderStatusColor: Record<EventSalesRecentOrder['status'], string> = {
  PAID: 'green',
  PENDING: 'gold',
  FAILED: 'red',
  CANCELLED: 'default',
  REFUNDED: 'purple',
}

export function AdminEventDetailPage() {
  const { eventId } = useParams()
  const [orderToRefund, setOrderToRefund] = useState<RefundableOrder | null>(null)
  const [cancelMode, setCancelMode] = useState<'cancel' | 'resume' | null>(null)

  const salesQuery = useQuery({
    queryKey: eventQueryKeys.sales(eventId ?? 'missing'),
    queryFn: () => getEventSales(eventId ?? ''),
    enabled: Boolean(eventId),
  })

  if (!eventId) {
    return <Navigate to={ROUTES.adminEvents} replace />
  }

  if (salesQuery.isLoading) {
    return (
      <section className="page-surface admin-page">
        <div className="catalog-loading">
          <Spin />
        </div>
      </section>
    )
  }

  if (salesQuery.isError || !salesQuery.data) {
    return (
      <section className="page-surface admin-page">
        <Alert
          showIcon
          title={apiErrorMessage(salesQuery.error, 'Could not load the sales report')}
          type="error"
        />
        <Space>
          <Button icon={<ArrowLeftOutlined />}>
            <Link to={ROUTES.adminEvents}>Back to events</Link>
          </Button>
        </Space>
      </section>
    )
  }

  const { event, inventory, revenue, orders, tickets, sections, dailySales, generatedAt } =
    salesQuery.data
  const sellThrough = inventory.seatsTotal === 0 ? 0 : (inventory.seatsSold / inventory.seatsTotal) * 100
  // Only events that have not started can be cancelled (judged at report time, which keeps render pure);
  // a cancelled event may still owe refunds.
  const canCancel =
    (event.status === 'DRAFT' || event.status === 'PUBLISHED') && new Date(event.startTime).getTime() > new Date(generatedAt).getTime()
  const awaitingRefunds = event.status === 'CANCELLED' ? orders.counts.paid : 0

  return (
    <section className="page-surface admin-page admin-event-detail">
      <div className="admin-page-header">
        <div>
          <Space align="center" wrap>
            <Typography.Title level={1}>{event.name}</Typography.Title>
            <EventStatusTag status={event.status} />
            {event.salesStatus ? <EventSalesStatusTag status={event.salesStatus} /> : null}
          </Space>
          <Typography.Text type="secondary">
            {event.venueName} · starts {formatDateTime(event.startTime)} · report generated{' '}
            {formatDateTime(generatedAt)}
          </Typography.Text>
        </div>
        <Space wrap>
          <Button
            icon={<ReloadOutlined />}
            loading={salesQuery.isFetching}
            onClick={() => {
              void salesQuery.refetch()
            }}
          >
            Refresh
          </Button>
          {event.status !== 'CANCELLED' ? (
            <Button icon={<EditOutlined />} type="primary">
              <Link to={ROUTES.adminEventEdit(event.id)}>Edit event</Link>
            </Button>
          ) : null}
          {event.status === 'PUBLISHED' ? (
            <Button icon={<ScanOutlined />}>
              <Link to={ROUTES.adminEventCheckIn(event.id)}>Check-in</Link>
            </Button>
          ) : null}
          {canCancel ? (
            <Button
              aria-label="Cancel event"
              danger
              icon={<CloseCircleOutlined />}
              onClick={() => setCancelMode('cancel')}
            >
              Cancel event
            </Button>
          ) : null}
          <Button icon={<ArrowLeftOutlined />}>
            <Link to={ROUTES.adminEvents}>Back to events</Link>
          </Button>
        </Space>
      </div>

      {event.status === 'CANCELLED' ? (
        <Alert
          action={
            awaitingRefunds > 0 ? (
              <Button size="small" onClick={() => setCancelMode('resume')}>
                Finish refunds
              </Button>
            ) : undefined
          }
          className="admin-inline-alert"
          showIcon
          title={
            awaitingRefunds > 0
              ? `This event was cancelled. ${awaitingRefunds} paid ${awaitingRefunds === 1 ? 'order is' : 'orders are'} still waiting for a refund.`
              : 'This event was cancelled. It is closed for sale and its orders have been refunded.'
          }
          type="warning"
        />
      ) : null}

      {event.status === 'DRAFT' ? (
        <Alert
          className="admin-inline-alert"
          showIcon
          title="This event is still a draft, so nothing is on sale yet. Publish it to create seat inventory."
          type="info"
        />
      ) : null}

      <div className="dashboard-tiles">
        <Card title="Seats sold">
          <Statistic value={inventory.seatsSold} suffix={`of ${inventory.seatsTotal}`} />
          <Progress
            percent={Number(sellThrough.toFixed(1))}
            size="small"
            status={sellThrough >= 100 ? 'success' : 'active'}
          />
        </Card>

        <Card title="Still available">
          <Statistic value={inventory.seatsAvailable} />
          <Typography.Text type="secondary">
            {inventory.seatsInCheckout} in checkout, {inventory.seatsBlocked} blocked
          </Typography.Text>
        </Card>

        <Card title="Revenue">
          {revenue.length === 0 ? (
            <>
              <Statistic value={0} />
              <Typography.Text type="secondary">No orders yet</Typography.Text>
            </>
          ) : (
            <Space orientation="vertical" size={4}>
              {revenue.map((entry) => (
                <div key={entry.currency}>
                  <Typography.Text strong>
                    {formatMoney(entry.paidAmount, entry.currency)}
                  </Typography.Text>
                  <br />
                  <Typography.Text type="secondary">
                    {entry.paidOrders} paid, {entry.pendingOrders} awaiting payment (
                    {formatMoney(entry.pendingAmount, entry.currency)})
                  </Typography.Text>
                  {entry.refundedOrders > 0 ? (
                    <>
                      <br />
                      <Typography.Text type="secondary">
                        {entry.refundedOrders} refunded ({formatMoney(entry.refundedAmount, entry.currency)})
                      </Typography.Text>
                    </>
                  ) : null}
                </div>
              ))}
            </Space>
          )}
        </Card>

        <Card title="Tickets">
          <Statistic value={tickets.issued} suffix="issued" />
          <Typography.Text type="secondary">
            {tickets.used} checked in, {tickets.cancelled} cancelled
          </Typography.Text>
        </Card>
      </div>

      <Card title="Schedule and inventory">
        <Descriptions bordered column={{ xs: 1, md: 2 }} size="small">
          <Descriptions.Item label="Venue">
            {event.venueName} ({event.venueTimezone})
          </Descriptions.Item>
          <Descriptions.Item label="Event start">{formatDateTime(event.startTime)}</Descriptions.Item>
          <Descriptions.Item label="Sales open">
            {formatDateTime(event.salesStartTime)}
          </Descriptions.Item>
          <Descriptions.Item label="Sales close">
            {formatDateTime(event.salesEndTime)}
          </Descriptions.Item>
          <Descriptions.Item label="Inventory value">
            {formatAmount(inventory.inventoryValue)}
          </Descriptions.Item>
          <Descriptions.Item label="Sold value">{formatAmount(inventory.soldValue)}</Descriptions.Item>
          <Descriptions.Item label="Orders" span={{ xs: 1, md: 2 }}>
            <Space size={4} wrap>
              <Tag color="green">{orders.counts.paid} paid</Tag>
              <Tag color="gold">{orders.counts.pending} pending</Tag>
              {orders.counts.failed > 0 ? <Tag color="red">{orders.counts.failed} failed</Tag> : null}
              {orders.counts.cancelled > 0 ? <Tag>{orders.counts.cancelled} cancelled</Tag> : null}
              {orders.counts.refunded > 0 ? <Tag color="purple">{orders.counts.refunded} refunded</Tag> : null}
              <Typography.Text type="secondary">{orders.counts.total} in total</Typography.Text>
            </Space>
          </Descriptions.Item>
          {event.description ? (
            <Descriptions.Item label="Description" span={{ xs: 1, md: 2 }}>
              {event.description}
            </Descriptions.Item>
          ) : null}
        </Descriptions>
      </Card>

      {event.status === 'DRAFT' || event.status === 'PUBLISHED' ? <EventReminderCard eventId={event.id} /> : null}

      <Card title="Sales by section">
        {sections.length === 0 ? (
          <Empty description="No section pricing yet" image={Empty.PRESENTED_IMAGE_SIMPLE} />
        ) : (
          <Table<EventSalesSection>
            columns={sectionColumns}
            dataSource={sections}
            pagination={false}
            rowKey="venueSectionId"
            size="middle"
          />
        )}
      </Card>

      <Card title="Seat map">
        <AdminSeatMap eventId={event.id} />
      </Card>

      <Card title="Paid demand, last 30 days">
        <SalesTrendChart points={dailySales} />
      </Card>

      <Card title="Recent orders">
        {orders.recent.length === 0 ? (
          <Empty description="No orders yet" image={Empty.PRESENTED_IMAGE_SIMPLE} />
        ) : (
          <Table<EventSalesRecentOrder>
            columns={buildOrderColumns(setOrderToRefund)}
            dataSource={orders.recent}
            pagination={false}
            rowKey="orderId"
            size="middle"
          />
        )}
      </Card>
      <RefundOrderModal order={orderToRefund} onClose={() => setOrderToRefund(null)} />
      <CancelEventModal
        event={cancelMode ? event : null}
        mode={cancelMode ?? 'cancel'}
        paidOrders={orders.counts.paid}
        pendingOrders={orders.counts.pending}
        onClose={() => setCancelMode(null)}
      />
    </section>
  )
}

const sectionColumns: ColumnsType<EventSalesSection> = [
  {
    title: 'Section',
    key: 'name',
    render: (_, section) => (
      <Space orientation="vertical" size={0}>
        <Typography.Text strong>{section.name}</Typography.Text>
        {section.salesEnabled ? null : <Tag color="orange">Sales off</Tag>}
      </Space>
    ),
  },
  {
    title: 'Price',
    dataIndex: 'price',
    key: 'price',
    align: 'right',
    render: (price: number) => formatAmount(price),
  },
  {
    title: 'Sold',
    key: 'sold',
    render: (_, section) => (
      <Space orientation="vertical" size={0}>
        <span>
          {section.seatsSold} of {section.seatsTotal}
        </span>
        <Progress
          percent={
            section.seatsTotal === 0
              ? 0
              : Number(((section.seatsSold / section.seatsTotal) * 100).toFixed(1))
          }
          size="small"
        />
      </Space>
    ),
  },
  {
    title: 'Available',
    dataIndex: 'seatsAvailable',
    key: 'seatsAvailable',
    align: 'right',
    responsive: ['md'],
  },
  {
    title: 'Blocked',
    dataIndex: 'seatsBlocked',
    key: 'seatsBlocked',
    align: 'right',
    responsive: ['lg'],
  },
  {
    title: 'Sold value',
    dataIndex: 'soldValue',
    key: 'soldValue',
    align: 'right',
    render: (value: number) => formatAmount(value),
  },
]

function buildOrderColumns(
  onRefund: (order: RefundableOrder) => void,
): ColumnsType<EventSalesRecentOrder> {
  return [
    {
      title: 'Buyer',
      dataIndex: 'buyerEmail',
      key: 'buyerEmail',
    },
    {
      title: 'Status',
      dataIndex: 'status',
      key: 'status',
      width: 130,
      render: (status: EventSalesRecentOrder['status']) => (
        <Tag color={orderStatusColor[status]}>{status}</Tag>
      ),
    },
    {
      title: 'Seats',
      dataIndex: 'seatCount',
      key: 'seatCount',
      align: 'right',
      width: 90,
    },
    {
      title: 'Total',
      key: 'totalAmount',
      align: 'right',
      render: (_, order) => formatMoney(order.totalAmount, order.currency),
    },
    {
      title: 'Placed',
      dataIndex: 'createdAt',
      key: 'createdAt',
      responsive: ['md'],
      render: (value: string) => formatDateTime(value),
    },
    {
      title: 'Actions',
      key: 'actions',
      align: 'right',
      width: 120,
      render: (_, order) => (
        order.status === 'PAID' ? (
          <Button
            aria-label={`Refund order for ${order.buyerEmail}`}
            danger
            icon={<RollbackOutlined />}
            size="small"
            onClick={() => onRefund(order)}
          >
            Refund
          </Button>
        ) : null
      ),
    },
  ]
}

function formatDateTime(value: string) {
  return new Intl.DateTimeFormat(undefined, {
    dateStyle: 'medium',
    timeStyle: 'short',
  }).format(new Date(value))
}

function formatAmount(value: number) {
  return new Intl.NumberFormat(undefined, { maximumFractionDigits: 2 }).format(value)
}

function formatMoney(value: number, currency: string) {
  return `${formatAmount(value)} ${currency}`
}
