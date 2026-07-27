import { useState } from 'react'
import { Alert, Button, Form, Input, Typography } from 'antd'
import { LockOutlined, MailOutlined } from '@ant-design/icons'
import { isAxiosError } from 'axios'
import { Link, useLocation, useNavigate } from 'react-router-dom'

import { ROUTES } from '../../../shared/constants/routes'
import { useAuth } from '../context/useAuth'
import type { LoginRequest } from '../types'

type LocationState = {
  from?: {
    pathname?: string
  }
}

export function LoginPage() {
  const [error, setError] = useState<string | null>(null)
  const [submitting, setSubmitting] = useState(false)
  const { login } = useAuth()
  const navigate = useNavigate()
  const location = useLocation()
  const state = location.state as LocationState | null
  const redirectTo = state?.from?.pathname ?? ROUTES.events

  const handleSubmit = async (values: LoginRequest) => {
    setError(null)
    setSubmitting(true)

    try {
      await login(values)
      navigate(redirectTo, { replace: true })
    } catch (submitError) {
      setError(errorMessage(submitError, 'Invalid email or password'))
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <section className="page-surface auth-page">
      <Typography.Title level={1}>Login</Typography.Title>

      {error ? <Alert type="error" title={error} showIcon /> : null}

      <Form<LoginRequest>
        className="auth-form"
        disabled={submitting}
        layout="vertical"
        requiredMark={false}
        onFinish={handleSubmit}
      >
        <Form.Item
          label="Email"
          name="email"
          rules={[
            { required: true, message: 'Email is required' },
            { type: 'email', message: 'Enter a valid email' },
          ]}
        >
          <Input autoComplete="email" prefix={<MailOutlined />} />
        </Form.Item>

        <Form.Item
          label="Password"
          name="password"
          rules={[{ required: true, message: 'Password is required' }]}
        >
          <Input.Password autoComplete="current-password" prefix={<LockOutlined />} />
        </Form.Item>

        <Button block htmlType="submit" loading={submitting} type="primary">
          Log in
        </Button>
      </Form>

      <Typography.Paragraph className="auth-alt-action">
        New to SeatFlow? <Link to={ROUTES.register}>Create an account</Link>
      </Typography.Paragraph>
    </section>
  )
}

function errorMessage(error: unknown, fallback: string) {
  if (isAxiosError<{ message?: string }>(error)) {
    return error.response?.data?.message ?? fallback
  }

  return fallback
}
