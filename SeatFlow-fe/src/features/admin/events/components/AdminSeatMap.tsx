import { useMemo, useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { Alert, Badge, Empty, Segmented, Space, Spin, Tooltip, Typography } from 'antd'

import { apiErrorMessage } from '../../../../shared/api/apiError'
import type { EventSeatLayoutSeat, EventSeatLayoutStatus } from '../../../events/types'
import { eventQueryKeys, getEventSeatMap } from '../api/eventsApi'
import type { AdminSeatOrder } from '../types'

/**
 * The seat map a buyer sees, read-only and with the buyer's identity attached. Status colours
 * are the buyer map's, deliberately: an admin comparing the two screens should not have to
 * translate. Every seat also carries its label and a spoken status, so nothing rests on colour.
 */
const STATUS_LABEL: Record<EventSeatLayoutStatus, string> = {
  AVAILABLE: 'Available',
  HELD: 'In checkout',
  HELD_BY_YOU: 'In checkout',
  SOLD: 'Sold',
  BLOCKED: 'Blocked',
}

export function AdminSeatMap({ eventId }: { eventId: string }) {
  const [selectedSectionId, setSelectedSectionId] = useState<string>()

  const seatMapQuery = useQuery({
    queryKey: eventQueryKeys.seatMap(eventId),
    queryFn: () => getEventSeatMap(eventId),
    // Holds expire on their own, so a map left open goes stale without a refetch.
    refetchInterval: 30_000,
  })

  const ordersBySeatId = useMemo(() => {
    const orders = new Map<string, AdminSeatOrder>()
    seatMapQuery.data?.orders.forEach((order) => orders.set(order.eventSeatId, order))
    return orders
  }, [seatMapQuery.data])

  if (seatMapQuery.isLoading) {
    return (
      <div className="catalog-loading">
        <Spin />
      </div>
    )
  }

  if (seatMapQuery.isError) {
    return (
      <Alert
        showIcon
        title={apiErrorMessage(seatMapQuery.error, 'Could not load the seat map')}
        type="error"
      />
    )
  }

  const sections = seatMapQuery.data?.sections ?? []

  if (sections.length === 0) {
    return (
      <Empty
        description="No seat inventory yet. Publishing the event creates it."
        image={Empty.PRESENTED_IMAGE_SIMPLE}
      />
    )
  }

  const activeSection =
    sections.find((section) => section.id === selectedSectionId) ?? sections[0]
  const counts = countByStatus(activeSection.rows.flatMap((row) => row.seats))

  return (
    <div className="admin-seat-map">
      <div className="admin-seat-map-toolbar">
        <Segmented
          options={sections.map((section) => ({ label: section.name, value: section.id }))}
          size="small"
          value={activeSection.id}
          onChange={(value) => setSelectedSectionId(value as string)}
        />
        <Space size={12} wrap>
          <Badge color="#ffffff" text={`Available ${counts.AVAILABLE}`} />
          <Badge color="#d97706" text={`In checkout ${counts.HELD}`} />
          <Badge color="#b8c3d1" text={`Sold ${counts.SOLD}`} />
          <Badge color="#4b5563" text={`Blocked ${counts.BLOCKED}`} />
          <Badge color="#16a34a" text="Wheelchair accessible" />
        </Space>
      </div>

      <div aria-label={`${activeSection.name} seat map`} className="admin-seat-map-layout">
        {activeSection.rows.map((row) => (
          <div className="admin-seat-map-row" key={row.rowLabel}>
            <span className="seat-row-label">{row.rowLabel}</span>
            <div className="admin-seat-map-grid">
              {row.seats.map((seat) => (
                <AdminSeat
                  key={seat.eventSeatId}
                  order={ordersBySeatId.get(seat.eventSeatId)}
                  seat={seat}
                />
              ))}
            </div>
          </div>
        ))}
      </div>

      <Typography.Text type="secondary">
        Seats in checkout are held by a buyer right now and clear on their own; this map
        refreshes every 30 seconds.
      </Typography.Text>
    </div>
  )
}

function AdminSeat({ seat, order }: { seat: EventSeatLayoutSeat; order?: AdminSeatOrder }) {
  const description = seatDescription(seat, order)
  const className = [
    'admin-seat',
    `state-${seatStatus(seat).toLowerCase().replace(/_/g, '-')}`,
    seat.accessible ? 'accessible' : '',
  ]
    .filter(Boolean)
    .join(' ')

  return (
    <Tooltip title={description}>
      <span aria-label={description} className={className}>
        {seat.seatLabel}
      </span>
    </Tooltip>
  )
}

/** The layout omits `status` when nothing transient applies, so fall back the way buyers do. */
function seatStatus(seat: EventSeatLayoutSeat): EventSeatLayoutStatus {
  return seat.status ?? seat.permanentStatus
}

function countByStatus(seats: EventSeatLayoutSeat[]) {
  const counts = { AVAILABLE: 0, HELD: 0, SOLD: 0, BLOCKED: 0 }
  seats.forEach((seat) => {
    const status = seatStatus(seat)
    if (status === 'HELD' || status === 'HELD_BY_YOU') {
      counts.HELD += 1
      return
    }
    counts[status] += 1
  })

  return counts
}

function seatDescription(seat: EventSeatLayoutSeat, order?: AdminSeatOrder) {
  const parts = [
    `Seat ${seat.seatLabel}`,
    STATUS_LABEL[seatStatus(seat)],
    formatPrice(seat.price),
  ]

  if (seat.accessible) {
    parts.push('wheelchair accessible')
  }

  if (order) {
    parts.push(`${order.buyerEmail} (order ${order.orderStatus.toLowerCase()})`)
  }

  return parts.join(' - ')
}

function formatPrice(value: number) {
  return new Intl.NumberFormat(undefined, { maximumFractionDigits: 2 }).format(value)
}
