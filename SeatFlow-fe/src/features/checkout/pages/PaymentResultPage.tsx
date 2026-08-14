import { ArrowLeftOutlined, CheckCircleOutlined } from '@ant-design/icons'
import { useQuery } from '@tanstack/react-query'
import { Alert, Button, Card, Descriptions, List, Result, Space, Spin, Steps, Tag, Typography } from 'antd'
import { Link, useNavigate, useParams } from 'react-router-dom'

import { ROUTES } from '../../../shared/constants/routes'
import { formatDateTime } from '../../events/utils/eventFormatters'
import type { PaymentStatus } from '../types'
import { getPaymentResult } from '../utils/checkoutStorage'
import { listTickets, ticketQueryKeys } from '../../tickets/api/ticketsApi'

export function PaymentResultPage() {
  const { holdId } = useParams()
  const navigate = useNavigate()
  const result = holdId ? getPaymentResult(holdId) : null
  const ticketsQuery = useQuery({
    queryKey: ticketQueryKeys.list(),
    queryFn: listTickets,
    enabled: result?.payment.status === 'SUCCEEDED',
  })

  if (!holdId || !result) {
    return (
      <section className="page-surface checkout-page">
        <Result
          status="404"
          title="Payment result not found"
          subTitle="Return to checkout from an active hold or open your tickets."
          extra={[
            <Button key="events" onClick={() => navigate(ROUTES.events)}>Events</Button>,
            <Button key="tickets" type="primary" onClick={() => navigate(ROUTES.tickets)}>Tickets</Button>,
          ]}
        />
      </section>
    )
  }

  const statusView = paymentStatusView(result.payment.status)
  const tickets = ticketsQuery.data?.filter((ticket) => ticket.orderId === result.order.id) ?? []

  return (
    <section className="page-surface checkout-page">
      <Button icon={<ArrowLeftOutlined />} type="link" onClick={() => navigate(ROUTES.events)}>
        Events
      </Button>

      <Steps
        current={2}
        items={[
          { title: 'Select seats' },
          { title: 'Checkout' },
          { title: 'Result' },
        ]}
      />

      <Card className="checkout-card">
        <Result
          status={statusView.status}
          title={statusView.title}
          subTitle={statusView.subtitle}
          extra={[
            result.payment.status === 'SUCCEEDED' ? (
              <Button key="tickets" type="primary" onClick={() => navigate(ROUTES.tickets)}>
                Tickets
              </Button>
            ) : null,
            <Button key="events" onClick={() => navigate(ROUTES.events)}>
              Events
            </Button>,
          ].filter(Boolean)}
        />

        <Card className="checkout-section-card" title="Payment result">
          <Descriptions bordered column={1}>
            <Descriptions.Item label="Order">{result.order.id}</Descriptions.Item>
            <Descriptions.Item label="Payment">{result.payment.id}</Descriptions.Item>
            <Descriptions.Item label="Status">
              <Tag color={paymentTagColor(result.payment.status)}>{result.payment.status}</Tag>
            </Descriptions.Item>
            <Descriptions.Item label="Amount">
              {formatPrice(result.payment.amount)} {result.order.currency}
            </Descriptions.Item>
            {result.payment.failureReason ? (
              <Descriptions.Item label="Reason">{result.payment.failureReason}</Descriptions.Item>
            ) : null}
          </Descriptions>
        </Card>

        {result.payment.status === 'SUCCEEDED' ? (
          <Card className="checkout-section-card" title="Issued tickets">
            {ticketsQuery.isLoading ? (
              <Spin />
            ) : ticketsQuery.isError ? (
              <Alert showIcon type="error" message="Unable to load issued tickets" />
            ) : (
              <List
                dataSource={tickets}
                locale={{ emptyText: 'Tickets are being prepared.' }}
                renderItem={(ticket) => (
                  <List.Item
                    actions={[
                      <Link key="open" to={ROUTES.ticketDetail(ticket.id)}>Open</Link>,
                    ]}
                  >
                    <List.Item.Meta
                      avatar={<CheckCircleOutlined className="ticket-list-icon" />}
                      title={ticket.event.name}
                      description={(
                        <Space wrap>
                          <span>{formatDateTime(ticket.event.startTime, ticket.event.venueTimezone)}</span>
                          <Tag>{ticket.seat.sectionName} {ticket.seat.seatLabel}</Tag>
                        </Space>
                      )}
                    />
                  </List.Item>
                )}
              />
            )}
          </Card>
        ) : (
          <Alert
            showIcon
            type={result.payment.status === 'TIMED_OUT' ? 'warning' : 'error'}
            message={statusView.title}
            description="No tickets were issued for this payment attempt."
          />
        )}

        <Typography.Text type="secondary">
          Refreshing this page will not repeat the purchase.
        </Typography.Text>
      </Card>
    </section>
  )
}

function paymentStatusView(status: PaymentStatus) {
  if (status === 'SUCCEEDED') {
    return {
      status: 'success' as const,
      title: 'Payment succeeded',
      subtitle: 'Your tickets have been issued.',
    }
  }
  if (status === 'DECLINED') {
    return {
      status: 'error' as const,
      title: 'Payment declined',
      subtitle: 'The simulated provider declined the payment. Your seats were not sold.',
    }
  }
  if (status === 'TIMED_OUT') {
    return {
      status: 'warning' as const,
      title: 'Payment timed out',
      subtitle: 'The simulated provider did not respond in time. Your seats were not sold.',
    }
  }
  return {
    status: 'error' as const,
    title: 'Payment failed',
    subtitle: 'The simulated payment failed. Your seats were not sold.',
  }
}

function paymentTagColor(status: PaymentStatus) {
  if (status === 'SUCCEEDED') {
    return 'green'
  }
  if (status === 'TIMED_OUT') {
    return 'gold'
  }
  return 'red'
}

function formatPrice(value: number) {
  return new Intl.NumberFormat(undefined, { maximumFractionDigits: 2 }).format(value)
}
