import { useState } from 'react'
import { Empty, Segmented, Space, Table, Tooltip, Typography } from 'antd'
import type { ColumnsType } from 'antd/es/table'

import type { EventSalesHeatmapRow, EventSalesHeatmapSection } from '../types'

/**
 * Sell-through is a magnitude, so the fill is one hue stepped light to dark - never a rainbow,
 * and never a categorical palette. Steps run 100 -> 700 of the blue ramp; the lightest means
 * "nothing sold yet" and is allowed to recede toward the page. Every step's label ink was
 * contrast-checked against its own fill (worst pair 5.39:1).
 */
const RAMP = [
  { upTo: 0, fill: '#cde2fb', ink: '#172033', label: 'None' },
  { upTo: 24, fill: '#9ec5f4', ink: '#172033', label: '1-24%' },
  { upTo: 49, fill: '#6da7ec', ink: '#172033', label: '25-49%' },
  { upTo: 74, fill: '#256abf', ink: '#ffffff', label: '50-74%' },
  { upTo: 99, fill: '#1c5cab', ink: '#ffffff', label: '75-99%' },
  { upTo: 100, fill: '#0d366b', ink: '#ffffff', label: 'Sold out' },
] as const

type HeatmapProps = {
  sections: EventSalesHeatmapSection[]
}

export function SeatSalesHeatmap({ sections }: HeatmapProps) {
  const [view, setView] = useState<'map' | 'table'>('map')

  if (sections.length === 0) {
    return (
      <Empty
        description="No seat inventory yet. Publishing the event creates it."
        image={Empty.PRESENTED_IMAGE_SIMPLE}
      />
    )
  }

  return (
    <div className="seat-heatmap">
      <div className="seat-heatmap-toolbar">
        <HeatmapLegend />
        <Segmented
          options={[
            { label: 'Heatmap', value: 'map' },
            { label: 'Table', value: 'table' },
          ]}
          size="small"
          value={view}
          onChange={(value) => setView(value as 'map' | 'table')}
        />
      </div>

      {view === 'table' ? (
        <Table<HeatmapTableRow>
          columns={tableColumns}
          dataSource={tableRows(sections)}
          pagination={false}
          rowKey="key"
          size="small"
        />
      ) : (
        sections.map((section) => (
          <section className="seat-heatmap-section" key={section.sectionId}>
            <div className="seat-heatmap-section-header">
              <Typography.Text strong>{section.name}</Typography.Text>
              <Typography.Text type="secondary">{sectionSummary(section)}</Typography.Text>
            </div>
            <ul className="seat-heatmap-grid">
              {section.rows.map((row) => (
                <HeatmapCell key={row.rowLabel} row={row} sectionName={section.name} />
              ))}
            </ul>
          </section>
        ))
      )}
    </div>
  )
}

function HeatmapCell({ row, sectionName }: { row: EventSalesHeatmapRow; sectionName: string }) {
  const percent = sellThrough(row)
  const step = rampStep(percent)
  // backgroundColor rather than the background shorthand: the shorthand would reset the
  // blocked hatch that the stylesheet paints as a background-image.
  const cellStyle = { backgroundColor: step.fill, color: step.ink }

  return (
    <li>
      <Tooltip title={cellDescription(row, sectionName, percent)}>
        <span
          aria-label={cellDescription(row, sectionName, percent)}
          className={row.seatsBlocked > 0 ? 'seat-heatmap-cell is-blocked' : 'seat-heatmap-cell'}
          style={cellStyle}
        >
          {row.rowLabel}
        </span>
      </Tooltip>
    </li>
  )
}

function HeatmapLegend() {
  return (
    <Space align="center" size={12} wrap>
      <Typography.Text type="secondary">Sold</Typography.Text>
      <ul className="seat-heatmap-legend">
        {RAMP.map((step) => (
          <li key={step.label}>
            <span className="seat-heatmap-swatch" style={{ background: step.fill }} />
            <Typography.Text type="secondary">{step.label}</Typography.Text>
          </li>
        ))}
        <li>
          <span className="seat-heatmap-swatch is-blocked" />
          <Typography.Text type="secondary">Has blocked seats</Typography.Text>
        </li>
      </ul>
    </Space>
  )
}

type HeatmapTableRow = HeatmapRowWithSection & { key: string }
type HeatmapRowWithSection = EventSalesHeatmapRow & { sectionName: string }

const tableColumns: ColumnsType<HeatmapTableRow> = [
  { title: 'Section', dataIndex: 'sectionName', key: 'sectionName' },
  { title: 'Row', dataIndex: 'rowLabel', key: 'rowLabel' },
  { title: 'Seats', dataIndex: 'seatsTotal', key: 'seatsTotal', align: 'right' },
  { title: 'Sold', dataIndex: 'seatsSold', key: 'seatsSold', align: 'right' },
  { title: 'Available', dataIndex: 'seatsAvailable', key: 'seatsAvailable', align: 'right' },
  { title: 'Blocked', dataIndex: 'seatsBlocked', key: 'seatsBlocked', align: 'right' },
  {
    title: 'Sold %',
    key: 'sellThrough',
    align: 'right',
    render: (_, row) => `${sellThrough(row)}%`,
  },
]

function tableRows(sections: EventSalesHeatmapSection[]): HeatmapTableRow[] {
  return sections.flatMap((section) =>
    section.rows.map((row) => ({
      ...row,
      sectionName: section.name,
      key: `${section.sectionId}-${row.rowLabel}`,
    })),
  )
}

function sectionSummary(section: EventSalesHeatmapSection) {
  const total = section.rows.reduce((sum, row) => sum + row.seatsTotal, 0)
  const sold = section.rows.reduce((sum, row) => sum + row.seatsSold, 0)

  return `${sold} of ${total} seats sold`
}

function sellThrough(row: EventSalesHeatmapRow) {
  if (row.seatsTotal === 0) {
    return 0
  }

  return Math.round((row.seatsSold / row.seatsTotal) * 100)
}

function rampStep(percent: number) {
  return RAMP.find((step) => percent <= step.upTo) ?? RAMP[RAMP.length - 1]
}

function cellDescription(row: EventSalesHeatmapRow, sectionName: string, percent: number) {
  const blocked = row.seatsBlocked > 0 ? `, ${row.seatsBlocked} blocked` : ''

  return `${sectionName} row ${row.rowLabel}: ${row.seatsSold} of ${row.seatsTotal} sold (${percent}%), ${row.seatsAvailable} available${blocked}`
}
