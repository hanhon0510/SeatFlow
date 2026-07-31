import { PlusOutlined } from '@ant-design/icons'
import { Alert, Button, Empty, Form, Input, InputNumber, Spin, Table, Tag } from 'antd'
import type { ColumnsType } from 'antd/es/table'

import type { SeatLayout, SeatLayoutSection, SectionFormValues } from '../types'

type SectionManagementProps = {
  createError: string | null
  creating: boolean
  layout?: SeatLayout
  layoutError: string | null
  loadingLayout: boolean
  onAddSection: (values: SectionFormValues) => Promise<void>
  onOpenBulkSeats: (section: SeatLayoutSection) => void
}

export function SectionManagement({
  createError,
  creating,
  layout,
  layoutError,
  loadingLayout,
  onAddSection,
  onOpenBulkSeats,
}: SectionManagementProps) {
  const [form] = Form.useForm<SectionFormValues>()
  const sections = layout?.sections ?? []

  const columns: ColumnsType<SeatLayoutSection> = [
    {
      title: 'Section',
      dataIndex: 'name',
      key: 'name',
    },
    {
      title: 'Order',
      dataIndex: 'displayOrder',
      key: 'displayOrder',
      width: 120,
      responsive: ['md'],
    },
    {
      title: 'Seats',
      key: 'seats',
      width: 120,
      render: (_, section) => <Tag>{section.seats.length}</Tag>,
    },
    {
      title: 'Actions',
      key: 'actions',
      align: 'right',
      width: 180,
      render: (_, section) => (
        <Button
          aria-label={`Add seats to ${section.name}`}
          icon={<PlusOutlined />}
          onClick={() => onOpenBulkSeats(section)}
        >
          Add seats
        </Button>
      ),
    },
  ]

  const handleAddSection = async (values: SectionFormValues) => {
    await onAddSection(values)
    form.resetFields()
  }

  return (
    <section className="admin-section">
      <div className="admin-section-header">
        <h2>Sections</h2>
      </div>

      {createError ? (
        <Alert className="admin-inline-alert" message={createError} showIcon type="error" />
      ) : null}

      <Form<SectionFormValues>
        className="section-form"
        disabled={creating}
        form={form}
        initialValues={{ displayOrder: 0 }}
        layout="vertical"
        name="sectionForm"
        requiredMark={false}
        onFinish={handleAddSection}
      >
        <Form.Item
          label="Section name"
          name="name"
          rules={[
            { required: true, message: 'Section name is required' },
            { max: 120, message: 'Section name must be 120 characters or fewer' },
          ]}
        >
          <Input autoComplete="off" />
        </Form.Item>

        <Form.Item
          label="Display order"
          name="displayOrder"
          rules={[
            { required: true, message: 'Display order is required' },
            { type: 'number', min: 0, message: 'Display order must be zero or greater' },
          ]}
        >
          <InputNumber min={0} precision={0} />
        </Form.Item>

        <Form.Item className="section-form-action">
          <Button
            htmlType="submit"
            icon={<PlusOutlined />}
            loading={creating}
            type="primary"
          >
            Add section
          </Button>
        </Form.Item>
      </Form>

      {layoutError ? (
        <Alert className="admin-inline-alert" message={layoutError} showIcon type="error" />
      ) : null}

      <Spin spinning={loadingLayout}>
        <Table<SeatLayoutSection>
          columns={columns}
          dataSource={sections}
          locale={{ emptyText: <Empty description="No sections yet" /> }}
          pagination={false}
          rowKey="id"
        />
      </Spin>
    </section>
  )
}
