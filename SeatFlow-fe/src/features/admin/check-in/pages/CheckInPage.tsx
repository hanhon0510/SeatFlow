import { ArrowLeftOutlined, ScanOutlined } from '@ant-design/icons'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Alert, Button, Card, Empty, Input, Progress, Space, Spin, Statistic, Table, Typography } from 'antd'
import type { InputRef } from 'antd'
import type { ColumnsType } from 'antd/es/table'
import { useRef, useState } from 'react'
import { Link, Navigate, useParams } from 'react-router-dom'

import { apiErrorMessage } from '../../../../shared/api/apiError'
import { ROUTES } from '../../../../shared/constants/routes'
import { checkInQueryKeys, checkInTicket, getCheckInSummary } from '../api/checkInApi'
import { CheckInResultPanel } from '../components/CheckInResultPanel'
import type { CheckInEntry, CheckInResult } from '../types'

// Several doors may be scanning at once, so the counts are refreshed in the background as well.
const SUMMARY_REFRESH_MS = 15_000

export function CheckInPage() {
  const { eventId } = useParams()
  const queryClient = useQueryClient()
  const inputRef = useRef<InputRef>(null)
  const [code, setCode] = useState('')
  const [result, setResult] = useState<CheckInResult | null>(null)

  const summaryQuery = useQuery({
    queryKey: checkInQueryKeys.summary(eventId ?? 'missing'),
    queryFn: () => getCheckInSummary(eventId ?? ''),
    enabled: Boolean(eventId),
    refetchInterval: SUMMARY_REFRESH_MS,
  })

  const scanMutation = useMutation({
    mutationFn: (scanned: string) => checkInTicket(eventId ?? '', scanned),
    onSuccess: async (scan) => {
      setResult(scan)
      setCode('')
      await queryClient.invalidateQueries({ queryKey: checkInQueryKeys.all })
    },
    onSettled: () => {
      // Ready for the next person without reaching for the mouse.
      inputRef.current?.focus()
    },
  })

  if (!eventId) {
    return <Navigate to={ROUTES.adminEvents} replace />
  }

  const submit = () => {
    const scanned = code.trim()
    if (!scanned || scanMutation.isPending) {
      return
    }
    scanMutation.mutate(scanned)
  }

  if (summaryQuery.isLoading) {
    return (
      <section className="page-surface admin-page">
        <div className="catalog-loading">
          <Spin />
        </div>
      </section>
    )
  }

  if (summaryQuery.isError || !summaryQuery.data) {
    return (
      <section className="page-surface admin-page">
        <Alert
          showIcon
          title={apiErrorMessage(summaryQuery.error, 'Could not load check-in for this event')}
          type="error"
        />
        <Space>
          <Button icon={<ArrowLeftOutlined />}>
            <Link to={ROUTES.adminEventDetail(eventId)}>Back to the event</Link>
          </Button>
        </Space>
      </section>
    )
  }

  const summary = summaryQuery.data
  const percent = summary.valid === 0 ? 0 : Math.round((summary.checkedIn / summary.valid) * 100)

  return (
    <section className="page-surface admin-page check-in-page">
      <div className="admin-page-header">
        <div>
          <Typography.Title level={1}>Check-in</Typography.Title>
          <Typography.Text type="secondary">{summary.eventName}</Typography.Text>
        </div>
        <Button icon={<ArrowLeftOutlined />}>
          <Link to={ROUTES.adminEventDetail(eventId)}>Back to the event</Link>
        </Button>
      </div>

      <Card>
        <div className="check-in-progress">
          <Statistic suffix={`of ${summary.valid}`} title="Checked in" value={summary.checkedIn} />
          <Statistic title="Still expected" value={summary.remaining} />
        </div>
        <Progress
          aria-label="Share of valid tickets checked in"
          percent={percent}
          status={summary.valid > 0 && summary.remaining === 0 ? 'success' : 'active'}
        />
      </Card>

      <Card title="Scan a ticket">
        <form
          onSubmit={(event) => {
            event.preventDefault()
            submit()
          }}
        >
          <Space.Compact block size="large">
            <Input
              aria-label="Ticket code"
              autoComplete="off"
              autoFocus
              disabled={scanMutation.isPending}
              placeholder="Scan the QR code or type the ticket code"
              prefix={<ScanOutlined />}
              ref={inputRef}
              spellCheck={false}
              value={code}
              onChange={(event) => setCode(event.target.value)}
            />
            <Button
              disabled={!code.trim()}
              htmlType="submit"
              loading={scanMutation.isPending}
              type="primary"
            >
              Check in
            </Button>
          </Space.Compact>
        </form>

        {scanMutation.isError ? (
          <Alert
            showIcon
            style={{ marginTop: 16 }}
            title={apiErrorMessage(scanMutation.error, 'The scan could not be processed. Try again.')}
            type="error"
          />
        ) : null}

        {result ? <CheckInResultPanel result={result} /> : null}
      </Card>

      <Card title="Latest check-ins">
        {summary.recent.length === 0 ? (
          <Empty description="Nobody has been checked in yet" image={Empty.PRESENTED_IMAGE_SIMPLE} />
        ) : (
          <Table<CheckInEntry>
            columns={recentColumns}
            dataSource={summary.recent}
            pagination={false}
            rowKey="ticketId"
            size="middle"
          />
        )}
      </Card>
    </section>
  )
}

const recentColumns: ColumnsType<CheckInEntry> = [
  {
    title: 'Time',
    dataIndex: 'checkedInAt',
    key: 'checkedInAt',
    width: 120,
    render: (value: string) =>
      new Intl.DateTimeFormat(undefined, { timeStyle: 'medium' }).format(new Date(value)),
  },
  {
    title: 'Seat',
    key: 'seat',
    render: (_, entry) => `${entry.sectionName}, row ${entry.rowLabel}, seat ${entry.seatLabel}`,
  },
  {
    title: 'Holder',
    dataIndex: 'buyerEmail',
    key: 'buyerEmail',
    responsive: ['md'],
  },
]
