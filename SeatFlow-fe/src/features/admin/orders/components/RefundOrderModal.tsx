import { useMutation, useQueryClient } from '@tanstack/react-query'
import { Alert, App as AntdApp, Descriptions, Input, Modal, Typography } from 'antd'
import { useState } from 'react'

import { apiErrorMessage } from '../../../../shared/api/apiError'
import { eventQueryKeys } from '../../events/api/eventsApi'
import { refundOrder } from '../api/ordersApi'
import type { RefundableOrder } from '../types'

const REASON_MAX_LENGTH = 255

type RefundOrderModalProps = {
  order: RefundableOrder | null
  onClose: () => void
}

export function RefundOrderModal({ order, onClose }: RefundOrderModalProps) {
  const queryClient = useQueryClient()
  const { notification } = AntdApp.useApp()
  const [reason, setReason] = useState('')

  const refundMutation = useMutation({
    mutationFn: (target: RefundableOrder) => refundOrder(target.orderId, { reason: reason.trim() || undefined }),
    onSuccess: async (refund, target) => {
      notification.success({
        title: 'Order refunded',
        description: `${formatMoney(refund.amount, refund.currency)} returned to ${target.buyerEmail}. The seats are back on sale.`,
      })
      await queryClient.invalidateQueries({ queryKey: eventQueryKeys.all })
      handleClose()
    },
  })

  const handleClose = () => {
    setReason('')
    refundMutation.reset()
    onClose()
  }

  if (!order) {
    return null
  }

  const amount = formatMoney(order.totalAmount, order.currency)
  const seats = `${order.seatCount} ${order.seatCount === 1 ? 'seat' : 'seats'}`

  return (
    <Modal
      cancelButtonProps={{ disabled: refundMutation.isPending }}
      cancelText="Keep order"
      closable={!refundMutation.isPending}
      confirmLoading={refundMutation.isPending}
      keyboard={!refundMutation.isPending}
      mask={{ closable: !refundMutation.isPending }}
      okButtonProps={{ danger: true }}
      okText={`Refund ${amount}`}
      open
      title="Refund order"
      onCancel={handleClose}
      onOk={() => refundMutation.mutate(order)}
    >
      <Descriptions column={1} size="small">
        <Descriptions.Item label="Buyer">{order.buyerEmail}</Descriptions.Item>
        <Descriptions.Item label="Seats">{seats}</Descriptions.Item>
        <Descriptions.Item label="Refund amount">
          <Typography.Text strong>{amount}</Typography.Text>
        </Descriptions.Item>
      </Descriptions>

      <Alert
        showIcon
        style={{ marginBottom: 16 }}
        title="This cannot be undone"
        description="The payment is reversed in full, the buyer's tickets stop working, and the seats go back on sale straight away."
        type="warning"
      />

      <label htmlFor="refund-reason">
        <Typography.Text>Reason (optional)</Typography.Text>
      </label>
      <Input.TextArea
        autoSize={{ minRows: 2, maxRows: 4 }}
        disabled={refundMutation.isPending}
        id="refund-reason"
        maxLength={REASON_MAX_LENGTH}
        placeholder="For example: duplicate purchase, event changed"
        showCount
        value={reason}
        onChange={(event) => setReason(event.target.value)}
      />

      {refundMutation.isError ? (
        <Alert
          showIcon
          style={{ marginTop: 16 }}
          title={apiErrorMessage(refundMutation.error, 'The refund could not be completed')}
          type="error"
        />
      ) : null}
    </Modal>
  )
}

function formatMoney(value: number, currency: string) {
  return `${new Intl.NumberFormat(undefined, { maximumFractionDigits: 2 }).format(value)} ${currency}`
}
