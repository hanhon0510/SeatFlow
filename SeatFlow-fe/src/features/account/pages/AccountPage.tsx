import { LockOutlined } from '@ant-design/icons'
import { useMutation } from '@tanstack/react-query'
import { Alert, Button, Card, Descriptions, Form, Input, Typography } from 'antd'

import { apiErrorMessage } from '../../../shared/api/apiError'
import { useAuth } from '../../auth/context/useAuth'
import { passwordRules } from '../../auth/passwordRules'
import { changePassword } from '../api/accountApi'

type ChangePasswordFormValues = {
  currentPassword: string
  newPassword: string
  confirmPassword: string
}

export function AccountPage() {
  const { user } = useAuth()
  const [form] = Form.useForm<ChangePasswordFormValues>()

  const changeMutation = useMutation({
    mutationFn: changePassword,
    onSuccess: () => form.resetFields(),
  })

  return (
    <section className="page-surface account-page">
      <div className="public-page-header">
        <Typography.Title level={1}>Account</Typography.Title>
      </div>

      <Card title="Your details">
        <Descriptions column={1} size="small">
          <Descriptions.Item label="Email">{user?.email}</Descriptions.Item>
        </Descriptions>
      </Card>

      <Card title="Change password">
        {changeMutation.isSuccess ? (
          <Alert
            showIcon
            style={{ marginBottom: 16 }}
            title="Your password has been changed"
            description="Your other devices were signed out. This one stays signed in."
            type="success"
          />
        ) : null}
        {changeMutation.isError ? (
          <Alert
            showIcon
            style={{ marginBottom: 16 }}
            title={apiErrorMessage(changeMutation.error, 'Could not change the password. Please try again.')}
            type="error"
          />
        ) : null}

        <Form<ChangePasswordFormValues>
          className="auth-form"
          disabled={changeMutation.isPending}
          form={form}
          layout="vertical"
          requiredMark={false}
          onFinish={(values) =>
            changeMutation.mutate({ currentPassword: values.currentPassword, newPassword: values.newPassword })
          }
          onValuesChange={() => {
            if (!changeMutation.isIdle) {
              changeMutation.reset()
            }
          }}
        >
          <Form.Item
            label="Current password"
            name="currentPassword"
            rules={[{ required: true, message: 'Enter your current password' }]}
          >
            <Input.Password autoComplete="current-password" prefix={<LockOutlined />} />
          </Form.Item>

          <Form.Item label="New password" name="newPassword" rules={passwordRules}>
            <Input.Password autoComplete="new-password" prefix={<LockOutlined />} />
          </Form.Item>

          <Form.Item
            dependencies={['newPassword']}
            label="Confirm new password"
            name="confirmPassword"
            rules={[
              { required: true, message: 'Confirm your new password' },
              ({ getFieldValue }) => ({
                validator(_, value: string) {
                  if (!value || getFieldValue('newPassword') === value) {
                    return Promise.resolve()
                  }
                  return Promise.reject(new Error('The passwords do not match'))
                },
              }),
            ]}
          >
            <Input.Password autoComplete="new-password" prefix={<LockOutlined />} />
          </Form.Item>

          <Button htmlType="submit" loading={changeMutation.isPending} type="primary">
            Change password
          </Button>
        </Form>
      </Card>
    </section>
  )
}
