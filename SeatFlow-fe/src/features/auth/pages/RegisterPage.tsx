import { useState } from 'react'
import { Alert, Button, Form, Input, Typography, App as AntdApp } from 'antd'
import { LockOutlined, MailOutlined } from '@ant-design/icons'
import { isAxiosError } from 'axios'
import { Link, useNavigate } from 'react-router-dom'

import { ROUTES } from '../../../shared/constants/routes'
import { useAuth } from '../context/useAuth'
import type { RegisterRequest } from '../types'

export function RegisterPage() {
  const [error, setError] = useState<string | null>(null)
  const [submitting, setSubmitting] = useState(false)
  const { register } = useAuth()
  const { notification } = AntdApp.useApp()
  const navigate = useNavigate()

  const handleSubmit = async (values: RegisterRequest) => {
    setError(null)
    setSubmitting(true)

    try {
      await register(values)
      notification.success({ title: 'Account created' })
      navigate(ROUTES.login, { replace: true })
    } catch (submitError) {
      setError(errorMessage(submitError, 'Registration failed'))
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <section className="page-surface auth-page">
      <Typography.Title level={1}>Register</Typography.Title>

      {error ? <Alert type="error" title={error} showIcon /> : null}

      <Form<RegisterRequest>
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
          rules={[
            { required: true, message: 'Password is required' },
            { min: 12, message: 'Password must be at least 12 characters' },
            {
              pattern: /[a-z]/,
              message: 'Password must include a lowercase letter',
            },
            {
              pattern: /[A-Z]/,
              message: 'Password must include an uppercase letter',
            },
            {
              pattern: /\d/,
              message: 'Password must include a number',
            },
            {
              pattern: /[^A-Za-z0-9]/,
              message: 'Password must include a special character',
            },
            {
              pattern: /^\S+$/,
              message: 'Password cannot contain spaces',
            },
          ]}
        >
          <Input.Password autoComplete="new-password" prefix={<LockOutlined />} />
        </Form.Item>

        <Button block htmlType="submit" loading={submitting} type="primary">
          Create account
        </Button>
      </Form>

      <Typography.Paragraph className="auth-alt-action">
        Already have an account? <Link to={ROUTES.login}>Log in</Link>
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
