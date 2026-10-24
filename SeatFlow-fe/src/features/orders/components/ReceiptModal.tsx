import { PrinterOutlined } from '@ant-design/icons'
import { Button, Descriptions, Modal, Space, Typography } from 'antd'

import { formatDateTime } from '../../events/utils/eventFormatters'
import { formatDate, formatMoney, orderReference, orderStatusLabel } from '../orderFormat'
import type { OrderHistoryItem } from '../types'

type ReceiptModalProps = {
  order: OrderHistoryItem | null
  onClose: () => void
}

/** A plain receipt for one order. Printing hides the rest of the page and prints only this. */
export function ReceiptModal({ order, onClose }: ReceiptModalProps) {
  if (!order) {
    return null
  }

  const discounted = order.discountAmount > 0
  const subtotal = order.totalAmount + order.discountAmount

  return (
    <Modal
      footer={
        <Space>
          <Button onClick={onClose}>Close</Button>
          <Button icon={<PrinterOutlined />} type="primary" onClick={() => window.print()}>
            Print
          </Button>
        </Space>
      }
      open
      title="Receipt"
      onCancel={onClose}
    >
      <div className="receipt-print">
        <Typography.Title level={4}>SeatFlow receipt</Typography.Title>
        <Descriptions column={1} size="small">
          <Descriptions.Item label="Order">{orderReference(order.orderId)}</Descriptions.Item>
          <Descriptions.Item label="Placed">{formatDate(order.createdAt)}</Descriptions.Item>
          <Descriptions.Item label="Status">{orderStatusLabel[order.status]}</Descriptions.Item>
          <Descriptions.Item label="Event">{order.event.name}</Descriptions.Item>
          <Descriptions.Item label="When">
            {formatDateTime(order.event.startTime, order.event.venueTimezone)}
          </Descriptions.Item>
          <Descriptions.Item label="Where">{order.event.venueName}</Descriptions.Item>
          <Descriptions.Item label={order.seats.length === 1 ? 'Seat' : 'Seats'}>
            {order.seats.map((seat) => `${seat.sectionName}, row ${seat.rowLabel}, seat ${seat.seatLabel}`).join('; ')}
          </Descriptions.Item>
          {discounted ? (
            <>
              <Descriptions.Item label="Subtotal">{formatMoney(subtotal, order.currency)}</Descriptions.Item>
              <Descriptions.Item label={order.promoCode ? `Discount (${order.promoCode})` : 'Discount'}>
                {`-${formatMoney(order.discountAmount, order.currency)}`}
              </Descriptions.Item>
            </>
          ) : null}
          <Descriptions.Item label="Total">
            <Typography.Text strong>{formatMoney(order.totalAmount, order.currency)}</Typography.Text>
          </Descriptions.Item>
          {order.refund ? (
            <Descriptions.Item label="Refunded">
              {`${formatMoney(order.refund.amount, order.currency)} on ${formatDate(order.refund.refundedAt)}`}
            </Descriptions.Item>
          ) : null}
        </Descriptions>
      </div>
    </Modal>
  )
}
