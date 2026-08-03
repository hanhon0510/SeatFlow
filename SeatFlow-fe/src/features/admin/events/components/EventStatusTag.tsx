import { Tag } from 'antd'

import type { EventStatus } from '../types'

const statusColor: Record<EventStatus, string> = {
  DRAFT: 'blue',
  PUBLISHED: 'green',
  CANCELLED: 'red',
  COMPLETED: 'default',
}

export function EventStatusTag({ status }: { status: EventStatus }) {
  return <Tag color={statusColor[status]}>{status}</Tag>
}
