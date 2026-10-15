import { Tag } from 'antd'

import type { TicketStatus } from '../types'

const colors: Record<TicketStatus, string> = {
  ACTIVE: 'green',
  USED: 'default',
  CANCELLED: 'red',
}

export function TicketStatusTag({ status }: { status: TicketStatus }) {
  return <Tag color={colors[status]}>{status}</Tag>
}
