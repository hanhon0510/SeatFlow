import { Typography } from 'antd'

type PlaceholderPageProps = {
  title: string
}

export function PlaceholderPage({ title }: PlaceholderPageProps) {
  return (
    <section className="page-surface">
      <Typography.Title level={1}>{title}</Typography.Title>
    </section>
  )
}
