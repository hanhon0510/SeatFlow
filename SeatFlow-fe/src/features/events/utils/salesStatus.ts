import type { EventSalesStatus } from '../types'

type SalesStatusPresentation = {
  label: string
  color: string
}

const presentations: Record<EventSalesStatus, SalesStatusPresentation> = {
  ON_SALE: { label: 'On sale', color: 'green' },
  UPCOMING: { label: 'Coming soon', color: 'blue' },
  SOLD_OUT: { label: 'Sold out', color: 'red' },
  SALES_CLOSED: { label: 'Sales closed', color: 'orange' },
  ENDED: { label: 'Ended', color: 'default' },
}

/** Filter choices, in the order a buyer cares about them. */
export const salesStatusOptions = (Object.keys(presentations) as EventSalesStatus[]).map(
  (status) => ({ value: status, label: presentations[status].label }),
)

export function salesStatusPresentation(status: EventSalesStatus) {
  return presentations[status]
}

/** Seats can only be picked while sales are open; everything else is read-only. */
export function isSeatSelectionOpen(status: EventSalesStatus) {
  return status === 'ON_SALE' || status === 'SOLD_OUT'
}
