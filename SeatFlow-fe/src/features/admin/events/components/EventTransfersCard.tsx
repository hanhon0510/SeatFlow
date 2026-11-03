import { useQuery } from '@tanstack/react-query'
import { Alert, Card, Empty, Skeleton, Table } from 'antd'
import type { ColumnsType } from 'antd/es/table'

import { apiErrorMessage } from '../../../../shared/api/apiError'
import { eventQueryKeys, getEventTransfers } from '../api/eventsApi'
import type { EventTransfer } from '../types'

const columns: ColumnsType<EventTransfer> = [
  {
    title: 'Seat',
    key: 'seat',
    render: (_, transfer) => `${transfer.sectionName}, row ${transfer.rowLabel}, seat ${transfer.seatLabel}`,
  },
  { title: 'From', dataIndex: 'fromEmail', key: 'fromEmail' },
  { title: 'To', dataIndex: 'toEmail', key: 'toEmail' },
  {
    title: 'When',
    dataIndex: 'transferredAt',
    key: 'transferredAt',
    render: (value: string) =>
      new Intl.DateTimeFormat(undefined, { dateStyle: 'medium', timeStyle: 'short' }).format(new Date(value)),
  },
]

/** Every ticket of the event that changed hands, newest first. */
export function EventTransfersCard({ eventId }: { eventId: string }) {
  const transfersQuery = useQuery({
    queryKey: eventQueryKeys.transfers(eventId),
    queryFn: () => getEventTransfers(eventId),
  })

  return (
    <Card title="Ticket transfers">
      {transfersQuery.isLoading ? <Skeleton active paragraph={{ rows: 2 }} title={false} /> : null}
      {transfersQuery.isError ? (
        <Alert showIcon title={apiErrorMessage(transfersQuery.error, 'Could not load ticket transfers')} type="error" />
      ) : null}
      {transfersQuery.data ? (
        transfersQuery.data.length === 0 ? (
          <Empty description="No ticket has changed hands" image={Empty.PRESENTED_IMAGE_SIMPLE} />
        ) : (
          <Table<EventTransfer>
            columns={columns}
            dataSource={transfersQuery.data}
            pagination={false}
            rowKey="id"
            size="middle"
          />
        )
      ) : null}
    </Card>
  )
}
