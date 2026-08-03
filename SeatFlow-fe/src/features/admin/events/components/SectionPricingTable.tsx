import { useEffect } from 'react'
import { SaveOutlined } from '@ant-design/icons'
import { Alert, Button, Empty, Form, InputNumber, Select, Spin, Table, Typography } from 'antd'
import type { ColumnsType } from 'antd/es/table'

import type { SeatLayoutSection } from '../../venues/types'
import type {
  Event,
  EventSectionConfiguration,
  EventSectionPriceRequest,
} from '../types'

type PricingFormValues = {
  sections?: Record<string, {
    price?: number
    salesEnabled?: 'true' | 'false'
  }>
}

type SectionPricingTableProps = {
  event: Event
  sections: SeatLayoutSection[]
  configuration?: EventSectionConfiguration
  error?: string | null
  loading: boolean
  submitting: boolean
  onSubmit: (sections: EventSectionPriceRequest[]) => void
}

export function SectionPricingTable({
  event,
  sections,
  configuration,
  error,
  loading,
  submitting,
  onSubmit,
}: SectionPricingTableProps) {
  const [form] = Form.useForm<PricingFormValues>()
  const locked = event.status !== 'DRAFT'

  useEffect(() => {
    const priceBySection = new Map(
      configuration?.sections.map((section) => [section.venueSectionId, section]) ?? [],
    )
    form.setFieldsValue({
      sections: Object.fromEntries(
        sections.map((section) => {
          const existing = priceBySection.get(section.id)
          return [
            section.id,
            {
              price: existing?.price,
              salesEnabled: existing?.salesEnabled === false ? 'false' : 'true',
            },
          ]
        }),
      ),
    })
  }, [configuration, form, sections])

  const columns: ColumnsType<SeatLayoutSection> = [
    {
      title: 'Section',
      dataIndex: 'name',
      key: 'name',
      render: (name, section) => (
        <Typography.Text strong>
          {name} <Typography.Text type="secondary">({section.seats.length} seats)</Typography.Text>
        </Typography.Text>
      ),
    },
    {
      title: 'Price',
      dataIndex: 'id',
      key: 'price',
      width: 220,
      render: (sectionId) => (
        <Form.Item
          name={['sections', sectionId, 'price']}
          rules={[
            { required: true, message: 'Price is required' },
            { type: 'number', min: 0, message: 'Price cannot be negative' },
          ]}
        >
          <InputNumber
            min={0}
            precision={2}
            step={1000}
            style={{ width: '100%' }}
          />
        </Form.Item>
      ),
    },
    {
      title: 'Sales',
      dataIndex: 'id',
      key: 'salesEnabled',
      width: 180,
      render: (sectionId) => (
        <Form.Item
          name={['sections', sectionId, 'salesEnabled']}
          rules={[{ required: true, message: 'Sales setting is required' }]}
        >
          <Select
            options={[
              { label: 'Enabled', value: 'true' },
              { label: 'Blocked', value: 'false' },
            ]}
          />
        </Form.Item>
      ),
    },
  ]

  const handleFinish = (values: PricingFormValues) => {
    onSubmit(
      sections.map((section) => ({
        venueSectionId: section.id,
        price: Number(values.sections?.[section.id]?.price ?? 0),
        salesEnabled: values.sections?.[section.id]?.salesEnabled !== 'false',
      })),
    )
  }

  return (
    <section className="admin-section">
      <div className="admin-section-header">
        <Typography.Title level={2}>Section pricing</Typography.Title>
        <Button
          disabled={locked || sections.length === 0}
          htmlType="submit"
          icon={<SaveOutlined />}
          loading={submitting}
          type="primary"
          form="eventPricingForm"
        >
          Save pricing
        </Button>
      </div>

      {locked ? (
        <Alert showIcon title="Published event pricing is locked" type="info" />
      ) : null}

      {error ? (
        <Alert className="admin-inline-alert" showIcon title={error} type="error" />
      ) : null}

      <Spin spinning={loading}>
        <Form<PricingFormValues>
          disabled={locked || submitting}
          form={form}
          id="eventPricingForm"
          name="eventPricingForm"
          onFinish={handleFinish}
        >
          <Table<SeatLayoutSection>
            columns={columns}
            dataSource={sections}
            locale={{ emptyText: <Empty description="No venue sections" /> }}
            pagination={false}
            rowKey="id"
          />
        </Form>
      </Spin>
    </section>
  )
}
