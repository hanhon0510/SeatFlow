import { MailOutlined } from '@ant-design/icons'
import { useMutation } from '@tanstack/react-query'
import { Alert, Button, Form, Input, Typography } from 'antd'
import { Link } from 'react-router-dom'

import { apiErrorMessage } from '../../../shared/api/apiError'
import { ROUTES } from '../../../shared/constants/routes'
import { requestPasswordReset } from '../api/authApi'
import type { ForgotPasswordRequest } from '../types'

export function ForgotPasswordPage() {
  const requestMutation = useMutation({ mutationFn: requestPasswordReset })

  if (requestMutation.isSuccess) {
    return (
      <section className="page-surface auth-page">
        <Typography.Title level={1}>Check your email</Typography.Title>
        <Alert
          showIcon
          title={`If an account exists for ${requestMutation.variables.email}, we have sent a link to choose a new password.`}
          description="The link works once and expires in an hour. If nothing arrives, check your spam folder or try again."
          type="success"
        />
        <Typography.Paragraph className="auth-alt-action">
          <Link to={ROUTES.login}>Back to log in</Link>
        </Typography.Paragraph>
      </section>
    )
  }

  return (
    <section className="page-surface auth-page">
      <Typography.Title level={1}>Forgot your password?</Typography.Title>
      <Typography.Paragraph type="secondary">
        Enter the email you registered with and we will send you a link to choose a new password.
      </Typography.Paragraph>

      {requestMutation.isError ? (
        <Alert
          showIcon
          title={apiErrorMessage(requestMutation.error, 'Could not send the link. Please try again.')}
          type="error"
        />
      ) : null}

      <Form<ForgotPasswordRequest>
        className="auth-form"
        disabled={requestMutation.isPending}
        layout="vertical"
        requiredMark={false}
        onFinish={(values) => requestMutation.mutate({ email: values.email.trim() })}
      >
        <Form.Item
          label="Email"
          name="email"
          normalize={(value: string) => value.trim()}
          rules={[
            { required: true, message: 'Email is required' },
            { type: 'email', message: 'Enter a valid email' },
          ]}
        >
          <Input autoComplete="email" prefix={<MailOutlined />} />
        </Form.Item>

        <Button block htmlType="submit" loading={requestMutation.isPending} type="primary">
          Send reset link
        </Button>
      </Form>

      <Typography.Paragraph className="auth-alt-action">
        Remembered it? <Link to={ROUTES.login}>Log in</Link>
      </Typography.Paragraph>
    </section>
  )
}
