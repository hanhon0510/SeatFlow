import { ArrowLeftOutlined } from '@ant-design/icons'
import { useQuery } from '@tanstack/react-query'
import { Alert, Button, Card, Descriptions, Empty, Spin, Space, Tag, Typography } from 'antd'
import { useNavigate, useParams } from 'react-router-dom'

import { apiErrorMessage } from '../../../shared/api/apiError'
import { ROUTES } from '../../../shared/constants/routes'
import { getPublicEvent, publicEventQueryKeys } from '../api/eventsApi'
import { EventSeatMap } from '../components/EventSeatMap'
import type { PublicEvent } from '../types'
import { formatDateTime, formatMinimumPrice } from '../utils/eventFormatters'
import { isSeatSelectionOpen, salesStatusPresentation } from '../utils/salesStatus'

export function EventDetailPage() {
  const { eventId } = useParams()
  const navigate = useNavigate()
  const eventQuery = useQuery({
    queryKey: eventId ? publicEventQueryKeys.detail(eventId) : publicEventQueryKeys.detail(''),
    queryFn: () => getPublicEvent(eventId ?? ''),
    enabled: Boolean(eventId),
  })

  if (!eventId) {
    return (
      <section className="page-surface event-detail-page">
        <Empty description="Event not found" />
      </section>
    )
  }

  if (eventQuery.isLoading) {
    return (
      <section className="page-surface event-detail-page catalog-loading">
        <Spin />
      </section>
    )
  }

  if (eventQuery.isError) {
    return (
      <section className="page-surface event-detail-page">
        <Alert
          showIcon
          type="error"
          message={apiErrorMessage(eventQuery.error, 'Unable to load event')}
        />
      </section>
    )
  }

  const event = eventQuery.data
  if (!event) {
    return (
      <section className="page-surface event-detail-page">
        <Empty description="Event not found" />
      </section>
    )
  }

  const status = salesStatusPresentation(event.salesStatus)

  return (
    <section className="page-surface event-detail-page">
      <Button icon={<ArrowLeftOutlined />} type="link" onClick={() => navigate(ROUTES.events)}>
        Events
      </Button>

      <div className="public-page-header">
        <Space orientation="vertical" size={8}>
          <Typography.Title level={1}>{event.name}</Typography.Title>
          <Space wrap>
            <Tag color={status.color}>{status.label}</Tag>
            <Tag>{event.venueTimezone}</Tag>
            <Tag color="blue">{formatMinimumPrice(event.minimumPrice)}</Tag>
          </Space>
        </Space>
      </div>

      <Card className="event-detail-card">
        <Descriptions column={1} bordered>
          <Descriptions.Item label="Venue">{event.venueName}</Descriptions.Item>
          <Descriptions.Item label="Location">
            {event.venueAddress}, {event.venueCity}, {event.venueCountry}
          </Descriptions.Item>
          <Descriptions.Item label="Time">
            {formatDateTime(event.startTime, event.venueTimezone)}
          </Descriptions.Item>
          <Descriptions.Item label="Sales window">
            {formatDateTime(event.salesStartTime, event.venueTimezone)} to{' '}
            {formatDateTime(event.salesEndTime, event.venueTimezone)}
          </Descriptions.Item>
        </Descriptions>
        {event.description ? (
          <Typography.Paragraph className="event-detail-description">
            {event.description}
          </Typography.Paragraph>
        ) : null}
      </Card>

      {isSeatSelectionOpen(event.salesStatus) ? (
        <EventSeatMap key={event.id} eventId={event.id} />
      ) : (
        <Alert showIcon type="info" message={closedSalesMessage(event)} />
      )}
    </section>
  )
}

/**
 * The API rejects a hold outside the sales window, so the seat map is withheld rather than
 * letting a visitor pick seats that can never be held.
 */
function closedSalesMessage(event: PublicEvent) {
  if (event.salesStatus === 'UPCOMING') {
    return `Seat selection opens when sales start on ${formatDateTime(event.salesStartTime, event.venueTimezone)}.`
  }
  if (event.salesStatus === 'SALES_CLOSED') {
    return `Sales closed on ${formatDateTime(event.salesEndTime, event.venueTimezone)}.`
  }
  return 'This event has already taken place.'
}
