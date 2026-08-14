import { ArrowLeftOutlined } from '@ant-design/icons'
import { useQuery } from '@tanstack/react-query'
import { Alert, Button, Card, Descriptions, QRCode, Result, Space, Spin, Tag, Typography } from 'antd'
import { isAxiosError } from 'axios'
import { useNavigate, useParams } from 'react-router-dom'

import { apiErrorMessage } from '../../../shared/api/apiError'
import { ROUTES } from '../../../shared/constants/routes'
import { formatDateTime } from '../../events/utils/eventFormatters'
import { getTicket, ticketQueryKeys } from '../api/ticketsApi'

export function TicketDetailPage() {
  const { ticketId } = useParams()
  const navigate = useNavigate()
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
              <Tag color={ticket.status === 'ACTIVE' ? 'green' : 'default'}>{ticket.status}</Tag>
              <Tag>{ticket.seat.sectionName} {ticket.seat.seatLabel}</Tag>
            </Space>
          </Space>
          <div className="ticket-qr-panel">
            <QRCode type="svg" value={ticket.qrData} size={180} />
          </div>
        </div>

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
