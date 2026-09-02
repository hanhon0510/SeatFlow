import { useState } from 'react'
import { ArrowLeftOutlined, LineChartOutlined, SendOutlined } from '@ant-design/icons'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Alert, App as AntdApp, Button, Modal, Space, Spin, Typography } from 'antd'
import { Link, Navigate, useNavigate, useParams } from 'react-router-dom'

import { apiErrorMessage } from '../../../../shared/api/apiError'
import { ROUTES } from '../../../../shared/constants/routes'
import { getSeatLayout, listVenues, venueQueryKeys } from '../../venues/api/venuesApi'
import {
  createEvent,
  eventQueryKeys,
  getEvent,
  getEventSections,
  publishEvent,
  replaceEventSections,
  updateEvent,
} from '../api/eventsApi'
import { EventForm } from '../components/EventForm'
import { EventStatusTag } from '../components/EventStatusTag'
import { SectionPricingTable } from '../components/SectionPricingTable'
import type { EventFormValues, EventSectionPriceRequest } from '../types'
import { eventFormInitialValues } from '../utils/eventFormInitialValues'

type EventFormPageProps = {
  mode: 'create' | 'edit'
}

export function EventFormPage({ mode }: EventFormPageProps) {
  const { eventId } = useParams()
  const [selectedVenueId, setSelectedVenueId] = useState<string | undefined>()
  const [formError, setFormError] = useState<string | null>(null)
  const [pricingError, setPricingError] = useState<string | null>(null)
  const [publishError, setPublishError] = useState<string | null>(null)
  const [publishModalOpen, setPublishModalOpen] = useState(false)
  const queryClient = useQueryClient()
  const navigate = useNavigate()
  const { notification } = AntdApp.useApp()
  const isEditMode = mode === 'edit'

  const venuesQuery = useQuery({
    queryKey: venueQueryKeys.list(0, 100),
    queryFn: () => listVenues(0, 100),
  })
  const eventQuery = useQuery({
    queryKey: eventId ? eventQueryKeys.detail(eventId) : eventQueryKeys.detail('missing'),
    queryFn: () => getEvent(eventId ?? ''),
    enabled: isEditMode && Boolean(eventId),
  })
  const layoutQuery = useQuery({
    queryKey: eventQuery.data ? venueQueryKeys.layout(eventQuery.data.venueId) : venueQueryKeys.layout('missing'),
    queryFn: () => getSeatLayout(eventQuery.data?.venueId ?? ''),
    enabled: isEditMode && Boolean(eventQuery.data?.venueId),
  })
  const eventSectionsQuery = useQuery({
    queryKey: eventId ? eventQueryKeys.sections(eventId) : eventQueryKeys.sections('missing'),
    queryFn: () => getEventSections(eventId ?? ''),
    enabled: isEditMode && Boolean(eventId),
  })

  const createEventMutation = useMutation({
    mutationFn: createEvent,
    onSuccess: async (event) => {
      setFormError(null)
      await queryClient.invalidateQueries({ queryKey: eventQueryKeys.all })
      notification.success({ title: 'Event created' })
      navigate(ROUTES.adminEventEdit(event.id), { replace: true })
    },
    onError: (error) => {
      setFormError(apiErrorMessage(error, 'Could not create event'))
    },
  })

  const updateEventMutation = useMutation({
    mutationFn: (values: EventFormValues) => updateEvent(eventId ?? '', values),
    onSuccess: async () => {
      setFormError(null)
      await queryClient.invalidateQueries({ queryKey: eventQueryKeys.all })
      notification.success({ title: 'Event updated' })
    },
    onError: (error) => {
      setFormError(apiErrorMessage(error, 'Could not update event'))
    },
  })

  const replaceSectionsMutation = useMutation({
    mutationFn: (sections: EventSectionPriceRequest[]) => replaceEventSections(eventId ?? '', sections),
    onSuccess: async () => {
      setPricingError(null)
      if (eventId) {
        await queryClient.invalidateQueries({ queryKey: eventQueryKeys.sections(eventId) })
      }
      notification.success({ title: 'Pricing saved' })
    },
    onError: (error) => {
      setPricingError(apiErrorMessage(error, 'Could not save pricing'))
    },
  })

  const publishMutation = useMutation({
    mutationFn: () => publishEvent(eventId ?? ''),
    onSuccess: async () => {
      setPublishError(null)
      setPublishModalOpen(false)
      await queryClient.invalidateQueries({ queryKey: eventQueryKeys.all })
      notification.success({ title: 'Event published' })
    },
    onError: (error) => {
      setPublishError(apiErrorMessage(error, 'Could not publish event'))
    },
  })

  if (isEditMode && !eventId) {
    return <Navigate to={ROUTES.adminEvents} replace />
  }

  const event = eventQuery.data
  const venues = venuesQuery.data?.items ?? []
  const selectedVenue = venues.find((venue) => venue.id === (selectedVenueId ?? event?.venueId))
  const submittingEvent = createEventMutation.isPending || updateEventMutation.isPending
  const initialValues = event ? eventFormInitialValues(event) : undefined
  const eventLoadError = eventQuery.isError
    ? apiErrorMessage(eventQuery.error, 'Could not load event')
    : null
  const venuesError = venuesQuery.isError
    ? apiErrorMessage(venuesQuery.error, 'Could not load venues')
    : null
  const pricingLoadError = layoutQuery.isError
    ? apiErrorMessage(layoutQuery.error, 'Could not load venue sections')
    : eventSectionsQuery.isError
      ? apiErrorMessage(eventSectionsQuery.error, 'Could not load event pricing')
      : null

  const handleSubmit = (values: EventFormValues) => {
    setFormError(null)

    if (isEditMode) {
      updateEventMutation.mutate(values)
      return
    }

    createEventMutation.mutate(values)
  }

  const handlePublish = () => {
    setPublishError(null)
    publishMutation.mutate()
  }

  return (
    <section className="page-surface admin-page">
      <div className="admin-page-header">
        <Space align="center" wrap>
          <Typography.Title level={1}>
            {isEditMode ? 'Edit event' : 'Create event'}
          </Typography.Title>
          {event ? <EventStatusTag status={event.status} /> : null}
        </Space>
        <Space wrap>
          {event?.status === 'DRAFT' ? (
            <Button
              icon={<SendOutlined />}
              onClick={() => {
                setPublishError(null)
                setPublishModalOpen(true)
              }}
            >
              Publish event
            </Button>
          ) : null}
          {isEditMode && eventId ? (
            <Button icon={<LineChartOutlined />}>
              <Link to={ROUTES.adminEventDetail(eventId)}>Sales report</Link>
            </Button>
          ) : null}
          <Button icon={<ArrowLeftOutlined />}>
            <Link to={ROUTES.adminEvents}>Back to events</Link>
          </Button>
        </Space>
      </div>

      {formError ? (
        <Alert className="admin-inline-alert" showIcon title={formError} type="error" />
      ) : null}

      {eventLoadError ? (
        <Alert className="admin-inline-alert" showIcon title={eventLoadError} type="error" />
      ) : null}

      {venuesError ? (
        <Alert className="admin-inline-alert" showIcon title={venuesError} type="error" />
      ) : null}

      <Spin spinning={(isEditMode && eventQuery.isLoading) || venuesQuery.isLoading}>
        <section className="admin-section">
          <EventForm
            initialValues={initialValues}
            selectedVenue={selectedVenue}
            submitLabel={isEditMode ? 'Save event' : 'Create event'}
            submitting={submittingEvent}
            venueLocked={event?.status === 'PUBLISHED'}
            venues={venues}
            onSubmit={handleSubmit}
            onVenueChange={setSelectedVenueId}
          />
        </section>
      </Spin>

      {isEditMode && event ? (
        <SectionPricingTable
          configuration={eventSectionsQuery.data}
          error={pricingError ?? pricingLoadError}
          event={event}
          loading={layoutQuery.isLoading || eventSectionsQuery.isLoading}
          sections={layoutQuery.data?.sections ?? []}
          submitting={replaceSectionsMutation.isPending}
          onSubmit={(sections) => {
            setPricingError(null)
            replaceSectionsMutation.mutate(sections)
          }}
        />
      ) : null}

      <Modal
        confirmLoading={publishMutation.isPending}
        okText="Publish event"
        open={publishModalOpen}
        title="Publish event"
        onCancel={() => {
          if (!publishMutation.isPending) {
            setPublishModalOpen(false)
            setPublishError(null)
          }
        }}
        onOk={handlePublish}
      >
        {publishError ? (
          <Alert className="admin-inline-alert" showIcon title={publishError} type="error" />
        ) : null}
        <Typography.Paragraph>
          Publish {event?.name}? This creates permanent event-seat inventory from the venue layout and section pricing.
        </Typography.Paragraph>
      </Modal>
    </section>
  )
}
