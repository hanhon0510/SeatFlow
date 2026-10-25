import { useEffect, useMemo, useRef, useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Alert, Button, Card, InputNumber, Space, Typography } from 'antd'
import dayjs from 'dayjs'
import { useLocation, useNavigate } from 'react-router-dom'

import { apiErrorMessage } from '../../../shared/api/apiError'
import { ROUTES } from '../../../shared/constants/routes'
import { useAuth } from '../../auth/context/useAuth'
import { getEventSeatLayout, publicEventQueryKeys } from '../../events/api/eventsApi'
import type { EventSeatLayout } from '../../events/types'
import { getWaitlistStatus, joinWaitlist, leaveWaitlist, waitlistQueryKeys } from '../api/waitlistApi'
import type { WaitlistStatus } from '../types'

const MAX_SEATS = 8
/** How often a buyer who is waiting or has an offer is checked on. */
const POLL_INTERVAL_MS = 15_000

type WaitlistCardProps = {
  eventId: string
  /** The catalogue says the event is sold out; used to invite signed-out visitors to sign in. */
  soldOut: boolean
}

/**
 * The buyer's side of the waitlist: join a sold-out event, see the place in line, and use the
 * seats that are kept for them once some free up. Renders nothing when there is nothing to say.
 */
export function WaitlistCard({ eventId, soldOut }: WaitlistCardProps) {
  const { isAuthenticated, user } = useAuth()
  const isCustomer = isAuthenticated && user?.role === 'USER'

  if (!isAuthenticated) {
    return soldOut ? <SignInInvitation /> : null
  }
  return isCustomer ? <SignedInWaitlist eventId={eventId} /> : null
}

function SignInInvitation() {
  const navigate = useNavigate()
  const location = useLocation()
  return (
    <Card className="seat-map-card waitlist-card" title="Waitlist">
      <Space align="center" wrap>
        <Typography.Text>This event is sold out. Sign in to join the waitlist.</Typography.Text>
        <Button type="primary" onClick={() => navigate(ROUTES.login, { state: { from: location } })}>
          Sign in
        </Button>
      </Space>
    </Card>
  )
}

function SignedInWaitlist({ eventId }: { eventId: string }) {
  const queryClient = useQueryClient()
  const statusQuery = useQuery({
    queryKey: waitlistQueryKeys.status(eventId),
    queryFn: () => getWaitlistStatus(eventId),
    refetchInterval: (query) => (query.state.data && query.state.data.state !== 'NONE' ? POLL_INTERVAL_MS : false),
  })
  // The same query as the seat map, so this costs no extra request.
  const layoutQuery = useQuery({
    queryKey: publicEventQueryKeys.seatLayout(eventId),
    queryFn: () => getEventSeatLayout(eventId),
  })
  const status = statusQuery.data
  const offerKey = status?.state === 'OFFERED' ? status.entryId : null

  useEffect(() => {
    // The seats kept for the buyer show as held to everyone else, so a map loaded before the
    // offer arrived has to be read again to show them as free to this buyer.
    if (offerKey) {
      void queryClient.invalidateQueries({ queryKey: publicEventQueryKeys.seatLayout(eventId) })
    }
  }, [offerKey, eventId, queryClient])

  const refreshAfterChange = (next: WaitlistStatus | null) => {
    if (next) {
      queryClient.setQueryData(waitlistQueryKeys.status(eventId), next)
    } else {
      void queryClient.invalidateQueries({ queryKey: waitlistQueryKeys.status(eventId) })
    }
    void queryClient.invalidateQueries({ queryKey: publicEventQueryKeys.seatLayout(eventId) })
  }

  if (!status) {
    return null
  }
  if (status.state === 'OFFERED') {
    return (
      <OfferedCard
        eventId={eventId}
        layout={layoutQuery.data}
        status={status}
        onChanged={refreshAfterChange}
        onLapsed={() => void statusQuery.refetch()}
      />
    )
  }
  if (status.state === 'WAITING') {
    return <WaitingCard eventId={eventId} status={status} onChanged={refreshAfterChange} />
  }
  return status.joinable ? <JoinCard eventId={eventId} onChanged={refreshAfterChange} /> : null
}

type ChangeHandler = (next: WaitlistStatus | null) => void

function JoinCard({ eventId, onChanged }: { eventId: string; onChanged: ChangeHandler }) {
  const [seatCount, setSeatCount] = useState<number | null>(2)
  const joinMutation = useMutation({
    mutationFn: (count: number) => joinWaitlist(eventId, count),
    onSuccess: (joined) => onChanged(joined),
    // The event may have gained seats or the buyer may have joined from another tab.
    onError: () => onChanged(null),
  })
  const valid = seatCount !== null && Number.isInteger(seatCount) && seatCount >= 1 && seatCount <= MAX_SEATS

  return (
    <Card className="seat-map-card waitlist-card" title="Join the waitlist">
      <Space orientation="vertical" size="middle" style={{ width: '100%' }}>
        <Typography.Text>
          There are no seats left right now. Seats come back when someone does not finish checkout or an order is
          refunded. Join the waitlist and we will email you, and keep the seats for you for a short while.
        </Typography.Text>
        <Space align="center" wrap>
          <label htmlFor="waitlist-seat-count">
            <Typography.Text>Seats you need</Typography.Text>
          </label>
          <InputNumber
            aria-label="Seats you need"
            disabled={joinMutation.isPending}
            id="waitlist-seat-count"
            max={MAX_SEATS}
            min={1}
            precision={0}
            status={valid ? undefined : 'error'}
            value={seatCount}
            onChange={setSeatCount}
          />
          <Button
            disabled={!valid}
            loading={joinMutation.isPending}
            type="primary"
            onClick={() => seatCount !== null && joinMutation.mutate(seatCount)}
          >
            Join waitlist
          </Button>
        </Space>
        {joinMutation.isError ? (
          <Alert showIcon title={apiErrorMessage(joinMutation.error, 'Could not join the waitlist')} type="error" />
        ) : null}
      </Space>
    </Card>
  )
}

function WaitingCard({
  eventId,
  status,
  onChanged,
}: {
  eventId: string
  status: WaitlistStatus
  onChanged: ChangeHandler
}) {
  const leaveMutation = useLeave(eventId, onChanged)
  const seats = status.seatCount ?? 1
  return (
    <Card className="seat-map-card waitlist-card" title="You are on the waitlist">
      <Space orientation="vertical" size="middle" style={{ width: '100%' }}>
        <Typography.Text>
          You are number <strong>{status.position}</strong> of {status.queueLength} waiting for{' '}
          <strong>{seats === 1 ? '1 seat' : `${seats} seats`}</strong>. We will email you as soon as seats are
          available, so you can leave this page.
        </Typography.Text>
        {leaveMutation.isError ? (
          <Alert showIcon title={apiErrorMessage(leaveMutation.error, 'Could not leave the waitlist')} type="error" />
        ) : null}
        <Button danger loading={leaveMutation.isPending} onClick={() => leaveMutation.mutate()}>
          Leave waitlist
        </Button>
      </Space>
    </Card>
  )
}

function OfferedCard({
  eventId,
  status,
  layout,
  onChanged,
  onLapsed,
}: {
  eventId: string
  status: WaitlistStatus
  layout: EventSeatLayout | undefined
  onChanged: ChangeHandler
  onLapsed: () => void
}) {
  const leaveMutation = useLeave(eventId, onChanged)
  const remaining = useSecondsUntil(status.offerExpiresAt, onLapsed)
  const labels = useMemo(() => seatLabels(layout, status.eventSeatIds), [layout, status.eventSeatIds])
  const count = status.eventSeatIds.length

  return (
    <Card className="seat-map-card waitlist-card" title="Seats are waiting for you">
      <Space orientation="vertical" size="middle" style={{ width: '100%' }}>
        <Alert
          showIcon
          type="success"
          title={`We are keeping ${count === 1 ? 'a seat' : `${count} seats`} for you for ${formatRemaining(remaining)}.`}
          description={
            <>
              {labels.length > 0 ? <>Reserved for you: {labels.join(', ')}. </> : null}
              Select {count === 1 ? 'it' : 'them'} on the seat map below and continue to checkout before the
              time runs out. After that the seats go to the next person in line.
            </>
          }
        />
        {leaveMutation.isError ? (
          <Alert showIcon title={apiErrorMessage(leaveMutation.error, 'Could not give up the seats')} type="error" />
        ) : null}
        <Button loading={leaveMutation.isPending} onClick={() => leaveMutation.mutate()}>
          Give up these seats
        </Button>
      </Space>
    </Card>
  )
}

function useLeave(eventId: string, onChanged: ChangeHandler) {
  return useMutation({
    mutationFn: () => leaveWaitlist(eventId),
    onSuccess: () => onChanged(null),
  })
}

/** Whole seconds left until the deadline, ticking once a second; calls onLapsed once when it passes. */
function useSecondsUntil(deadline: string | null, onLapsed: () => void) {
  const [now, setNow] = useState(() => Date.now())
  useEffect(() => {
    const timer = window.setInterval(() => setNow(Date.now()), 1000)
    return () => window.clearInterval(timer)
  }, [])
  const remaining = deadline ? Math.max(0, Math.ceil((dayjs(deadline).valueOf() - now) / 1000)) : 0
  const lapsed = deadline !== null && remaining === 0
  const onLapsedRef = useRef(onLapsed)
  useEffect(() => {
    onLapsedRef.current = onLapsed
  }, [onLapsed])
  useEffect(() => {
    if (lapsed) {
      onLapsedRef.current()
    }
  }, [lapsed])
  return remaining
}

function formatRemaining(seconds: number) {
  const minutes = Math.floor(seconds / 60)
  const rest = seconds % 60
  return `${minutes}:${String(rest).padStart(2, '0')}`
}

/** "Orchestra A3" for each offered seat, in the order the server listed them. */
function seatLabels(layout: EventSeatLayout | undefined, eventSeatIds: string[]) {
  if (!layout) {
    return []
  }
  const byId = new Map<string, string>()
  for (const section of layout.sections) {
    for (const row of section.rows) {
      for (const seat of row.seats) {
        byId.set(seat.eventSeatId, `${section.name} ${seat.seatLabel}`)
      }
    }
  }
  return eventSeatIds.map((id) => byId.get(id)).filter((label): label is string => Boolean(label))
}
