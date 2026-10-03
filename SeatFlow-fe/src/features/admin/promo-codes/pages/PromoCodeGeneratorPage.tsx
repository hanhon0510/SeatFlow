import { CopyOutlined, ReloadOutlined } from '@ant-design/icons'
import { useMutation } from '@tanstack/react-query'
import { Alert, App as AntdApp, Button, Form, Input, InputNumber, Segmented, Space, Tooltip, Typography } from 'antd'

import { apiErrorMessage } from '../../../../shared/api/apiError'
import { generatePromoCode } from '../api/promoCodesApi'
import type { PromoCodeGeneratorValues } from '../types'

const initialValues: PromoCodeGeneratorValues = {
  code: '',
  length: 10,
  characterSet: 'ALPHANUMERIC',
}

export function PromoCodeGeneratorPage() {
  const [form] = Form.useForm<PromoCodeGeneratorValues>()
  const { notification } = AntdApp.useApp()
  const generateMutation = useMutation({
    mutationFn: generatePromoCode,
    onSuccess: ({ code }) => {
      form.setFieldValue('code', code)
      notification.success({ title: 'Code generated' })
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

  return (
    <section className="page-surface admin-page">
      <div className="admin-page-header">
        <Typography.Title level={1}>Promo code generator</Typography.Title>
      </div>

      {generateMutation.isError ? (
        <Alert
          className="admin-inline-alert"
          message={apiErrorMessage(generateMutation.error, 'Could not generate a promo code')}
          showIcon
          type="error"
        />
      ) : null}

      <section className="admin-section promo-code-generator-section">
        <Form<PromoCodeGeneratorValues>
          form={form}
          initialValues={initialValues}
          layout="vertical"
          name="promoCodeGenerator"
          requiredMark={false}
        >
          <Form.Item label="Code" name="code">
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
              type="primary"
              onClick={() => void handleGenerate()}
            >
              Generate code
            </Button>
          </Space>
        </Form>
      </section>
    </section>
  )
}
