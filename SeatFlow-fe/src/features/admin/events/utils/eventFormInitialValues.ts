import dayjs from 'dayjs'

import type { EventFormValues } from '../types'

export function eventFormInitialValues(event: {
  venueId: string
  name: string
  description: string | null
  startTime: string
  salesStartTime: string
  salesEndTime: string
}): EventFormValues {
  return {
    venueId: event.venueId,
    name: event.name,
    description: event.description ?? undefined,
    startTime: dayjs(event.startTime),
    salesStartTime: dayjs(event.salesStartTime),
    salesEndTime: dayjs(event.salesEndTime),
  }
}
