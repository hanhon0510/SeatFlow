import { useEffect } from 'react'
import { SaveOutlined } from '@ant-design/icons'
import { Button, Form, Input, Select } from 'antd'

import { getBrowserTimezone, timezoneOptions } from '../../../../shared/utils/timezones'
import type { VenueFormValues } from '../types'

type VenueFormProps = {
  initialValues?: VenueFormValues
  submitLabel: string
  submitting: boolean
  onSubmit: (values: VenueFormValues) => void
}

export function VenueForm({
  initialValues,
  submitLabel,
  submitting,
  onSubmit,
}: VenueFormProps) {
  const [form] = Form.useForm<VenueFormValues>()

  useEffect(() => {
    if (initialValues) {
      form.setFieldsValue(initialValues)
      return
    }

    form.resetFields()
    form.setFieldValue('timezone', getBrowserTimezone())
  }, [form, initialValues])

  return (
    <Form<VenueFormValues>
      className="venue-form"
      disabled={submitting}
      form={form}
      layout="vertical"
      name="venueForm"
      requiredMark={false}
      onFinish={onSubmit}
    >
      <Form.Item
        label="Venue name"
        name="name"
        rules={[
          { required: true, message: 'Venue name is required' },
          { max: 160, message: 'Venue name must be 160 characters or fewer' },
        ]}
      >
        <Input autoComplete="organization" />
      </Form.Item>

      <Form.Item
        label="Address"
        name="address"
        rules={[
          { required: true, message: 'Address is required' },
          { max: 255, message: 'Address must be 255 characters or fewer' },
        ]}
      >
        <Input autoComplete="street-address" />
      </Form.Item>

      <div className="admin-form-grid">
        <Form.Item
          label="City"
          name="city"
          rules={[
            { required: true, message: 'City is required' },
            { max: 120, message: 'City must be 120 characters or fewer' },
          ]}
        >
          <Input autoComplete="address-level2" />
        </Form.Item>

        <Form.Item
          label="Country"
          name="country"
          rules={[
            { required: true, message: 'Country is required' },
            { max: 120, message: 'Country must be 120 characters or fewer' },
          ]}
        >
          <Input autoComplete="country-name" />
        </Form.Item>

        <Form.Item
          label="Timezone"
          name="timezone"
          rules={[
            { required: true, message: 'Timezone is required' },
            { max: 64, message: 'Timezone must be 64 characters or fewer' },
          ]}
        >
          <Select
            aria-label="Timezone"
            optionFilterProp="label"
            options={timezoneOptions}
            placeholder="Select timezone"
            showSearch
          />
        </Form.Item>
      </div>

      <Button
        htmlType="submit"
        icon={<SaveOutlined />}
        loading={submitting}
        type="primary"
      >
        {submitLabel}
      </Button>
    </Form>
  )
}
