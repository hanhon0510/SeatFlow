import { useState } from 'react'
import { EditOutlined, InboxOutlined, PlusOutlined } from '@ant-design/icons'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Alert, Button, Empty, Modal, Space, Spin, Table, Tag, Typography } from 'antd'
import type { ColumnsType, TablePaginationConfig } from 'antd/es/table'
import { Link } from 'react-router-dom'

import { apiErrorMessage } from '../../../../shared/api/apiError'
import { ROUTES } from '../../../../shared/constants/routes'
import {
  archiveVenue,
  listVenues,
  venueQueryKeys,
} from '../api/venuesApi'
import type { Venue } from '../types'

const pageSizeOptions = [10, 20, 50]

export function VenueListPage() {
  const [page, setPage] = useState(0)
  const [size, setSize] = useState(10)
  const [venueToArchive, setVenueToArchive] = useState<Venue | null>(null)
  const [archiveError, setArchiveError] = useState<string | null>(null)
  const queryClient = useQueryClient()

  const venuesQuery = useQuery({
    queryKey: venueQueryKeys.list(page, size),
    queryFn: () => listVenues(page, size),
  })

  const archiveMutation = useMutation({
    mutationFn: archiveVenue,
    onSuccess: async () => {
      setVenueToArchive(null)
      setArchiveError(null)
      await queryClient.invalidateQueries({ queryKey: venueQueryKeys.all })
    },
    onError: (error) => {
      setArchiveError(apiErrorMessage(error, 'Could not archive venue'))
    },
  })

  const columns: ColumnsType<Venue> = [
    {
      title: 'Venue',
      dataIndex: 'name',
      key: 'name',
      render: (name, venue) => (
        <Space orientation="vertical" size={0}>
          <Typography.Text strong>{name}</Typography.Text>
          <Typography.Text type="secondary">{venue.address}</Typography.Text>
        </Space>
      ),
    },
    {
      title: 'Location',
      key: 'location',
      responsive: ['md'],
      render: (_, venue) => `${venue.city}, ${venue.country}`,
    },
    {
      title: 'Timezone',
      dataIndex: 'timezone',
      key: 'timezone',
      responsive: ['lg'],
    },
    {
      title: 'Status',
      dataIndex: 'status',
      key: 'status',
      width: 120,
      render: (status) => (
        <Tag color={status === 'ACTIVE' ? 'green' : 'default'}>
          {status === 'ACTIVE' ? 'Active' : 'Archived'}
        </Tag>
      ),
    },
    {
      title: 'Actions',
      key: 'actions',
      align: 'right',
      width: 220,
      render: (_, venue) => (
        <Space wrap>
          <Button icon={<EditOutlined />}>
            <Link to={ROUTES.adminVenueEdit(venue.id)}>Edit</Link>
          </Button>
          <Button
            aria-label={`Archive ${venue.name}`}
            danger
            disabled={venue.status === 'ARCHIVED'}
            icon={<InboxOutlined />}
            onClick={() => {
              setArchiveError(null)
              setVenueToArchive(venue)
            }}
          >
            Archive
          </Button>
        </Space>
      ),
    },
  ]

  const handleTableChange = (pagination: TablePaginationConfig) => {
    setPage(Math.max(0, (pagination.current ?? 1) - 1))
    setSize(pagination.pageSize ?? size)
  }

  const handleArchive = () => {
    if (venueToArchive) {
      archiveMutation.mutate(venueToArchive.id)
    }
  }

  return (
    <section className="page-surface admin-page">
      <div className="admin-page-header">
        <Typography.Title level={1}>Venues</Typography.Title>
        <Button icon={<PlusOutlined />} type="primary">
          <Link to={ROUTES.adminVenueNew}>Create venue</Link>
        </Button>
      </div>

      {venuesQuery.isError ? (
        <Alert
          className="admin-inline-alert"
          message={apiErrorMessage(venuesQuery.error, 'Could not load venues')}
          showIcon
          type="error"
        />
      ) : null}

      <Spin spinning={venuesQuery.isLoading}>
        <Table<Venue>
          columns={columns}
          dataSource={venuesQuery.data?.items ?? []}
          locale={{ emptyText: <Empty description="No venues yet" /> }}
          pagination={{
            current: page + 1,
            pageSize: size,
            pageSizeOptions,
            showSizeChanger: true,
            total: venuesQuery.data?.totalItems ?? 0,
          }}
          rowKey="id"
          onChange={handleTableChange}
        />
      </Spin>

      <Modal
        confirmLoading={archiveMutation.isPending}
        okButtonProps={{ danger: true }}
        okText="Archive venue"
        open={Boolean(venueToArchive)}
        title="Archive venue"
        onCancel={() => {
          if (!archiveMutation.isPending) {
            setVenueToArchive(null)
            setArchiveError(null)
          }
        }}
        onOk={handleArchive}
      >
        {archiveError ? (
          <Alert className="admin-inline-alert" message={archiveError} showIcon type="error" />
        ) : null}
        <Typography.Paragraph>
          Archive {venueToArchive?.name}? Archived venues remain visible and cannot host new events.
        </Typography.Paragraph>
      </Modal>
    </section>
  )
}
