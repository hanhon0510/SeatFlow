import { useMutation, useQueryClient } from '@tanstack/react-query'
import { Alert, Button, Input, Modal, Progress, Space, Typography } from 'antd'
import { useState } from 'react'

import { apiErrorMessage } from '../../../../shared/api/apiError'
import { cancelEventAndRefund, eventQueryKeys } from '../api/eventsApi'
import type { EventCancellation } from '../types'

const REASON_MAX_LENGTH = 200

type CancelEventModalProps = {
  event: { id: string; name: string } | null
  /** `cancel` asks for confirmation first; `resume` only finishes refunds of an event already cancelled. */
  mode: 'cancel' | 'resume'
  paidOrders: number
  pendingOrders: number
  onClose: () => void
}

export function CancelEventModal({ event, mode, paidOrders, pendingOrders, onClose }: CancelEventModalProps) {
  const queryClient = useQueryClient()
  const [reason, setReason] = useState('')
  const [confirmation, setConfirmation] = useState('')
  const [progress, setProgress] = useState<EventCancellation | null>(null)

  const cancelMutation = useMutation({
    mutationFn: (target: { id: string }) =>
      cancelEventAndRefund(target.id, { reason: reason.trim() || undefined }, setProgress),
    onSettled: async () => {
      // The event is cancelled as soon as the first request lands, even if a later one fails.
      await queryClient.invalidateQueries({ queryKey: eventQueryKeys.all })
    },
  })

  const handleClose = () => {
    setReason('')
    setConfirmation('')
    setProgress(null)
    cancelMutation.reset()
    onClose()
  }

  if (!event) {
    return null
  }

  const running = cancelMutation.isPending
  const result = cancelMutation.data
  const confirmed = mode === 'resume' || confirmation.trim() === event.name.trim()
  const unfinished = result !== undefined && result.remainingRefunds > 0
  const finished = result !== undefined && !unfinished
  const started = progress !== null

  return (
    <Modal
      closable={!running}
      footer={
        <Space>
          <Button disabled={running} onClick={handleClose}>
            {finished ? 'Done' : mode === 'resume' ? 'Not now' : 'Keep the event'}
          </Button>
          {finished ? null : (
            <Button
              danger={mode === 'cancel'}
              disabled={!confirmed}
              loading={running}
              type="primary"
              onClick={() => cancelMutation.mutate(event)}
            >
              {unfinished || (cancelMutation.isError && started)
                ? 'Continue refunds'
                : mode === 'resume'
                  ? 'Refund remaining orders'
                  : 'Cancel event and refund'}
            </Button>
          )}
        </Space>
      }
      keyboard={!running}
      mask={{ closable: !running }}
      open
      title={mode === 'resume' ? 'Finish refunds' : 'Cancel event'}
      onCancel={handleClose}
    >
      {running || started || result !== undefined ? (
        <CancellationProgress
          error={cancelMutation.isError ? apiErrorMessage(cancelMutation.error, 'The cancellation was interrupted') : null}
          progress={result ?? progress}
          running={running}
        />
      ) : (
        <>
          <Typography.Paragraph>
            <strong>{event.name}</strong> will close for sale and disappear from the catalogue.
          </Typography.Paragraph>
          <Alert
            showIcon
            style={{ marginBottom: 16 }}
            title="This cannot be undone"
            description={consequences(mode, paidOrders, pendingOrders)}
            type="warning"
          />

          {mode === 'cancel' ? (
            <>
              <label htmlFor="cancel-event-reason">
                <Typography.Text>Reason (optional, shown on every refund)</Typography.Text>
              </label>
              <Input.TextArea
                autoSize={{ minRows: 2, maxRows: 4 }}
                id="cancel-event-reason"
                maxLength={REASON_MAX_LENGTH}
                placeholder="For example: the artist is ill, the venue is unavailable"
                showCount
                value={reason}
                onChange={(input) => setReason(input.target.value)}
              />

              <label htmlFor="cancel-event-confirmation" style={{ display: 'block', marginTop: 16 }}>
                <Typography.Text>
                  Type <strong>{event.name}</strong> to confirm
                </Typography.Text>
              </label>
              <Input
                autoComplete="off"
                id="cancel-event-confirmation"
                value={confirmation}
                onChange={(input) => setConfirmation(input.target.value)}
              />
            </>
          ) : null}

          {cancelMutation.isError ? (
            <Alert
              showIcon
              style={{ marginTop: 16 }}
              title={apiErrorMessage(cancelMutation.error, 'The event could not be cancelled')}
              type="error"
            />
          ) : null}
        </>
      )}
    </Modal>
  )
}

function CancellationProgress({
  progress,
  running,
  error,
}: {
  progress: EventCancellation | null
  running: boolean
  error: string | null
}) {
  const refunded = progress?.refundedOrders ?? 0
  const remaining = progress?.remainingRefunds ?? 0
  const total = refunded + remaining
  const percent = total === 0 ? 100 : Math.round((refunded / total) * 100)
  const done = progress !== null && !running && !error && remaining === 0

  return (
    <div aria-live="polite">
      <Progress
        aria-label="Share of orders refunded"
        percent={percent}
        status={error ? 'exception' : done ? 'success' : 'active'}
      />
      <Typography.Paragraph>
        {progress === null
          ? 'Cancelling the event…'
          : `${refunded} of ${total} paid ${total === 1 ? 'order' : 'orders'} refunded.`}
      </Typography.Paragraph>

      {error ? <Alert showIcon style={{ marginBottom: 12 }} title={error} type="error" /> : null}
      {done ? (
        <Alert
          showIcon
          title={
            progress.ordersNeedingAttention > 0
              ? 'The event is cancelled'
              : 'The event is cancelled and everyone has been refunded'
          }
          type="success"
        />
      ) : null}
      {!running && !error && remaining > 0 ? (
        <Alert
          showIcon
          title={`${remaining} ${remaining === 1 ? 'order' : 'orders'} could not be refunded yet`}
          description="Nothing is lost: continue to try them again."
          type="warning"
        />
      ) : null}
      {progress !== null && progress.ordersNeedingAttention > 0 ? (
        <Alert
          showIcon
          style={{ marginTop: 12 }}
          title={`${progress.ordersNeedingAttention} ${
            progress.ordersNeedingAttention === 1 ? 'order has' : 'orders have'
          } tickets that were already scanned`}
          description="These are not refunded automatically. Handle them individually from the orders list."
          type="warning"
        />
      ) : null}
    </div>
  )
}

function consequences(mode: 'cancel' | 'resume', paidOrders: number, pendingOrders: number) {
  if (mode === 'resume') {
    return `${paidOrders} paid ${paidOrders === 1 ? 'order is' : 'orders are'} still waiting for a refund.`
  }
  const parts = [
    paidOrders === 0
      ? 'No orders have been paid yet.'
      : `${paidOrders} paid ${paidOrders === 1 ? 'order' : 'orders'} will be refunded in full and their tickets stop working.`,
  ]
  if (pendingOrders > 0) {
    parts.push(
      `${pendingOrders} unpaid ${pendingOrders === 1 ? 'checkout' : 'checkouts'} will be closed and their seats released.`,
    )
  }
  return parts.join(' ')
}
