import { useQuery } from '@tanstack/react-query'
import { Alert, Card, Skeleton, Space, Statistic, Typography } from 'antd'

import { apiErrorMessage } from '../../../../shared/api/apiError'
import { eventQueryKeys, getEventWaitlist } from '../api/eventsApi'

/** How much demand is queued behind a sold-out event, and how the offers made from it went. */
export function EventWaitlistCard({ eventId }: { eventId: string }) {
  const waitlistQuery = useQuery({
    queryKey: eventQueryKeys.waitlist(eventId),
    queryFn: () => getEventWaitlist(eventId),
  })
  const waitlist = waitlistQuery.data

  return (
    <Card title="Waitlist">
      {waitlistQuery.isLoading ? <Skeleton active paragraph={{ rows: 2 }} title={false} /> : null}
      {waitlistQuery.isError ? (
        <Alert showIcon title={apiErrorMessage(waitlistQuery.error, 'Could not load the waitlist')} type="error" />
      ) : null}
      {waitlist ? (
        <Space orientation="vertical" size="middle" style={{ width: '100%' }}>
          <Space size="large" wrap>
            <Statistic title="Waiting" value={waitlist.waiting} />
            <Statistic title="Seats wanted" value={waitlist.waitingSeats} />
            <Statistic title="Offered now" value={waitlist.offered} />
            <Statistic title="Took their seats" value={waitlist.fulfilled} />
            <Statistic title="Offer lapsed" value={waitlist.expired} />
            <Statistic title="Left the list" value={waitlist.cancelled} />
          </Space>
          <Typography.Text type="secondary">
            Buyers join when an event has no free seats. Seats that come back are offered to them in the order they
            joined, and kept for each buyer for a short time.
          </Typography.Text>
        </Space>
      ) : null}
    </Card>
  )
}
