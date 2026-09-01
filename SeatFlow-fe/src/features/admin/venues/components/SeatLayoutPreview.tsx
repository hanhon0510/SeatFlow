import { Alert, Empty, Space, Spin, Tag, Tooltip, Typography } from 'antd'

import type { Seat, SeatLayout } from '../types'

type SeatLayoutPreviewProps = {
  error: string | null
  layout?: SeatLayout
  loading: boolean
  pendingSeatIds?: string[]
  onToggleAccessible?: (seat: Seat) => void
}

export function SeatLayoutPreview({
  error,
  layout,
  loading,
  pendingSeatIds = [],
  onToggleAccessible,
}: SeatLayoutPreviewProps) {
  const sections = layout?.sections ?? []
  const pending = new Set(pendingSeatIds)

  return (
    <section className="admin-section">
      <div className="admin-section-header">
        <h2>Seat layout</h2>
      </div>

      <Typography.Paragraph type="secondary">
        Select a seat to switch it between wheelchair accessible and standard. Accessible seats are
        marked for ticket buyers on the public seat map — the label does not change the price, and
        it never makes a seat unavailable.
      </Typography.Paragraph>

      <Space className="seat-layout-legend" size={16} wrap>
        <span className="seat-legend-item">
          <span aria-hidden className="seat-chip accessible">
            <span className="seat-chip-number">1</span>
            <AccessibleMark />
          </span>
          Wheelchair accessible
        </span>
        <span className="seat-legend-item">
          <span aria-hidden className="seat-chip">
            <span className="seat-chip-number">2</span>
          </span>
          Standard seat
        </span>
      </Space>

      {error ? <Alert className="admin-inline-alert" message={error} showIcon type="error" /> : null}

      <Spin spinning={loading}>
        {sections.length === 0 ? (
          <Empty description="No layout available" />
        ) : (
          <div className="seat-layout-preview">
            {sections.map((section) => (
              <div className="seat-layout-section" key={section.id}>
                <Space className="seat-layout-section-title" size={8} wrap>
                  <Typography.Text strong>{section.name}</Typography.Text>
                  <Tag>{section.seats.length} seats</Tag>
                  <Tag color="green">{accessibleCount(section.seats)} accessible</Tag>
                </Space>

                {section.seats.length === 0 ? (
                  <Empty
                    className="compact-empty"
                    description="No seats"
                    image={Empty.PRESENTED_IMAGE_SIMPLE}
                  />
                ) : (
                  <div className="seat-layout-rows">
                    {seatRows(section.seats).map(([rowLabel, seats]) => (
                      <div className="seat-layout-row" key={rowLabel}>
                        <span className="seat-row-label">{rowLabel}</span>
                        <div className="seat-grid">
                          {seats.map((seat) => (
                            <SeatChip
                              key={seat.id}
                              pending={pending.has(seat.id)}
                              seat={seat}
                              onToggle={onToggleAccessible}
                            />
                          ))}
                        </div>
                      </div>
                    ))}
                  </div>
                )}
              </div>
            ))}
          </div>
        )}
      </Spin>
    </section>
  )
}

function SeatChip({
  pending,
  seat,
  onToggle,
}: {
  pending: boolean
  seat: Seat
  onToggle?: (seat: Seat) => void
}) {
  const state = seat.accessible ? 'Wheelchair accessible' : 'Standard seat'
  const className = [
    'seat-chip',
    seat.accessible ? 'accessible' : '',
    onToggle ? 'interactive' : '',
  ].filter(Boolean).join(' ')

  const face = (
    <>
      <span className="seat-chip-number">{seat.seatNumber}</span>
      {seat.accessible ? <AccessibleMark /> : null}
    </>
  )

  if (!onToggle) {
    return (
      <Tooltip title={`${seat.seatLabel} — ${state}`}>
        <span aria-label={`Seat ${seat.seatLabel}, ${state.toLowerCase()}`} className={className}>
          {face}
        </span>
      </Tooltip>
    )
  }

  const action = seat.accessible ? 'mark it standard' : 'mark it wheelchair accessible'
  return (
    <Tooltip title={`${seat.seatLabel} — ${state}. Select to ${action}.`}>
      <button
        aria-label={`Seat ${seat.seatLabel}, ${state.toLowerCase()}. Select to ${action}.`}
        aria-pressed={seat.accessible}
        className={className}
        disabled={pending}
        type="button"
        onClick={() => onToggle(seat)}
      >
        {face}
      </button>
    </Tooltip>
  )
}

/** Drawn rather than typed: the ♿ character depends on an emoji font the browser may not have. */
function AccessibleMark() {
  return (
    <svg
      aria-hidden
      className="seat-chip-mark"
      fill="none"
      focusable="false"
      stroke="currentColor"
      strokeLinecap="round"
      strokeLinejoin="round"
      strokeWidth={2}
      viewBox="0 0 24 24"
    >
      <circle cx="14" cy="3.8" fill="currentColor" r="2.6" stroke="none" />
      <path d="M14 7.5v6h4.5" />
      <path d="M18.5 13.5 20.5 19.5" />
      <circle cx="11.5" cy="15.5" r="6.5" />
    </svg>
  )
}

function accessibleCount(seats: Seat[]) {
  return seats.filter((seat) => seat.accessible).length
}

function seatRows(seats: Seat[]) {
  const rows = new Map<string, Seat[]>()

  seats.forEach((seat) => {
    const row = rows.get(seat.rowLabel) ?? []
    row.push(seat)
    rows.set(seat.rowLabel, row)
  })

  return Array.from(rows.entries()).map(([rowLabel, rowSeats]) => [
    rowLabel,
    rowSeats.toSorted((first, second) => first.seatNumber - second.seatNumber),
  ] as const)
}
