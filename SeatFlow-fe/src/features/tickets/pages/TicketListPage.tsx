import { ArrowRightOutlined, QrcodeOutlined } from '@ant-design/icons'
import { useQuery } from '@tanstack/react-query'
import { Alert, Button, Card, List, Space, Spin, Tag, Typography } from 'antd'
import { Link } from 'react-router-dom'

import { apiErrorMessage } from '../../../shared/api/apiError'
import { ROUTES } from '../../../shared/constants/routes'
import { formatDateTime } from '../../events/utils/eventFormatters'
import { listTickets, ticketQueryKeys } from '../api/ticketsApi'

export function TicketListPage() {
  const ticketsQuery = useQuery({
    queryKey: ticketQueryKeys.list(),
    queryFn: listTickets,
  })

  return (
    <section className="page-surface ticket-page">
      <div className="public-page-header">
        <Typography.Title level={1}>Tickets</Typography.Title>
      </div>

      <Card>
        {ticketsQuery.isLoading ? (
          <div className="catalog-loading">
            <Spin />
          </div>
        ) : ticketsQuery.isError ? (
          <Alert
            showIcon
            type="error"
            message={apiErrorMessage(ticketsQuery.error, 'Unable to load tickets')}
          />
        ) : (
          <List
            dataSource={ticketsQuery.data ?? []}
            locale={{ emptyText: 'No tickets yet' }}
            renderItem={(ticket) => (
              <List.Item
                actions={[
                  <Button
                    key="open"
                    icon={<ArrowRightOutlined />}
                    type="link"
                  >
                    <Link to={ROUTES.ticketDetail(ticket.id)}>Open</Link>
                  </Button>,
                ]}
              >
                <List.Item.Meta
                  avatar={<QrcodeOutlined className="ticket-list-icon" />}
                  title={<Link to={ROUTES.ticketDetail(ticket.id)}>{ticket.event.name}</Link>}
                  description={(
                    <Space wrap>
                      <span>{formatDateTime(ticket.event.startTime, ticket.event.venueTimezone)}</span>
                      <Tag>{ticket.event.venueName}</Tag>
                      <Tag>{ticket.seat.sectionName} {ticket.seat.seatLabel}</Tag>
                      <Tag color={ticket.status === 'ACTIVE' ? 'green' : 'default'}>{ticket.status}</Tag>
                    </Space>
                  )}
                />
              </List.Item>
            )}
          />
        )}
      </Card>
    </section>
  )
}
