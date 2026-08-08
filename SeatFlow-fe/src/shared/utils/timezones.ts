const fallbackTimezones = [
  'UTC',
  'Africa/Cairo',
  'America/Chicago',
  'America/Denver',
  'America/Los_Angeles',
  'America/New_York',
  'America/Toronto',
  'Asia/Bangkok',
  'Asia/Dubai',
  'Asia/Ho_Chi_Minh',
  'Asia/Saigon',
  'Asia/Singapore',
  'Asia/Tokyo',
  'Australia/Sydney',
  'Europe/Berlin',
  'Europe/London',
  'Europe/Paris',
  'Pacific/Auckland',
]

type IntlWithTimeZones = typeof Intl & {
  supportedValuesOf?: (key: 'timeZone') => string[]
}

export const timezoneOptions = getSupportedTimezones().map((timezone) => ({
  label: timezone,
  value: timezone,
}))

export function getBrowserTimezone() {
  return Intl.DateTimeFormat().resolvedOptions().timeZone ?? 'UTC'
}

function getSupportedTimezones() {
  const intlTimezones = (Intl as IntlWithTimeZones).supportedValuesOf?.('timeZone') ?? []
  const currentTimezone = getBrowserTimezone()
  return Array.from(new Set([...intlTimezones, ...fallbackTimezones, currentTimezone]))
    .filter(Boolean)
    .sort((first, second) => first.localeCompare(second))
}
