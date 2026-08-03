import { useEffect } from 'react'
import { SaveOutlined } from '@ant-design/icons'
import { Button, DatePicker, Form, Input, Select, Space, Typography } from 'antd'

import type { Venue } from '../../venues/types'
import type { EventFormValues } from '../types'

type EventFormProps = {
  initialValues?: EventFormValues
  submitLabel: string
  submitting: boolean
  venues: Venue[]
  venueLocked: boolean
  selectedVenue?: Venue
  onSubmit: (values: EventFormValues) => void
  onVenueChange: (venueId?: string) => void
}

export function EventForm({
  initialValues,
  submitLabel,
  submitting,
  venues,
  venueLocked,
  selectedVenue,
  onSubmit,
  onVenueChange,
}: EventFormProps) {
  const [form] = Form.useForm<EventFormValues>()

  useEffect(() => {
    if (initialValues) {
      form.setFieldsValue(initialValues)
      onVenueChange(initialValues.venueId)
      return
    }

    form.resetFields()
  }, [form, initialValues, onVenueChange])

  useEffect(() => {
    if (initialValues || form.getFieldValue('venueId')) {
      return
    }

    const activeVenues = venues.filter((venue) => venue.status === 'ACTIVE')
    if (activeVenues.length === 1) {
      form.setFieldValue('venueId', activeVenues[0].id)
      onVenueChange(activeVenues[0].id)
    }
  }, [form, initialValues, onVenueChange, venues])

  return (
    <Form<EventFormValues>
      className="event-form"
      disabled={submitting}
      form={form}
      layout="vertical"
      name="eventForm"
      requiredMark={false}
      onFinish={onSubmit}
      onValuesChange={(_, values) => onVenueChange(values.venueId)}
    >
      <div className="admin-form-grid two-column">
        <Form.Item
          label="Venue"
          name="venueId"
          rules={[{ required: true, message: 'Venue is required' }]}
        >
          <Select
            disabled={venueLocked || submitting}
            optionFilterProp="label"
            options={venues.map((venue) => ({
              disabled: venue.status === 'ARCHIVED',
              label: `${venue.name} (${venue.timezone})`,
              value: venue.id,
            }))}
            placeholder="Select venue"
            showSearch
          />
        </Form.Item>

        <Form.Item label="Venue timezone">
          <Typography.Text>
            {selectedVenue?.timezone ?? 'Select a venue'}
          </Typography.Text>
        </Form.Item>
      </div>

      <Form.Item
        label="Event name"
        name="name"
        rules={[
          { required: true, message: 'Event name is required' },
          { max: 180, message: 'Event name must be 180 characters or fewer' },
        ]}
      >
        <Input autoComplete="off" />
      </Form.Item>

      <Form.Item
        label="Description"
        name="description"
        rules={[{ max: 4000, message: 'Description must be 4000 characters or fewer' }]}
      >
        <Input.TextArea rows={3} />
      </Form.Item>

      <div className="admin-form-grid">
        <Form.Item
          label="Event start"
          name="startTime"
          rules={[{ required: true, message: 'Event start is required' }]}
        >
          <DatePicker
            format="YYYY-MM-DD HH:mm"
            showTime={{ format: 'HH:mm' }}
          />
        </Form.Item>

        <Form.Item
          dependencies={['startTime']}
          label="Sales start"
          name="salesStartTime"
          rules={[
            { required: true, message: 'Sales start is required' },
            ({ getFieldValue }) => ({
              validator(_, value) {
                const startTime = getFieldValue('startTime') as EventFormValues['startTime'] | undefined
                if (!value || !startTime || value.isBefore(startTime)) {
                  return Promise.resolve()
                }
                return Promise.reject(new Error('Sales start must be before event start'))
              },
            }),
          ]}
        >
          <DatePicker
            format="YYYY-MM-DD HH:mm"
            showTime={{ format: 'HH:mm' }}
          />
        </Form.Item>

        <Form.Item
          dependencies={['startTime']}
          label="Sales end"
          name="salesEndTime"
          rules={[
            { required: true, message: 'Sales end is required' },
            ({ getFieldValue }) => ({
              validator(_, value) {
                const startTime = getFieldValue('startTime') as EventFormValues['startTime'] | undefined
                if (!value || !startTime || !value.isAfter(startTime)) {
                  return Promise.resolve()
                }
                return Promise.reject(new Error('Sales end cannot be after event start'))
              },
            }),
          ]}
        >
          <DatePicker
            format="YYYY-MM-DD HH:mm"
            showTime={{ format: 'HH:mm' }}
          />
        </Form.Item>
      </div>

      <Space wrap>
        <Button
          htmlType="submit"
          icon={<SaveOutlined />}
          loading={submitting}
          type="primary"
        >
          {submitLabel}
        </Button>
      </Space>
    </Form>
  )
}
