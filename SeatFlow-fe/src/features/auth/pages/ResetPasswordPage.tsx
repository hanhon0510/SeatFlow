import { LockOutlined } from '@ant-design/icons'
import { useMutation } from '@tanstack/react-query'
import { Alert, Button, Form, Input, Typography } from 'antd'
import { Link, useSearchParams } from 'react-router-dom'

import { apiErrorCode, apiErrorMessage } from '../../../shared/api/apiError'
import { ROUTES } from '../../../shared/constants/routes'
import { resetPassword } from '../api/authApi'
import { passwordRules } from '../passwordRules'

type ResetPasswordFormValues = {
  password: string
  confirmPassword: string
}

export function ResetPasswordPage() {
  const [searchParams] = useSearchParams()
  const token = searchParams.get('token')?.trim() ?? ''
  const resetMutation = useMutation({ mutationFn: resetPassword })

  if (!token) {
    return (
      <section className="page-surface auth-page">
        <Typography.Title level={1}>Choose a new password</Typography.Title>
        <Alert
          showIcon
          title="This link is incomplete"
          description="Open the link from the email exactly as it was sent, or ask for a new one."
          type="error"
        />
        <Typography.Paragraph className="auth-alt-action">
          <Link to={ROUTES.forgotPassword}>Send me a new link</Link>
        </Typography.Paragraph>
      </section>
    )
  }

  if (resetMutation.isSuccess) {
    return (
      <section className="page-surface auth-page">
        <Typography.Title level={1}>Password changed</Typography.Title>
        <Alert
          showIcon
          title="Your password has been changed"
          description="You were signed out on all devices. Log in with your new password."
          type="success"
        />
        <Typography.Paragraph className="auth-alt-action">
          <Link to={ROUTES.login}>Log in</Link>
        </Typography.Paragraph>
      </section>
    )
  }

  const linkRejected = apiErrorCode(resetMutation.error) === 'INVALID_PASSWORD_RESET_TOKEN'

  return (
    <section className="page-surface auth-page">
      <Typography.Title level={1}>Choose a new password</Typography.Title>

      {resetMutation.isError ? (
        <Alert
          showIcon
          title={apiErrorMessage(resetMutation.error, 'Could not change the password. Please try again.')}
          description={
            linkRejected ? <Link to={ROUTES.forgotPassword}>Send me a new link</Link> : undefined
          }
          type="error"
        />
      ) : null}

      <Form<ResetPasswordFormValues>
        className="auth-form"
        disabled={resetMutation.isPending}
        layout="vertical"
        requiredMark={false}
        onFinish={(values) => resetMutation.mutate({ token, password: values.password })}
      >
        <Form.Item label="New password" name="password" rules={passwordRules}>
          <Input.Password autoComplete="new-password" prefix={<LockOutlined />} />
        </Form.Item>

        <Form.Item
          dependencies={['password']}
          label="Confirm new password"
          name="confirmPassword"
          rules={[
            { required: true, message: 'Confirm your new password' },
            ({ getFieldValue }) => ({
              validator(_, value: string) {
                if (!value || getFieldValue('password') === value) {
                  return Promise.resolve()
                }
                return Promise.reject(new Error('The passwords do not match'))
              },
            }),
          ]}
        >
          <Input.Password autoComplete="new-password" prefix={<LockOutlined />} />
        </Form.Item>

        <Button block htmlType="submit" loading={resetMutation.isPending} type="primary">
          Change password
        </Button>
      </Form>
    </section>
  )
}
