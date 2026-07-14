import { Typography } from 'antd'

export function HomePage() {
  return (
    <div className="app-shell">
      <div className="content-shell">
        <p className="app-brand">SeatFlow</p>
        <Typography.Title level={2}>Welcome</Typography.Title>
        <Typography.Paragraph>
          Basic project scaffold is ready. Add features under <code>src/features</code>.
        </Typography.Paragraph>
      </div>
    </div>
  )
}
