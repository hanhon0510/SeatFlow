import { CopyOutlined, PlusOutlined, ReloadOutlined, StopOutlined } from '@ant-design/icons'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import {
  Alert,
  App as AntdApp,
  Button,
  DatePicker,
  Empty,
  Form,
  Input,
  InputNumber,
  Popconfirm,
  Segmented,
  Select,
  Space,
  Table,
  Tag,
  Tooltip,
  Typography,
} from 'antd'
import type { ColumnsType, TablePaginationConfig } from 'antd/es/table'
import { useState } from 'react'

import { apiErrorMessage } from '../../../../shared/api/apiError'
import { eventQueryKeys, listEvents } from '../../events/api/eventsApi'
import {
  createPromoCode,
  deactivatePromoCode,
  generatePromoCode,
  listPromoCodes,
  promoCodeQueryKeys,
} from '../api/promoCodesApi'
import type { PromoCode, PromoCodeFormValues, PromoCodeGeneratorValues } from '../types'

type PromoCodeFormState = PromoCodeGeneratorValues & Omit<PromoCodeFormValues, 'code'>

const initialValues: Partial<PromoCodeFormState> = {
  code: '',
  length: 10,
  characterSet: 'ALPHANUMERIC',
  discountType: 'PERCENT',
}

const pageSizeOptions = [10, 20, 50]

export function PromoCodeGeneratorPage() {
  const [form] = Form.useForm<PromoCodeFormState>()
  const { notification } = AntdApp.useApp()
  const queryClient = useQueryClient()
  const [page, setPage] = useState(0)
  const [size, setSize] = useState(10)
  const discountType = Form.useWatch('discountType', form)

  const generateMutation = useMutation({
    mutationFn: generatePromoCode,
    onSuccess: ({ code }) => {
      form.setFieldValue('code', code)
      notification.success({ title: 'Code generated' })
    },
  })

  const eventsQuery = useQuery({
    queryKey: eventQueryKeys.list(0, 100),
    queryFn: () => listEvents(0, 100),
  })

  const promoCodesQuery = useQuery({
    queryKey: promoCodeQueryKeys.list(page, size),
    queryFn: () => listPromoCodes(page, size),
  })

  const createMutation = useMutation({
    mutationFn: createPromoCode,
    onSuccess: async (promoCode) => {
      notification.success({ title: `Promo code ${promoCode.code} created` })
      form.resetFields(['code', 'discountValue', 'eventId', 'expiresAt', 'maxUses'])
      setPage(0)
      await queryClient.invalidateQueries({ queryKey: promoCodeQueryKeys.all })
    },
  })

  const deactivateMutation = useMutation({
    mutationFn: deactivatePromoCode,
    onSuccess: async (promoCode) => {
      notification.success({ title: `Promo code ${promoCode.code} deactivated` })
      await queryClient.invalidateQueries({ queryKey: promoCodeQueryKeys.all })
    },
    onError: (error) => {
      notification.error({
        title: 'Could not deactivate the code',
        description: apiErrorMessage(error, 'Try again'),
      })
    },
  })

  const handleGenerate = async () => {
    try {
      const values = await form.validateFields(['length', 'characterSet'])
      generateMutation.mutate({
        length: values.length,
        characterSet: values.characterSet,
      })
    }
    catch {
      // Field-level validation explains invalid generator settings.
    }
  }

  const handleCopy = async () => {
    const code = form.getFieldValue('code')?.trim()
    if (!code) {
      notification.warning({ title: 'Generate or enter a code first' })
      return
    }

    try {
      await navigator.clipboard.writeText(code)
      notification.success({ title: 'Code copied' })
    }
    catch {
      notification.error({ title: 'Could not copy the code' })
    }
  }

  const handleCreate = (values: PromoCodeFormState) => {
    createMutation.mutate({
      code: values.code.trim(),
      discountType: values.discountType,
      discountValue: values.discountValue,
      eventId: values.eventId ?? null,
      expiresAt: values.expiresAt ? values.expiresAt.toISOString() : null,
      maxUses: values.maxUses ?? null,
    })
  }

  const eventNames = new Map(eventsQuery.data?.items.map((event) => [event.id, event.name]))

  const columns: ColumnsType<PromoCode> = [
    {
      title: 'Code',
      dataIndex: 'code',
      key: 'code',
      render: (code: string) => <Typography.Text strong copyable>{code}</Typography.Text>,
    },
    {
      title: 'Discount',
      key: 'discount',
      render: (_, promo) => (
        promo.discountType === 'PERCENT'
          ? `${promo.discountValue}%`
          : formatPrice(promo.discountValue)
      ),
    },
    {
      title: 'Event',
      key: 'event',
      responsive: ['md'],
      render: (_, promo) => (
        promo.eventId ? (eventNames.get(promo.eventId) ?? promo.eventId) : 'All events'
      ),
    },
    {
      title: 'Used',
      key: 'used',
      width: 100,
      render: (_, promo) => (
        promo.maxUses === null ? `${promo.usedCount}` : `${promo.usedCount} / ${promo.maxUses}`
      ),
    },
    {
      title: 'Expires',
      dataIndex: 'expiresAt',
      key: 'expiresAt',
      responsive: ['lg'],
      render: (expiresAt: string | null) => (expiresAt ? new Date(expiresAt).toLocaleString() : 'Never'),
    },
    {
      title: 'Status',
      key: 'status',
      width: 120,
      render: (_, promo) => {
        if (!promo.active) {
          return <Tag>Inactive</Tag>
        }
        if (promo.expiresAt && Date.parse(promo.expiresAt) <= Date.now()) {
          return <Tag color="orange">Expired</Tag>
        }
        if (promo.maxUses !== null && promo.usedCount >= promo.maxUses) {
          return <Tag color="orange">Used up</Tag>
        }
        return <Tag color="green">Active</Tag>
      },
    },
    {
      title: 'Actions',
      key: 'actions',
      align: 'right',
      width: 140,
      render: (_, promo) => (
        <Popconfirm
          disabled={!promo.active}
          okButtonProps={{ danger: true }}
          okText="Deactivate"
          title={`Deactivate ${promo.code}?`}
          onConfirm={() => deactivateMutation.mutate(promo.id)}
        >
          <Button
            aria-label={`Deactivate ${promo.code}`}
            danger
            disabled={!promo.active}
            icon={<StopOutlined />}
            loading={deactivateMutation.isPending && deactivateMutation.variables === promo.id}
          >
            Deactivate
          </Button>
        </Popconfirm>
      ),
    },
  ]

  const handleTableChange = (pagination: TablePaginationConfig) => {
    setPage(Math.max(0, (pagination.current ?? 1) - 1))
    setSize(pagination.pageSize ?? size)
  }

  return (
    <section className="page-surface admin-page">
      <div className="admin-page-header">
        <Typography.Title level={1}>Promo codes</Typography.Title>
      </div>

      {generateMutation.isError ? (
        <Alert
          className="admin-inline-alert"
          message={apiErrorMessage(generateMutation.error, 'Could not generate a promo code')}
          showIcon
          type="error"
        />
      ) : null}

      {createMutation.isError ? (
        <Alert
          className="admin-inline-alert"
          message={apiErrorMessage(createMutation.error, 'Could not create the promo code')}
          showIcon
          type="error"
        />
      ) : null}

      <section className="admin-section promo-code-generator-section">
        <Form<PromoCodeFormState>
          form={form}
          initialValues={initialValues}
          layout="vertical"
          name="promoCodeGenerator"
          requiredMark={false}
          onFinish={handleCreate}
        >
          <Form.Item
            label="Code"
            name="code"
            rules={[
              { required: true, message: 'Generate or enter a code' },
              { pattern: /^[A-Za-z0-9]{4,32}$/, message: 'Use 4 to 32 letters or numbers' },
            ]}
          >
            <Space.Compact block>
              <Input autoComplete="off" maxLength={32} />
              <Tooltip title="Copy code">
                <Button aria-label="Copy code" icon={<CopyOutlined />} onClick={() => void handleCopy()} />
              </Tooltip>
            </Space.Compact>
          </Form.Item>

          <div className="admin-form-grid two-column">
            <Form.Item
              label="Length"
              name="length"
              rules={[
                { required: true, message: 'Length is required' },
                { type: 'number', min: 4, max: 32, message: 'Use a length from 4 to 32' },
              ]}
            >
              <InputNumber min={4} max={32} precision={0} />
            </Form.Item>

            <Form.Item label="Characters" name="characterSet" rules={[{ required: true }]}>
              <Segmented
                block
                options={[
                  { label: 'Letters', value: 'LETTERS' },
                  { label: 'Numbers', value: 'NUMBERS' },
                  { label: 'Both', value: 'ALPHANUMERIC' },
                ]}
              />
            </Form.Item>
          </div>

          <Space wrap>
            <Button
              icon={<ReloadOutlined />}
              loading={generateMutation.isPending}
              onClick={() => void handleGenerate()}
            >
              Generate code
            </Button>
          </Space>

          <Typography.Title level={4}>Discount</Typography.Title>

          <div className="admin-form-grid two-column">
            <Form.Item label="Type" name="discountType" rules={[{ required: true }]}>
              <Segmented
                block
                options={[
                  { label: 'Percent', value: 'PERCENT' },
                  { label: 'Fixed amount', value: 'FIXED' },
                ]}
              />
            </Form.Item>

            <Form.Item
              label={discountType === 'FIXED' ? 'Amount off (VND)' : 'Percent off'}
              name="discountValue"
              rules={[
                { required: true, message: 'Discount is required' },
                {
                  type: 'number',
                  min: 1,
                  max: discountType === 'PERCENT' ? 100 : undefined,
                  message: discountType === 'PERCENT' ? 'Use 1 to 100' : 'Must be at least 1',
                },
              ]}
            >
              <InputNumber min={1} max={discountType === 'PERCENT' ? 100 : undefined} precision={0} />
            </Form.Item>

            <Form.Item label="Limit to event" name="eventId">
              <Select
                allowClear
                loading={eventsQuery.isLoading}
                optionFilterProp="label"
                options={eventsQuery.data?.items.map((event) => ({ label: event.name, value: event.id }))}
                placeholder="All events"
                showSearch
              />
            </Form.Item>

            <Form.Item label="Maximum uses" name="maxUses">
              <InputNumber min={1} placeholder="Unlimited" precision={0} />
            </Form.Item>

            <Form.Item label="Expires" name="expiresAt">
              <DatePicker showTime />
            </Form.Item>
          </div>

          <Button
            htmlType="submit"
            icon={<PlusOutlined />}
            loading={createMutation.isPending}
            type="primary"
          >
            Create promo code
          </Button>
        </Form>
      </section>

      {promoCodesQuery.isError ? (
        <Alert
          className="admin-inline-alert"
          message={apiErrorMessage(promoCodesQuery.error, 'Could not load promo codes')}
          showIcon
          type="error"
        />
      ) : null}

      <Table<PromoCode>
        columns={columns}
        dataSource={promoCodesQuery.data?.items ?? []}
        loading={promoCodesQuery.isLoading}
        locale={{ emptyText: <Empty description="No promo codes yet" /> }}
        pagination={{
          current: page + 1,
          pageSize: size,
          pageSizeOptions,
          showSizeChanger: true,
          total: promoCodesQuery.data?.totalItems ?? 0,
        }}
        rowKey="id"
        onChange={handleTableChange}
      />
    </section>
  )
}

function formatPrice(value: number) {
  return new Intl.NumberFormat(undefined, {
    maximumFractionDigits: 0,
    style: 'currency',
    currency: 'VND',
  }).format(value)
}
