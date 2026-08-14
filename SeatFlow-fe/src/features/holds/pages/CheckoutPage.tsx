import { ArrowLeftOutlined } from '@ant-design/icons'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import {
  App as AntdApp,
  Alert,
  Button,
  Card,
  Descriptions,
  Radio,
  Result,
  Space,
  Spin,
  Statistic,
  Steps,
  Tag,
  Typography,
} from 'antd'
import { isAxiosError } from 'axios'
import { useEffect, useMemo, useRef, useState } from 'react'
import { useNavigate, useParams } from 'react-router-dom'

import { createOrder, createPayment, createReservation } from '../../checkout/api/checkoutApi'
import type { CheckoutPaymentResult, PaymentToken } from '../../checkout/types'
import {
  getCheckoutDraft,
  getPaymentResult,
  saveCheckoutDraft,
  savePaymentResult,
} from '../../checkout/utils/checkoutStorage'
import { apiErrorMessage } from '../../../shared/api/apiError'
import { ROUTES } from '../../../shared/constants/routes'
import { getEventSeatLayout, getPublicEvent, publicEventQueryKeys } from '../../events/api/eventsApi'
import type { EventSeatLayout, EventSeatLayoutSeat } from '../../events/types'
import { formatDateTime } from '../../events/utils/eventFormatters'
import { ticketQueryKeys } from '../../tickets/api/ticketsApi'
import { getSeatHold, releaseSeatHold, seatHoldQueryKeys } from '../api/holdsApi'

const { Countdown } = Statistic

const paymentOptions: { label: string; help: string; token: PaymentToken }[] = [
  {
    label: 'Approve payment',
    help: 'Simulates a successful provider authorization.',
    token: 'tok_success',
  },
  {
    label: 'Decline card',
    help: 'Simulates a provider decline. Seats are not sold.',
    token: 'tok_declined',
  },
  {
    label: 'Provider timeout',
    help: 'Simulates a timed-out payment attempt.',
    token: 'tok_timeout',
  },
]

export function CheckoutPage() {
  const { holdId } = useParams()
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const { modal, notification } = AntdApp.useApp()
  const submittingRef = useRef(false)
  const [selectedToken, setSelectedToken] = useState<PaymentToken>('tok_success')
  const [paymentError, setPaymentError] = useState<string | null>(null)
  const [expiredHold, setExpiredHold] = useState<{ holdId: string; expiresAt: string } | null>(null)

  const holdQuery = useQuery({
    queryKey: holdId ? seatHoldQueryKeys.detail(holdId) : seatHoldQueryKeys.detail(''),
    queryFn: () => getSeatHold(holdId ?? ''),
    enabled: Boolean(holdId),
  })

  const hold = holdQuery.data
  const eventQuery = useQuery({
    queryKey: hold ? publicEventQueryKeys.detail(hold.eventId) : publicEventQueryKeys.detail(''),
    queryFn: () => getPublicEvent(hold?.eventId ?? ''),
    enabled: Boolean(hold),
  })
  const seatLayoutQuery = useQuery({
    queryKey: hold ? publicEventQueryKeys.seatLayout(hold.eventId) : publicEventQueryKeys.seatLayout(''),
    queryFn: () => getEventSeatLayout(hold?.eventId ?? ''),
    enabled: Boolean(hold),
  })

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

  const selectedSeats = useMemo(() => {
    const seats = seatsByEventSeatId(seatLayoutQuery.data)
    return hold?.eventSeatIds.map((eventSeatId) => seats.get(eventSeatId)).filter(isSeat) ?? []
  }, [hold?.eventSeatIds, seatLayoutQuery.data])
  const totalPrice = selectedSeats.reduce((total, seat) => total + seat.price, 0)
  const restoredResult = hold ? getPaymentResult(hold.holdId) : null

  const paymentMutation = useMutation({
    mutationFn: async (token: PaymentToken): Promise<CheckoutPaymentResult> => {
      if (!hold) {
        throw new Error('Hold is not loaded')
      }
      const draft = getCheckoutDraft(hold.holdId)
      const reservation = await createReservation(hold.holdId)
      saveCheckoutDraft({ ...draft, reservationId: reservation.id })
      const order = await createOrder(reservation.id)
      saveCheckoutDraft({ ...draft, reservationId: reservation.id, orderId: order.id })
      const payment = await createPayment(order.id, token, draft.idempotencyKey)

      return {
        holdId: hold.holdId,
        reservation,
        order,
        payment,
        completedAt: new Date().toISOString(),
      }
    },
    onSuccess: async (result) => {
      savePaymentResult(result)
      await queryClient.invalidateQueries({ queryKey: ticketQueryKeys.list() })
      notification.success({
        title: paymentNotificationTitle(result.payment.status),
      })
      navigate(ROUTES.paymentResult(result.holdId), { replace: true })
    },
    onError: (error) => {
      const message = apiErrorMessage(error, 'Payment could not be completed')
      setPaymentError(message)
      notification.error({
        title: 'Payment failed',
        description: message,
      })
    },
    onSettled: () => {
      submittingRef.current = false
    },
  })

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
    return <MissingHoldResult onEvents={() => navigate(ROUTES.events)} />
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
            message={apiErrorMessage(holdQuery.error, 'Unable to load hold')}
          />
        )}
      </section>
    )
  }

  if (!hold || !Number.isFinite(expiresAtMs)) {
    return <MissingHoldResult onEvents={() => navigate(ROUTES.events)} />
  }

  const expired = expiredHold?.holdId === hold.holdId && expiredHold.expiresAt === hold.expiresAt
  const event = eventQuery.data
  const loadingSummary = eventQuery.isLoading || seatLayoutQuery.isLoading
  const paymentDisabled = expired || paymentMutation.isPending || releaseMutation.isPending

  const confirmRelease = () => {
    modal.confirm({
      title: 'Release held seats?',
      content: 'Your selected seats will become available to other users.',
      okText: 'Release hold',
      okButtonProps: { danger: true },
      onOk: () => releaseMutation.mutateAsync(hold.holdId),
    })
  }

  const startPayment = () => {
    if (submittingRef.current || paymentDisabled) {
      return
    }
    if (Date.parse(hold.expiresAt) <= Date.now()) {
      setExpiredHold({ holdId: hold.holdId, expiresAt: hold.expiresAt })
      return
    }
    if (restoredResult) {
      navigate(ROUTES.paymentResult(hold.holdId), { replace: true })
      return
    }

    submittingRef.current = true
    setPaymentError(null)
    paymentMutation.mutate(selectedToken)
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
          { title: 'Checkout' },
          { title: 'Result' },
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
            subTitle="Payment is disabled because the server hold has expired."
          />
        ) : (
          <Alert
            showIcon
            type="info"
            message="Seats are held temporarily"
            description="Complete payment before the countdown reaches zero."
          />
        )}

        {restoredResult ? (
          <Alert
            showIcon
            type="success"
            message="Payment result restored"
            description="This checkout already has a saved payment result. Opening it will not repeat the purchase."
            action={(
              <Button size="small" onClick={() => navigate(ROUTES.paymentResult(hold.holdId))}>
                View result
              </Button>
            )}
          />
        ) : null}

        <Card className="checkout-section-card" title="Reservation summary">
          {loadingSummary ? (
            <Spin />
          ) : (
            <Descriptions bordered column={1}>
              <Descriptions.Item label="Event">
                {event?.name ?? hold.eventId}
              </Descriptions.Item>
              {event ? (
                <>
                  <Descriptions.Item label="Venue">
                    {event.venueName}
                  </Descriptions.Item>
                  <Descriptions.Item label="Time">
                    {formatDateTime(event.startTime, event.venueTimezone)}
                  </Descriptions.Item>
                </>
              ) : null}
              <Descriptions.Item label="Seats">
                <div className="hold-seat-list" aria-label="Held seats">
                  {hold.eventSeatIds.map((eventSeatId) => {
                    const seat = selectedSeats.find((selectedSeat) => selectedSeat.eventSeatId === eventSeatId)
                    return <Tag key={eventSeatId}>{seat?.seatLabel ?? eventSeatId}</Tag>
                  })}
                </div>
              </Descriptions.Item>
              <Descriptions.Item label="Total">
                {selectedSeats.length > 0 ? formatPrice(totalPrice) : 'Pending'}
              </Descriptions.Item>
            </Descriptions>
          )}
        </Card>

        <Card className="checkout-section-card" title="Payment simulator">
          <Radio.Group
            className="payment-simulator-options"
            disabled={paymentDisabled || Boolean(restoredResult)}
            value={selectedToken}
            onChange={(event) => setSelectedToken(event.target.value as PaymentToken)}
          >
            {paymentOptions.map((option) => (
              <Radio key={option.token} value={option.token}>
                <Space direction="vertical" size={0}>
                  <Typography.Text strong>{option.label}</Typography.Text>
                  <Typography.Text type="secondary">{option.help}</Typography.Text>
                </Space>
              </Radio>
            ))}
          </Radio.Group>

          {paymentError ? (
            <Alert showIcon type="error" message={paymentError} />
          ) : null}
        </Card>

        <Space wrap>
          <Button
            type="primary"
            disabled={paymentDisabled}
            loading={paymentMutation.isPending}
            onClick={startPayment}
          >
            Pay now
          </Button>
          <Button danger loading={releaseMutation.isPending} onClick={confirmRelease}>
            Release hold
          </Button>
        </Space>
      </Card>
    </section>
  )
}

function MissingHoldResult({ onEvents }: { onEvents: () => void }) {
  return (
    <section className="page-surface checkout-page">
      <Result
        status="404"
        title="Hold not found"
        subTitle="Open checkout from an active seat hold."
        extra={<Button onClick={onEvents}>Events</Button>}
      />
    </section>
  )
}

function seatsByEventSeatId(layout: EventSeatLayout | undefined) {
  const seats = new Map<string, EventSeatLayoutSeat>()
  layout?.sections.forEach((section) => {
    section.rows.forEach((row) => {
      row.seats.forEach((seat) => seats.set(seat.eventSeatId, seat))
    })
  })
  return seats
}

function isSeat(seat: EventSeatLayoutSeat | undefined): seat is EventSeatLayoutSeat {
  return Boolean(seat)
}

function paymentNotificationTitle(status: string) {
  if (status === 'SUCCEEDED') {
    return 'Payment succeeded'
  }
  if (status === 'DECLINED') {
    return 'Payment declined'
  }
  if (status === 'TIMED_OUT') {
    return 'Payment timed out'
  }
  return 'Payment finished'
}

function formatPrice(value: number) {
  return new Intl.NumberFormat(undefined, {
    maximumFractionDigits: 2,
    style: 'currency',
    currency: 'VND',
  }).format(value)
}
