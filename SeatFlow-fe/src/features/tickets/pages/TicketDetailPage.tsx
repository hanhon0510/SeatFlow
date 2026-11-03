import { ArrowLeftOutlined, SwapOutlined } from '@ant-design/icons'
import { useQuery } from '@tanstack/react-query'
import { Alert, App as AntdApp, Button, Card, Descriptions, QRCode, Result, Space, Spin, Tag, Typography } from 'antd'
import { isAxiosError } from 'axios'
import { useState } from 'react'
import { useNavigate, useParams } from 'react-router-dom'

import { apiErrorMessage } from '../../../shared/api/apiError'
import { ROUTES } from '../../../shared/constants/routes'
import { formatDateTime } from '../../events/utils/eventFormatters'
import { getTicket, ticketQueryKeys } from '../api/ticketsApi'
import { TicketStatusTag } from '../components/TicketStatusTag'
import { TransferTicketModal } from '../components/TransferTicketModal'

export function TicketDetailPage() {
  const { ticketId } = useParams()
  const navigate = useNavigate()
  const { message } = AntdApp.useApp()
  const [transferOpen, setTransferOpen] = useState(false)
  const [openedAt] = useState(() => Date.now())
  const ticketQuery = useQuery({
    queryKey: ticketId ? ticketQueryKeys.detail(ticketId) : ticketQueryKeys.detail(''),
    queryFn: () => getTicket(ticketId ?? ''),
    enabled: Boolean(ticketId),
  })

  if (!ticketId) {
    return <TicketNotFound onTickets={() => navigate(ROUTES.tickets)} />
  }

  if (ticketQuery.isLoading) {
    return (
      <section className="page-surface ticket-page catalog-loading">
        <Spin />
      </section>
    )
  }

  if (ticketQuery.isError) {
    const notFound = isAxiosError(ticketQuery.error) && ticketQuery.error.response?.status === 404
    return (
      <section className="page-surface ticket-page">
        {notFound ? (
          <TicketNotFound onTickets={() => navigate(ROUTES.tickets)} />
        ) : (
          <Alert
            showIcon
            type="error"
            message={apiErrorMessage(ticketQuery.error, 'Unable to load ticket')}
          />
        )}
      </section>
    )
  }

  const ticket = ticketQuery.data
  if (!ticket) {
    return <TicketNotFound onTickets={() => navigate(ROUTES.tickets)} />
  }

  const transferable =
    ticket.status === 'ACTIVE'
    && ticket.event.transfersAllowed
    && new Date(ticket.event.startTime).getTime() > openedAt

  return (
    <section className="page-surface ticket-page">
      <Button icon={<ArrowLeftOutlined />} type="link" onClick={() => navigate(ROUTES.tickets)}>
        Tickets
      </Button>

      <Card className="ticket-detail-card">
        <div className="ticket-detail-header">
          <Space direction="vertical" size={8}>
            <Typography.Title level={1}>{ticket.event.name}</Typography.Title>
            <Space wrap>
              <TicketStatusTag status={ticket.status} />
              <Tag>{ticket.seat.sectionName} {ticket.seat.seatLabel}</Tag>
            </Space>
            {transferable ? (
              <Button icon={<SwapOutlined />} onClick={() => setTransferOpen(true)}>
                Transfer ticket
              </Button>
            ) : null}
          </Space>
          {ticket.status === 'ACTIVE' ? (
            <div className="ticket-qr-panel">
              <QRCode type="svg" value={ticket.qrData} size={180} />
            </div>
          ) : null}
        </div>

        {ticket.status === 'ACTIVE' ? null : (
          <Alert
            showIcon
            style={{ marginBottom: 16 }}
            type={ticket.status === 'CANCELLED' ? 'error' : 'info'}
            message={ticket.status === 'CANCELLED' ? 'This ticket is no longer valid' : 'This ticket has been used'}
            description={ticket.status === 'CANCELLED'
              ? 'It was cancelled because the order was refunded. It will not be accepted at the venue.'
              : 'It was already scanned at the venue.'}
          />
        )}

        <Descriptions bordered column={1}>
          <Descriptions.Item label="Ticket">{ticket.id}</Descriptions.Item>
          <Descriptions.Item label="Code">{ticket.ticketCode}</Descriptions.Item>
          <Descriptions.Item label="Event time">
            {formatDateTime(ticket.event.startTime, ticket.event.venueTimezone)}
          </Descriptions.Item>
          <Descriptions.Item label="Venue">
            {ticket.event.venueName}, {ticket.event.venueCity}, {ticket.event.venueCountry}
          </Descriptions.Item>
          <Descriptions.Item label="Seat">
            {ticket.seat.sectionName}, row {ticket.seat.rowLabel}, seat {ticket.seat.seatLabel}
          </Descriptions.Item>
          <Descriptions.Item label="Issued">
            {formatDateTime(ticket.issuedAt, ticket.event.venueTimezone)}
          </Descriptions.Item>
          <Descriptions.Item label="QR data">{ticket.qrData}</Descriptions.Item>
        </Descriptions>
      </Card>

      {transferable ? (
        <TransferTicketModal
          eventName={ticket.event.name}
          open={transferOpen}
          ticketId={ticket.id}
          onClose={() => setTransferOpen(false)}
          onTransferred={() => {
            message.success('Ticket transferred')
            navigate(ROUTES.tickets)
          }}
        />
      ) : null}
    </section>
  )
}

function TicketNotFound({ onTickets }: { onTickets: () => void }) {
  return (
    <Result
      status="404"
      title="Ticket not found"
      subTitle="The ticket does not exist or is not available to this account."
      extra={<Button onClick={onTickets}>Tickets</Button>}
    />
  )
}
