import { ArrowLeftOutlined } from '@ant-design/icons'
import { useQuery } from '@tanstack/react-query'
import { Alert, Button, Card, Descriptions, Empty, Spin, Space, Tag, Typography } from 'antd'
import { useNavigate, useParams } from 'react-router-dom'

import { apiErrorMessage } from '../../../shared/api/apiError'
import { ROUTES } from '../../../shared/constants/routes'
import { getPublicEvent, publicEventQueryKeys } from '../api/eventsApi'
import { formatDateTime, formatMinimumPrice } from '../utils/eventFormatters'

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

  return (
    <section className="page-surface event-detail-page">
      <Button icon={<ArrowLeftOutlined />} type="link" onClick={() => navigate(ROUTES.events)}>
        Events
      </Button>

      <div className="public-page-header">
        <Space orientation="vertical" size={8}>
          <Typography.Title level={1}>{event.name}</Typography.Title>
          <Space wrap>
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
    </section>
  )
}
