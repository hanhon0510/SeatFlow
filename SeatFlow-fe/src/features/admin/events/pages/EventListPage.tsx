import { useState } from 'react'
import { EditOutlined, LineChartOutlined, PlusOutlined } from '@ant-design/icons'
import { useQuery } from '@tanstack/react-query'
import { Alert, Button, Empty, Space, Spin, Table, Typography } from 'antd'
import type { ColumnsType, TablePaginationConfig } from 'antd/es/table'
import { Link } from 'react-router-dom'

import { apiErrorMessage } from '../../../../shared/api/apiError'
import { ROUTES } from '../../../../shared/constants/routes'
import { listVenues, venueQueryKeys } from '../../venues/api/venuesApi'
import type { Venue } from '../../venues/types'
import { listEvents, eventQueryKeys } from '../api/eventsApi'
import { EventStatusTag } from '../components/EventStatusTag'
import type { Event } from '../types'

const pageSizeOptions = [10, 20, 50]

export function EventListPage() {
  const [page, setPage] = useState(0)
  const [size, setSize] = useState(10)

  const eventsQuery = useQuery({
    queryKey: eventQueryKeys.list(page, size),
    queryFn: () => listEvents(page, size),
  })
  const venuesQuery = useQuery({
    queryKey: venueQueryKeys.list(0, 100),
    queryFn: () => listVenues(0, 100),
  })

  const venueById = new Map(
    venuesQuery.data?.items.map((venue) => [venue.id, venue]) ?? [],
  )

  const columns: ColumnsType<Event> = [
    {
      title: 'Event',
      dataIndex: 'name',
      key: 'name',
      render: (name, event) => (
        <Space orientation="vertical" size={0}>
          <Link to={ROUTES.adminEventDetail(event.id)}>{name}</Link>
          <Typography.Text type="secondary">
            {event.description ?? 'No description'}
          </Typography.Text>
        </Space>
      ),
    },
    {
      title: 'Venue',
      dataIndex: 'venueId',
      key: 'venueId',
      render: (venueId) => venueLabel(venueById.get(venueId), venueId),
    },
    {
      title: 'Start',
      dataIndex: 'startTime',
      key: 'startTime',
      responsive: ['md'],
      render: (value) => formatDateTime(value),
    },
    {
      title: 'Sales window',
      key: 'sales',
      responsive: ['lg'],
      render: (_, event) => (
        <Space orientation="vertical" size={0}>
          <span>{formatDateTime(event.salesStartTime)}</span>
          <Typography.Text type="secondary">{formatDateTime(event.salesEndTime)}</Typography.Text>
        </Space>
      ),
    },
    {
      title: 'Status',
      dataIndex: 'status',
      key: 'status',
      width: 130,
      render: (status) => <EventStatusTag status={status} />,
    },
    {
      title: 'Actions',
      key: 'actions',
      align: 'right',
      width: 200,
      render: (_, event) => (
        <Space size={8}>
          <Button icon={<LineChartOutlined />}>
            <Link to={ROUTES.adminEventDetail(event.id)}>Sales</Link>
          </Button>
          <Button icon={<EditOutlined />}>
            <Link to={ROUTES.adminEventEdit(event.id)}>Edit</Link>
          </Button>
        </Space>
      ),
    },
  ]

  const handleTableChange = (pagination: TablePaginationConfig) => {
    setPage(Math.max(0, (pagination.current ?? 1) - 1))
    setSize(pagination.pageSize ?? size)
  }

  return (
    <section className="page-surface admin-page">
      <div className="admin-page-header">
        <Typography.Title level={1}>Events</Typography.Title>
        <Space wrap>
          <Button>
            <Link to={ROUTES.admin}>Venues</Link>
          </Button>
          <Button icon={<PlusOutlined />} type="primary">
            <Link to={ROUTES.adminEventNew}>Create event</Link>
          </Button>
        </Space>
      </div>

      {eventsQuery.isError ? (
        <Alert
          className="admin-inline-alert"
          showIcon
          title={apiErrorMessage(eventsQuery.error, 'Could not load events')}
          type="error"
        />
      ) : null}

      {venuesQuery.isError ? (
        <Alert
          className="admin-inline-alert"
          showIcon
          title={apiErrorMessage(venuesQuery.error, 'Could not load venues')}
          type="error"
        />
      ) : null}

      <Spin spinning={eventsQuery.isLoading || venuesQuery.isLoading}>
        <Table<Event>
          columns={columns}
          dataSource={eventsQuery.data?.items ?? []}
          locale={{ emptyText: <Empty description="No events yet" /> }}
          pagination={{
            current: page + 1,
            pageSize: size,
            pageSizeOptions,
            showSizeChanger: true,
            total: eventsQuery.data?.totalItems ?? 0,
          }}
          rowKey="id"
          onChange={handleTableChange}
        />
      </Spin>
    </section>
  )
}

function venueLabel(venue: Venue | undefined, fallbackId: string) {
  if (!venue) {
    return fallbackId
  }

  return (
    <Space orientation="vertical" size={0}>
      <Typography.Text>{venue.name}</Typography.Text>
      <Typography.Text type="secondary">{venue.timezone}</Typography.Text>
    </Space>
  )
}

function formatDateTime(value: string) {
  return new Intl.DateTimeFormat(undefined, {
    dateStyle: 'medium',
    timeStyle: 'short',
  }).format(new Date(value))
}
