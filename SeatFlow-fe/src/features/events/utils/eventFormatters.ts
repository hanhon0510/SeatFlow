export function formatDateTime(value: string, timezone: string) {
  return new Intl.DateTimeFormat(undefined, {
    dateStyle: 'medium',
    timeStyle: 'short',
    timeZone: timezone,
  }).format(new Date(value))
}

export function formatMinimumPrice(value: number | null) {
  if (value === null) {
    return 'Price pending'
  }
  return `From ${new Intl.NumberFormat(undefined, { maximumFractionDigits: 2 }).format(value)}`
}
