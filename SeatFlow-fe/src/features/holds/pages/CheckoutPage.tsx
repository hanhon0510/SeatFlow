import { ArrowLeftOutlined } from '@ant-design/icons'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { App as AntdApp, Alert, Button, Card, Result, Space, Spin, Statistic, Steps, Tag, Typography } from 'antd'
import { isAxiosError } from 'axios'
import { useEffect, useMemo, useState } from 'react'
import { useNavigate, useParams } from 'react-router-dom'

import { apiErrorMessage } from '../../../shared/api/apiError'
import { ROUTES } from '../../../shared/constants/routes'
import { publicEventQueryKeys } from '../../events/api/eventsApi'
import { getSeatHold, releaseSeatHold, seatHoldQueryKeys } from '../api/holdsApi'

const { Countdown } = Statistic

export function CheckoutPage() {
  const { holdId } = useParams()
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const { modal, notification } = AntdApp.useApp()
  const [expiredHold, setExpiredHold] = useState<{ holdId: string; expiresAt: string } | null>(null)

  const holdQuery = useQuery({
    queryKey: holdId ? seatHoldQueryKeys.detail(holdId) : seatHoldQueryKeys.detail(''),
    queryFn: () => getSeatHold(holdId ?? ''),
    enabled: Boolean(holdId),
  })

  const hold = holdQuery.data
  const expiresAtMs = useMemo(
    () => (hold ? Date.parse(hold.expiresAt) : Number.NaN),
    [hold],
  )

  useEffect(() => {
    if (!hold || !Number.isFinite(expiresAtMs)) {
      return undefined
    }

    const remainingMs = expiresAtMs - Date.now()
    const timeoutId = window.setTimeout(
      () => setExpiredHold({ holdId: hold.holdId, expiresAt: hold.expiresAt }),
      Math.max(0, remainingMs),
    )
    return () => window.clearTimeout(timeoutId)
  }, [expiresAtMs, hold])

  const releaseMutation = useMutation({
    mutationFn: releaseSeatHold,
    onSuccess: async () => {
      if (hold) {
        queryClient.removeQueries({ queryKey: seatHoldQueryKeys.detail(hold.holdId) })
        await queryClient.invalidateQueries({
          queryKey: publicEventQueryKeys.seatLayout(hold.eventId),
        })
        notification.success({ title: 'Hold released' })
        navigate(ROUTES.eventDetail(hold.eventId), { replace: true })
      }
    },
    onError: (error) => {
      notification.error({
        title: 'Release failed',
        description: apiErrorMessage(error, 'Unable to release hold'),
      })
    },
  })

  if (!holdId) {
    return (
      <section className="page-surface checkout-page">
        <Result
          status="404"
          title="Hold not found"
          subTitle="Open checkout from an active seat hold."
          extra={<Button onClick={() => navigate(ROUTES.events)}>Events</Button>}
        />
      </section>
    )
  }

  if (holdQuery.isLoading) {
    return (
      <section className="page-surface checkout-page catalog-loading">
        <Spin />
      </section>
    )
  }

  if (holdQuery.isError) {
    const notFound = isAxiosError(holdQuery.error) && holdQuery.error.response?.status === 404
    return (
      <section className="page-surface checkout-page">
        {notFound ? (
          <Result
            status="404"
            title="Hold not found"
            subTitle="This hold has expired, was released, or does not belong to your session."
            extra={<Button onClick={() => navigate(ROUTES.events)}>Events</Button>}
          />
        ) : (
          <Alert
            showIcon
            type="error"
            title={apiErrorMessage(holdQuery.error, 'Unable to load hold')}
          />
        )}
      </section>
    )
  }

  if (!hold || !Number.isFinite(expiresAtMs)) {
    return (
      <section className="page-surface checkout-page">
        <Result
          status="404"
          title="Hold not found"
          subTitle="Open checkout from an active seat hold."
          extra={<Button onClick={() => navigate(ROUTES.events)}>Events</Button>}
        />
      </section>
    )
  }

  const expired = expiredHold?.holdId === hold.holdId && expiredHold.expiresAt === hold.expiresAt

  const confirmRelease = () => {
    modal.confirm({
      title: 'Release held seats?',
      content: 'Your selected seats will become available to other users.',
      okText: 'Release hold',
      okButtonProps: { danger: true },
      onOk: () => releaseMutation.mutateAsync(hold.holdId),
    })
  }

  return (
    <section className="page-surface checkout-page">
      <Button
        icon={<ArrowLeftOutlined />}
        type="link"
        onClick={() => navigate(ROUTES.eventDetail(hold.eventId))}
      >
        Seat map
      </Button>

      <Steps
        current={1}
        items={[
          { title: 'Select seats' },
          { title: 'Hold' },
          { title: 'Checkout' },
        ]}
      />

      <Card className="checkout-card">
        <Space className="checkout-card-header" align="start" wrap>
          <div>
            <Typography.Title level={1}>Checkout</Typography.Title>
            <Typography.Text type="secondary">Hold {hold.holdId}</Typography.Text>
          </div>
          <Countdown
            title="Hold expires in"
            value={expiresAtMs}
            onFinish={() => setExpiredHold({ holdId: hold.holdId, expiresAt: hold.expiresAt })}
          />
        </Space>

        {expired ? (
          <Result
            status="warning"
            title="Hold expired"
            subTitle="Checkout is disabled because the server hold has expired."
          />
        ) : (
          <Alert
            showIcon
            type="info"
            title="Seats are held temporarily"
            description="Complete checkout before the countdown reaches zero."
          />
        )}

        <div className="hold-seat-list" aria-label="Held seats">
          {hold.eventSeatIds.map((eventSeatId) => (
            <Tag key={eventSeatId}>{eventSeatId}</Tag>
          ))}
        </div>

        <Space wrap>
          <Button type="primary" disabled={expired}>
            Continue to payment
          </Button>
          <Button danger loading={releaseMutation.isPending} onClick={confirmRelease}>
            Release hold
          </Button>
        </Space>
      </Card>
    </section>
  )
}
