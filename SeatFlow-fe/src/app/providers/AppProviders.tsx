import type { PropsWithChildren } from 'react'
import { ConfigProvider } from 'antd'
import enUS from 'antd/locale/en_US'
import { BrowserRouter } from 'react-router-dom'

export function AppProviders({ children }: PropsWithChildren) {
  return (
    <ConfigProvider
      locale={enUS}
      theme={{
        token: {
          colorPrimary: '#246bfe',
          borderRadius: 10,
        },
      }}
    >
      <BrowserRouter>{children}</BrowserRouter>
    </ConfigProvider>
  )
}
