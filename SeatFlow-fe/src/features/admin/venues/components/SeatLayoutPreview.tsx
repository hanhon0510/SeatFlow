import { Alert, Empty, Space, Spin, Tag, Tooltip, Typography } from 'antd'

import type { Seat, SeatLayout } from '../types'

type SeatLayoutPreviewProps = {
  error: string | null
  layout?: SeatLayout
  loading: boolean
}

export function SeatLayoutPreview({ error, layout, loading }: SeatLayoutPreviewProps) {
  const sections = layout?.sections ?? []

  return (
    <section className="admin-section">
      <div className="admin-section-header">
        <h2>Seat layout</h2>
      </div>

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
                            <Tooltip
                              key={seat.id}
                              title={`${seat.seatLabel}${seat.accessible ? ' accessible' : ''}`}
                            >
                              <span
                                aria-label={`Seat ${seat.seatLabel}`}
                                className={seat.accessible ? 'seat-chip accessible' : 'seat-chip'}
                              >
                                {seat.seatNumber}
                              </span>
                            </Tooltip>
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
