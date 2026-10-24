import { useQuery } from '@tanstack/react-query'
import { Alert, Button, Card, Empty, Pagination, Space, Spin, Tag, Typography } from 'antd'
import { useState } from 'react'
import { Link } from 'react-router-dom'

import { apiErrorMessage } from '../../../shared/api/apiError'
import { ROUTES } from '../../../shared/constants/routes'
import { formatDateTime } from '../../events/utils/eventFormatters'
import { TicketStatusTag } from '../../tickets/components/TicketStatusTag'
import { listOrderHistory, orderHistoryQueryKeys } from '../api/ordersApi'
import { ReceiptModal } from '../components/ReceiptModal'
import { formatDate, formatMoney, orderReference, orderStatusColor, orderStatusLabel } from '../orderFormat'
import type { OrderHistoryItem } from '../types'

const PAGE_SIZE = 10

export function OrdersPage() {
  const [page, setPage] = useState(0)
  const [receiptOrder, setReceiptOrder] = useState<OrderHistoryItem | null>(null)

  const historyQuery = useQuery({
    queryKey: orderHistoryQueryKeys.page(page, PAGE_SIZE),
    queryFn: () => listOrderHistory(page, PAGE_SIZE),
    placeholderData: (previous) => previous,
  })

  return (
    <section className="page-surface orders-page">
      <div className="public-page-header">
        <Typography.Title level={1}>My orders</Typography.Title>
      </div>

      {historyQuery.isLoading ? (
        <div className="catalog-loading">
          <Spin />
        </div>
      ) : historyQuery.isError ? (
        <Alert
          showIcon
          title={apiErrorMessage(historyQuery.error, 'Unable to load your orders')}
          type="error"
        />
      ) : historyQuery.data && historyQuery.data.items.length === 0 ? (
        <Card>
          <Empty description="You have not placed any orders yet">
            <Button type="primary">
              <Link to={ROUTES.events}>Browse events</Link>
            </Button>
          </Empty>
        </Card>
      ) : (
        <Space orientation="vertical" size="middle" style={{ width: '100%' }}>
          {historyQuery.data?.items.map((order) => (
            <OrderCard key={order.orderId} order={order} onShowReceipt={setReceiptOrder} />
          ))}
          {historyQuery.data && historyQuery.data.totalPages > 1 ? (
            <Pagination
              current={historyQuery.data.page + 1}
              pageSize={historyQuery.data.size}
              showSizeChanger={false}
              total={historyQuery.data.totalItems}
              onChange={(next) => setPage(next - 1)}
            />
          ) : null}
        </Space>
      )}

      <ReceiptModal order={receiptOrder} onClose={() => setReceiptOrder(null)} />
    </section>
  )
}

function OrderCard({
  order,
  onShowReceipt,
}: {
  order: OrderHistoryItem
  onShowReceipt: (order: OrderHistoryItem) => void
}) {
  const { event } = order
  const hasReceipt = order.status === 'PAID' || order.status === 'REFUNDED'

  return (
    <Card
      aria-label={`Order ${orderReference(order.orderId)}`}
      extra={<Tag color={orderStatusColor[order.status]}>{orderStatusLabel[order.status]}</Tag>}
      title={event.name}
    >
      <Space orientation="vertical" size="small" style={{ width: '100%' }}>
        <Typography.Text type="secondary">
          {formatDateTime(event.startTime, event.venueTimezone)} · {event.venueName}
        </Typography.Text>

        <ul className="order-seat-list">
          {order.seats.map((seat) => (
            <li key={`${seat.sectionName}-${seat.rowLabel}-${seat.seatLabel}`}>
              {`${seat.sectionName}, row ${seat.rowLabel}, seat ${seat.seatLabel}`}{' '}
              {seat.ticketStatus && seat.ticketStatus !== 'ACTIVE' ? (
                <TicketStatusTag status={seat.ticketStatus} />
              ) : null}
            </li>
          ))}
        </ul>

        <div>
          <Typography.Text strong>{formatMoney(order.totalAmount, order.currency)}</Typography.Text>
          {order.discountAmount > 0 ? (
            <Typography.Text type="secondary">
              {` · saved ${formatMoney(order.discountAmount, order.currency)}`}
              {order.promoCode ? ` with ${order.promoCode}` : ''}
            </Typography.Text>
          ) : null}
        </div>

        <Typography.Text type="secondary">
          Order {orderReference(order.orderId)} · placed {formatDate(order.createdAt)}
        </Typography.Text>

        <OrderNotice order={order} />

        <Space wrap>
          {order.status === 'PAID' ? (
            <Button>
              <Link to={ROUTES.tickets}>View tickets</Link>
            </Button>
          ) : null}
          {hasReceipt ? <Button onClick={() => onShowReceipt(order)}>Receipt</Button> : null}
        </Space>
      </Space>
    </Card>
  )
}

/** Explains anything unusual about the order, such as why money came back. */
function OrderNotice({ order }: { order: OrderHistoryItem }) {
  const { event, refund } = order

  if (event.cancelled) {
    const reason = event.cancellationReason ? ` Reason: ${event.cancellationReason}.` : ''
    return (
      <Alert
        showIcon
        title="This event was cancelled"
        description={
          refund
            ? `You were refunded ${formatMoney(refund.amount, order.currency)} on ${formatDate(refund.refundedAt)}.${reason}`
            : order.status === 'PAID'
              ? `Your refund is being processed.${reason}`
              : `No payment was taken.${reason}`
        }
        type="warning"
      />
    )
  }

  if (refund) {
    return (
      <Alert
        showIcon
        title={`Refunded ${formatMoney(refund.amount, order.currency)} on ${formatDate(refund.refundedAt)}`}
        description={refund.reason ? `Reason: ${refund.reason}` : 'The tickets from this order no longer work.'}
        type="info"
      />
    )
  }

  if (order.status === 'PENDING') {
    return <Alert showIcon title="This order is waiting for payment" type="info" />
  }

  if (order.status === 'FAILED') {
    return <Alert showIcon title="The payment did not go through, so you were not charged" type="error" />
  }

  return null
}
