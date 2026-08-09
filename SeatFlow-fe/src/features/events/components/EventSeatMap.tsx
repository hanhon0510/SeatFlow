import { useMemo, useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { Alert, Badge, Button, Card, Empty, Segmented, Spin, Statistic, Tag, Typography } from 'antd'

import { apiErrorMessage } from '../../../shared/api/apiError'
import { getEventSeatLayout, publicEventQueryKeys } from '../api/eventsApi'
import type { EventSeatLayout, EventSeatLayoutSeat, EventSeatLayoutStatus } from '../types'

const maxSelectionCount = 8

type EventSeatMapProps = {
  eventId: string
}

export function EventSeatMap({ eventId }: EventSeatMapProps) {
  const [selectedSectionId, setSelectedSectionId] = useState<string>()
  const [selectedSeatIds, setSelectedSeatIds] = useState<string[]>([])
  const [limitMessageVisible, setLimitMessageVisible] = useState(false)
  const seatLayoutQuery = useQuery({
    queryKey: publicEventQueryKeys.seatLayout(eventId),
    queryFn: () => getEventSeatLayout(eventId),
  })

  const seatsById = useMemo(() => seatsByEventSeatId(seatLayoutQuery.data), [seatLayoutQuery.data])
  const selectedSeats = selectedSeatIds
    .map((seatId) => seatsById.get(seatId))
    .filter((seat): seat is EventSeatLayoutSeat => Boolean(seat))
  const totalPrice = selectedSeats.reduce((total, seat) => total + seat.price, 0)
  const sections = seatLayoutQuery.data?.sections ?? []
  const activeSectionId = sections.some((section) => section.id === selectedSectionId)
    ? selectedSectionId
    : sections[0]?.id
  const activeSection = sections.find((section) => section.id === activeSectionId)

  const toggleSeat = (seat: EventSeatLayoutSeat) => {
    if (seatStatus(seat) !== 'AVAILABLE') {
      return
    }

    setSelectedSeatIds((current) => {
      if (current.includes(seat.eventSeatId)) {
        setLimitMessageVisible(false)
        return current.filter((seatId) => seatId !== seat.eventSeatId)
      }

      if (current.length >= maxSelectionCount) {
        setLimitMessageVisible(true)
        return current
      }

      setLimitMessageVisible(false)
      return [...current, seat.eventSeatId]
    })
  }

  if (seatLayoutQuery.isLoading) {
    return (
      <Card className="seat-map-card">
        <div className="catalog-loading">
          <Spin />
        </div>
      </Card>
    )
  }

  if (seatLayoutQuery.isError) {
    return (
      <Card className="seat-map-card">
        <Alert
          showIcon
          type="error"
          title={apiErrorMessage(seatLayoutQuery.error, 'Unable to load seat map')}
        />
      </Card>
    )
  }

  if (!seatLayoutQuery.data || seatLayoutQuery.data.sections.length === 0) {
    return (
      <Card className="seat-map-card">
        <Empty description="No seats available" />
      </Card>
    )
  }

  return (
    <div className="seat-map-layout">
      <Card className="seat-map-card" title="Select seats">
        <div className="seat-map-tools">
          <Segmented
            aria-label="Section"
            className="seat-section-selector"
            options={sections.map((section) => ({
              label: section.name,
              value: section.id,
            }))}
            value={activeSectionId}
            onChange={(value) => setSelectedSectionId(String(value))}
          />
          <SeatLegend />
        </div>

        {limitMessageVisible ? (
          <Alert
            showIcon
            className="seat-map-alert"
            type="warning"
            title={`You can select up to ${maxSelectionCount} seats.`}
          />
        ) : null}

        {activeSection ? (
          <div className="public-seat-layout" aria-label={`${activeSection.name} seat map`}>
            {activeSection.rows.map((row) => (
              <div className="public-seat-row" key={row.rowLabel}>
                <span className="seat-row-label">{row.rowLabel}</span>
                <div className="public-seat-grid">
                  {row.seats.map((seat) => (
                    <SeatButton
                      key={seat.eventSeatId}
                      seat={seat}
                      selected={selectedSeatIds.includes(seat.eventSeatId)}
                      onToggle={() => toggleSeat(seat)}
                    />
                  ))}
                </div>
              </div>
            ))}
          </div>
        ) : (
          <Empty description="No seats available" />
        )}
      </Card>

      <Card className="selection-summary-card" title="Selection summary">
        <Typography.Text type="secondary">
          {selectedSeats.length} of {maxSelectionCount} selected
        </Typography.Text>
        <div className="selected-seat-tags">
          {selectedSeats.length === 0 ? (
            <Typography.Text>No seats selected</Typography.Text>
          ) : (
            selectedSeats.map((seat) => (
              <Tag key={seat.eventSeatId}>{seat.seatLabel}</Tag>
            ))
          )}
        </div>
        <Statistic title="Total" value={totalPrice} formatter={(value) => formatPrice(Number(value))} />
        <Button block disabled={selectedSeats.length === 0} type="primary">
          Continue
        </Button>
      </Card>
    </div>
  )
}

function SeatButton({
  seat,
  selected,
  onToggle,
}: {
  seat: EventSeatLayoutSeat
  selected: boolean
  onToggle: () => void
}) {
  const selectable = seatStatus(seat) === 'AVAILABLE'
  const state = selected ? 'SELECTED' : seatStatus(seat)
  const className = [
    'public-seat-button',
    `state-${statusClassName(state)}`,
    seat.accessible ? 'accessible' : '',
  ].filter(Boolean).join(' ')

  return (
    <Button
      aria-label={seatAriaLabel(seat, selected)}
      aria-pressed={selected}
      className={className}
      disabled={!selectable}
      onClick={onToggle}
    >
      {seat.seatLabel}
    </Button>
  )
}

function SeatLegend() {
  return (
    <div className="seat-legend" aria-label="Seat legend">
      <Badge color="#ffffff" text="Available" />
      <Badge color="#1f6feb" text="Selected" />
      <Badge color="#d97706" text="Held" />
      <Badge color="#0f766e" text="Held by you" />
      <Badge color="#b8c3d1" text="Sold" />
      <Badge color="#4b5563" text="Blocked" />
      <Badge color="#16a34a" text="Accessible" />
    </div>
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

function seatAriaLabel(seat: EventSeatLayoutSeat, selected: boolean) {
  const state = selected ? 'selected' : statusLabel(seatStatus(seat)).toLowerCase()
  const accessibility = seat.accessible ? 'accessible' : 'standard'
  return `Seat ${seat.seatLabel}, ${state}, ${formatPrice(seat.price)}, ${accessibility}`
}

function seatStatus(seat: EventSeatLayoutSeat) {
  return seat.status ?? seat.permanentStatus
}

function statusClassName(status: EventSeatLayoutStatus | 'SELECTED') {
  return status.toLowerCase().replace(/_/g, '-')
}

function statusLabel(status: EventSeatLayoutStatus) {
  if (status === 'HELD_BY_YOU') {
    return 'Held by you'
  }
  return status.charAt(0) + status.slice(1).toLowerCase()
}

function formatPrice(value: number) {
  return new Intl.NumberFormat(undefined, { maximumFractionDigits: 2 }).format(value)
}
