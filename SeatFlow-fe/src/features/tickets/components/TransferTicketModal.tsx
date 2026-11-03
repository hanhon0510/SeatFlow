import { useMutation, useQueryClient } from '@tanstack/react-query'
import { Alert, Form, Input, Modal, Typography } from 'antd'

import { apiErrorMessage } from '../../../shared/api/apiError'
import { ticketQueryKeys, transferTicket } from '../api/ticketsApi'

type TransferTicketModalProps = {
  ticketId: string
  eventName: string
  open: boolean
  onClose: () => void
  onTransferred: () => void
}

/** Hands the ticket to another SeatFlow account by email. The sender's QR code stops working. */
export function TransferTicketModal({ ticketId, eventName, open, onClose, onTransferred }: TransferTicketModalProps) {
  const [form] = Form.useForm<{ recipientEmail: string }>()
  const queryClient = useQueryClient()
  const transferMutation = useMutation({
    mutationFn: (recipientEmail: string) => transferTicket(ticketId, recipientEmail),
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ticketQueryKeys.all })
      onTransferred()
    },
  })

  return (
    <Modal
      cancelText="Keep ticket"
      confirmLoading={transferMutation.isPending}
      okButtonProps={{ danger: true }}
      okText="Transfer ticket"
      open={open}
      title={`Transfer your ticket for ${eventName}`}
      onCancel={onClose}
      onOk={() => form.submit()}
    >
      <Typography.Paragraph>
        The ticket moves to the account with this email and your QR code stops working. This cannot be undone.
      </Typography.Paragraph>
      {transferMutation.isError ? (
        <Alert
          showIcon
          style={{ marginBottom: 16 }}
          title={apiErrorMessage(transferMutation.error, 'Could not transfer the ticket')}
          type="error"
        />
      ) : null}
      <Form
        form={form}
        layout="vertical"
        onFinish={(values) => transferMutation.mutate(values.recipientEmail.trim())}
      >
        <Form.Item
          label="Recipient email"
          name="recipientEmail"
          rules={[
            { required: true, message: 'Enter the email of the SeatFlow account to receive the ticket' },
            { type: 'email', message: 'Enter a valid email address' },
          ]}
        >
          <Input autoComplete="off" autoFocus type="email" />
        </Form.Item>
      </Form>
    </Modal>
  )
}
