import { Tag } from 'antd'

import type { EventSalesStatus } from '../../../events/types'
import { salesStatusPresentation } from '../../../events/utils/salesStatus'

/** Shows an admin the same buyable-right-now label the catalogue shows a visitor. */
export function EventSalesStatusTag({ status }: { status: EventSalesStatus }) {
  const { label, color } = salesStatusPresentation(status)

  return <Tag color={color}>{label}</Tag>
}
