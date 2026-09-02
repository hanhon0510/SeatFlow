import { Empty, Typography } from 'antd'

import type { EventSalesDailyPoint } from '../types'

/**
 * A bar per day of paid demand. Deliberately hand-drawn rather than pulled from a chart
 * library: the shape of a thirty-row series is all an admin needs to read here.
 */
export function SalesTrendChart({ points }: { points: EventSalesDailyPoint[] }) {
  if (points.length === 0) {
    return (
      <Empty description="No paid orders in the last 30 days" image={Empty.PRESENTED_IMAGE_SIMPLE} />
    )
  }

  const peak = points.reduce((highest, point) => Math.max(highest, point.seatsSold), 0)

  return (
    <ol className="sales-trend">
      {points.map((point) => (
        <li className="sales-trend-row" key={point.date}>
          <Typography.Text className="sales-trend-date" type="secondary">
            {formatDay(point.date)}
          </Typography.Text>
          <span className="sales-trend-track">
            <span
              className="sales-trend-bar"
              style={{ width: `${peak === 0 ? 0 : (point.seatsSold / peak) * 100}%` }}
            />
          </span>
          <Typography.Text className="sales-trend-value">
            {point.seatsSold} seats, {point.paidOrders} orders
          </Typography.Text>
        </li>
      ))}
    </ol>
  )
}

function formatDay(value: string) {
  return new Intl.DateTimeFormat(undefined, {
    day: 'numeric',
    month: 'short',
    timeZone: 'UTC',
  }).format(new Date(`${value}T00:00:00Z`))
}
