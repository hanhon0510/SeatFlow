import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Alert, App as AntdApp, Button, Card, InputNumber, Skeleton, Space, Switch, Typography } from 'antd'
import { useState } from 'react'

import { apiErrorMessage } from '../../../../shared/api/apiError'
import { eventQueryKeys, getEventReminder, updateEventReminder } from '../api/eventsApi'
import type { EventReminder } from '../types'

const MIN_HOURS = 1
const MAX_HOURS = 336

type EventReminderCardProps = {
  eventId: string
}

/** Lets an admin choose how long before the show buyers get a reminder email, or turn it off. */
export function EventReminderCard({ eventId }: EventReminderCardProps) {
  const reminderQuery = useQuery({
    queryKey: eventQueryKeys.reminder(eventId),
    queryFn: () => getEventReminder(eventId),
  })

  return (
    <Card title="Reminder email">
      {reminderQuery.isLoading ? <Skeleton active paragraph={{ rows: 2 }} title={false} /> : null}
      {reminderQuery.isError ? (
        <Alert
          showIcon
          title={apiErrorMessage(reminderQuery.error, 'Could not load the reminder settings')}
          type="error"
        />
      ) : null}
      {reminderQuery.data ? (
        // Keyed on the saved values so the form starts over from them after every save or reload.
        <ReminderForm
          key={`${reminderQuery.data.enabled}-${reminderQuery.data.hoursBefore}`}
          eventId={eventId}
          reminder={reminderQuery.data}
        />
      ) : null}
    </Card>
  )
}

function ReminderForm({ eventId, reminder }: { eventId: string; reminder: EventReminder }) {
  const queryClient = useQueryClient()
  const { notification } = AntdApp.useApp()
  const [enabled, setEnabled] = useState(reminder.enabled)
  const [hours, setHours] = useState<number | null>(reminder.hoursBefore)

  const saveMutation = useMutation({
    mutationFn: (request: { enabled: boolean; hoursBefore: number }) => updateEventReminder(eventId, request),
    onSuccess: async (saved) => {
      queryClient.setQueryData(eventQueryKeys.reminder(eventId), saved)
      notification.success({
        title: 'Reminder saved',
        description: saved.enabled
          ? `Buyers will be reminded ${hoursLabel(saved.hoursBefore)} before the event starts.`
          : 'Buyers will not get a reminder for this event.',
      })
    },
  })

  const hoursValid = hours !== null && Number.isInteger(hours) && hours >= MIN_HOURS && hours <= MAX_HOURS
  const changed = enabled !== reminder.enabled || hours !== reminder.hoursBefore
  const canSave = changed && (hoursValid || (!enabled && hours === reminder.hoursBefore))

  return (
    <Space orientation="vertical" size="middle" style={{ width: '100%' }}>
      <Space align="center" wrap>
        <Switch
          aria-label="Send a reminder email before the event"
          checked={enabled}
          disabled={saveMutation.isPending}
          onChange={setEnabled}
        />
        <Typography.Text>Send buyers a reminder email</Typography.Text>
      </Space>

      <Space align="center" wrap>
        <label htmlFor="reminder-hours">
          <Typography.Text type={enabled ? undefined : 'secondary'}>Send it</Typography.Text>
        </label>
        <InputNumber
          aria-label="Hours before the event starts"
          disabled={!enabled || saveMutation.isPending}
          id="reminder-hours"
          max={MAX_HOURS}
          min={MIN_HOURS}
          precision={0}
          status={enabled && !hoursValid ? 'error' : undefined}
          value={hours}
          onChange={setHours}
        />
        <Typography.Text type={enabled ? undefined : 'secondary'}>hours before the event starts</Typography.Text>
      </Space>

      <Typography.Text type="secondary">
        {describe(reminder, enabled, hoursValid ? hours : null)}
      </Typography.Text>

      {enabled && !hoursValid ? (
        <Alert
          showIcon
          title={`Enter a whole number of hours between ${MIN_HOURS} and ${MAX_HOURS} (${MAX_HOURS / 24} days).`}
          type="warning"
        />
      ) : null}

      {saveMutation.isError ? (
        <Alert
          showIcon
          title={apiErrorMessage(saveMutation.error, 'Could not save the reminder settings')}
          type="error"
        />
      ) : null}

      <Button
        disabled={!canSave}
        loading={saveMutation.isPending}
        type="primary"
        onClick={() => saveMutation.mutate({ enabled, hoursBefore: hours ?? reminder.hoursBefore })}
      >
        Save reminder
      </Button>
    </Space>
  )
}

function describe(saved: EventReminder, enabled: boolean, hours: number | null) {
  const sent = saved.remindersSent === 0 ? 'No reminders sent yet.' : `${saved.remindersSent} sent so far.`
  if (!enabled) {
    return `Reminders are off for this event. ${sent}`
  }
  const source = saved.usingDefault ? ' (the default, until you save a choice)' : ''
  return hours === null
    ? sent
    : `Each buyer gets one email ${hoursLabel(hours)} before the start${source}. Orders placed after that point are not reminded again. ${sent}`
}

function hoursLabel(hours: number) {
  if (hours % 24 === 0 && hours >= 48) {
    return `${hours} hours (${hours / 24} days)`
  }
  return hours === 1 ? '1 hour' : `${hours} hours`
}
