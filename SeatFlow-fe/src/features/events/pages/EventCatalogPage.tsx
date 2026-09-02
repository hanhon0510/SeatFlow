import { useMemo, useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { Alert, Card, DatePicker, Empty, Input, List, Pagination, Select, Spin, Space, Tag, Typography } from 'antd'
import type { Dayjs } from 'dayjs'
import { NavLink } from 'react-router-dom'

import { apiErrorMessage } from '../../../shared/api/apiError'
import { ROUTES } from '../../../shared/constants/routes'
import { listPublicEvents, publicEventQueryKeys } from '../api/eventsApi'
import type { EventCatalogFilters, EventCatalogSort, EventSalesStatus, PublicEvent } from '../types'
import { formatDateTime, formatMinimumPrice } from '../utils/eventFormatters'
import { salesStatusOptions, salesStatusPresentation } from '../utils/salesStatus'

const { RangePicker } = DatePicker
const pageSize = 12

type DateRangeValue = [Dayjs | null, Dayjs | null] | null

export function EventCatalogPage() {
  const [searchInput, setSearchInput] = useState('')
  const [search, setSearch] = useState('')
  const [venueId, setVenueId] = useState<string>()
  const [dateRange, setDateRange] = useState<DateRangeValue>(null)
  const [statuses, setStatuses] = useState<EventSalesStatus[]>([])
  const [sort, setSort] = useState<EventCatalogSort>('recommended')
  const [page, setPage] = useState(0)

  const filters = useMemo<EventCatalogFilters>(() => ({
    search: search || undefined,
    venueId,
    startDate: dateRange?.[0]?.startOf('day').toDate().toISOString(),
    endDate: dateRange?.[1]?.endOf('day').toDate().toISOString(),
    // Left empty the server keeps events a buyer can still act on and drops the ones that
    // have already happened.
    statuses,
    page,
    size: pageSize,
    sort,
  }), [dateRange, page, search, sort, statuses, venueId])

  const eventQuery = useQuery({
    queryKey: publicEventQueryKeys.list(filters),
    queryFn: () => listPublicEvents(filters),
  })

  const venueOptionsQuery = useQuery({
    queryKey: publicEventQueryKeys.venueOptions(),
    queryFn: () =>
      listPublicEvents({
        statuses: [],
        page: 0,
        size: 100,
        sort: 'startAsc',
      }),
  })

  const venueOptions = useMemo(() => {
    const seen = new Set<string>()
    return (venueOptionsQuery.data?.items ?? []).flatMap((event) => {
      if (seen.has(event.venueId)) {
        return []
      }
      seen.add(event.venueId)
      return [{
        value: event.venueId,
        label: `${event.venueName} - ${event.venueCity}`,
      }]
    })
  }, [venueOptionsQuery.data?.items])

  const events = eventQuery.data?.items ?? []

  const submitSearch = (value: string) => {
    setSearch(value.trim())
    setPage(0)
  }

  return (
    <section className="page-surface event-catalog-page">
      <div className="public-page-header">
        <Typography.Title level={1}>Events</Typography.Title>
      </div>

      <div className="event-catalog-filters">
        <Input.Search
          allowClear
          aria-label="Search events"
          enterButton
          placeholder="Search events"
          value={searchInput}
          onChange={(event) => {
            setSearchInput(event.target.value)
            if (!event.target.value) {
              submitSearch('')
            }
          }}
          onSearch={submitSearch}
        />
        <RangePicker
          aria-label="Event date range"
          placeholder={['Start date', 'End date']}
          value={dateRange}
          onChange={(value) => {
            setDateRange(value)
            setPage(0)
          }}
        />
        <Select
          allowClear
          aria-label="Venue"
          loading={venueOptionsQuery.isLoading}
          optionFilterProp="label"
          options={venueOptions}
          placeholder="Venue"
          showSearch
          value={venueId}
          onChange={(value) => {
            setVenueId(value)
            setPage(0)
          }}
        />
        <Select
          allowClear
          aria-label="Sales status"
          mode="multiple"
          options={salesStatusOptions}
          placeholder="Any current status"
          value={statuses}
          onChange={(value: EventSalesStatus[]) => {
            setStatuses(value)
            setPage(0)
          }}
        />
        <Select
          aria-label="Sort events"
          options={[
            { value: 'recommended', label: 'Recommended' },
            { value: 'startAsc', label: 'Soonest' },
            { value: 'startDesc', label: 'Latest' },
            { value: 'priceAsc', label: 'Lowest price' },
            { value: 'priceDesc', label: 'Highest price' },
          ]}
          value={sort}
          onChange={(value) => {
            setSort(value)
            setPage(0)
          }}
        />
      </div>

      {eventQuery.isError ? (
        <Alert
          showIcon
          type="error"
          message={apiErrorMessage(eventQuery.error, 'Unable to load events')}
        />
      ) : null}

      {eventQuery.isLoading ? (
        <div className="catalog-loading">
          <Spin />
        </div>
      ) : events.length === 0 ? (
        <Empty description="No events match these filters" />
      ) : (
        <>
          <List
            grid={{ gutter: 16, xs: 1, sm: 2, lg: 3 }}
            dataSource={events}
            renderItem={(event) => (
              <List.Item>
                <EventCard event={event} />
              </List.Item>
            )}
          />
          <Pagination
            current={page + 1}
            pageSize={pageSize}
            showSizeChanger={false}
            total={eventQuery.data?.totalItems ?? 0}
            onChange={(nextPage) => setPage(nextPage - 1)}
          />
        </>
      )}
    </section>
  )
}

function EventCard({ event }: { event: PublicEvent }) {
  const status = salesStatusPresentation(event.salesStatus)

  return (
    <Card
      className="event-card"
      extra={<Tag color={status.color}>{status.label}</Tag>}
      title={<NavLink to={ROUTES.eventDetail(event.id)}>{event.name}</NavLink>}
    >
      <Space orientation="vertical" size={10}>
        <Typography.Text strong>{event.venueName}</Typography.Text>
        <Typography.Text type="secondary">
          {event.venueCity}, {event.venueCountry}
        </Typography.Text>
        <Typography.Text>{formatDateTime(event.startTime, event.venueTimezone)}</Typography.Text>
        <Space wrap>
          <Tag>{event.venueTimezone}</Tag>
          <Tag color="blue">{formatMinimumPrice(event.minimumPrice)}</Tag>
        </Space>
      </Space>
    </Card>
  )
}
