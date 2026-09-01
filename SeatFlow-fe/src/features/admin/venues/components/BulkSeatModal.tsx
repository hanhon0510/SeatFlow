import { useEffect } from 'react'
import { Alert, Form, Input, InputNumber, Modal, Switch } from 'antd'

import type {
  BulkSeatFormValues,
  SeatCreateRequest,
  SeatLayoutSection,
} from '../types'

type BulkSeatModalProps = {
  error: string | null
  open: boolean
  section: SeatLayoutSection | null
  submitting: boolean
  onCancel: () => void
  onSubmit: (sectionId: string, seats: SeatCreateRequest[]) => void
}

const defaultValues: BulkSeatFormValues = {
  rowLabel: '',
  startNumber: 1,
  quantity: 10,
  accessible: true,
}

export function BulkSeatModal({
  error,
  open,
  section,
  submitting,
  onCancel,
  onSubmit,
}: BulkSeatModalProps) {
  const [form] = Form.useForm<BulkSeatFormValues>()

  useEffect(() => {
    if (open) {
      form.setFieldsValue(defaultValues)
    }
  }, [form, open])

  const handleFinish = (values: BulkSeatFormValues) => {
    if (!section) {
      return
    }

    const rowLabel = values.rowLabel.trim()
    const seats = Array.from({ length: values.quantity }, (_, index) => {
      const seatNumber = values.startNumber + index
      return {
        rowLabel,
        seatNumber,
        seatLabel: `${rowLabel}${seatNumber}`,
        accessible: values.accessible,
      }
    })

    onSubmit(section.id, seats)
  }

  return (
    <Modal
      confirmLoading={submitting}
      okText="Create seats"
      open={open}
      title={section ? `Bulk add seats to ${section.name}` : 'Bulk add seats'}
      onCancel={onCancel}
      onOk={() => form.submit()}
    >
      {error ? (
        <Alert className="admin-inline-alert" message={error} showIcon type="error" />
      ) : null}

      <Form<BulkSeatFormValues>
        disabled={submitting}
        form={form}
        initialValues={defaultValues}
        layout="vertical"
        name="bulkSeatForm"
        requiredMark={false}
        onFinish={handleFinish}
      >
        <Form.Item
          label="Row label"
          name="rowLabel"
          rules={[
            { required: true, message: 'Row label is required' },
            { max: 32, message: 'Row label must be 32 characters or fewer' },
          ]}
        >
          <Input autoComplete="off" />
        </Form.Item>

        <div className="admin-form-grid two-column">
          <Form.Item
            label="Starting seat number"
            name="startNumber"
            rules={[
              { required: true, message: 'Starting seat number is required' },
              {
                type: 'number',
                min: 1,
                message: 'Starting seat number must be at least 1',
              },
            ]}
          >
            <InputNumber min={1} precision={0} />
          </Form.Item>

          <Form.Item
            label="Seat count"
            name="quantity"
            rules={[
              { required: true, message: 'Seat count is required' },
              { type: 'number', min: 1, message: 'Seat count must be at least 1' },
              { type: 'number', max: 500, message: 'Seat count must be 500 or fewer' },
            ]}
          >
            <InputNumber min={1} max={500} precision={0} />
          </Form.Item>
        </div>

        <Form.Item
          extra="Applies to every seat in this batch. Ticket buyers see a wheelchair marker on the seat map; it does not change price or availability. You can change individual seats afterwards in the seat layout below."
          label="Wheelchair accessible"
          name="accessible"
          valuePropName="checked"
        >
          <Switch checkedChildren="Accessible" unCheckedChildren="Standard" />
        </Form.Item>
      </Form>
    </Modal>
  )
}
