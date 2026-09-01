import { useState } from 'react'
import { ArrowLeftOutlined } from '@ant-design/icons'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Alert, App as AntdApp, Button, Spin, Typography } from 'antd'
import { Link, Navigate, useNavigate, useParams } from 'react-router-dom'

import { apiErrorMessage } from '../../../../shared/api/apiError'
import { ROUTES } from '../../../../shared/constants/routes'
import {
  createSection,
  createSeatsBulk,
  createVenue,
  getSeatLayout,
  getVenue,
  updateSeatAccessibility,
  updateVenue,
  venueQueryKeys,
} from '../api/venuesApi'
import { BulkSeatModal } from '../components/BulkSeatModal'
import { SectionManagement } from '../components/SectionManagement'
import { SeatLayoutPreview } from '../components/SeatLayoutPreview'
import { VenueForm } from '../components/VenueForm'
import type {
  Seat,
  SeatCreateRequest,
  SeatLayoutSection,
  SectionFormValues,
  VenueFormValues,
} from '../types'

type VenueFormPageProps = {
  mode: 'create' | 'edit'
}

export function VenueFormPage({ mode }: VenueFormPageProps) {
  const { venueId } = useParams()
  const [formError, setFormError] = useState<string | null>(null)
  const [sectionError, setSectionError] = useState<string | null>(null)
  const [bulkSeatError, setBulkSeatError] = useState<string | null>(null)
  const [bulkSeatSection, setBulkSeatSection] = useState<SeatLayoutSection | null>(null)
  const [seatAccessibilityError, setSeatAccessibilityError] = useState<string | null>(null)
  const [pendingSeatIds, setPendingSeatIds] = useState<string[]>([])
  const queryClient = useQueryClient()
  const navigate = useNavigate()
  const { notification } = AntdApp.useApp()

  const isEditMode = mode === 'edit'
  const venueQuery = useQuery({
    queryKey: venueId ? venueQueryKeys.detail(venueId) : venueQueryKeys.detail('missing'),
    queryFn: () => getVenue(venueId ?? ''),
    enabled: isEditMode && Boolean(venueId),
  })
  const layoutQuery = useQuery({
    queryKey: venueId ? venueQueryKeys.layout(venueId) : venueQueryKeys.layout('missing'),
    queryFn: () => getSeatLayout(venueId ?? ''),
    enabled: isEditMode && Boolean(venueId),
  })

  const createVenueMutation = useMutation({
    mutationFn: createVenue,
    onSuccess: async (venue) => {
      setFormError(null)
      await queryClient.invalidateQueries({ queryKey: venueQueryKeys.all })
      notification.success({ title: 'Venue created' })
      navigate(ROUTES.adminVenueEdit(venue.id), { replace: true })
    },
    onError: (error) => {
      setFormError(apiErrorMessage(error, 'Could not create venue'))
    },
  })

  const updateVenueMutation = useMutation({
    mutationFn: (values: VenueFormValues) => updateVenue(venueId ?? '', values),
    onSuccess: async () => {
      setFormError(null)
      await queryClient.invalidateQueries({ queryKey: venueQueryKeys.all })
      notification.success({ title: 'Venue updated' })
    },
    onError: (error) => {
      setFormError(apiErrorMessage(error, 'Could not update venue'))
    },
  })

  const createSectionMutation = useMutation({
    mutationFn: (values: SectionFormValues) => createSection(venueId ?? '', values),
    onSuccess: async () => {
      setSectionError(null)
      if (venueId) {
        await queryClient.invalidateQueries({ queryKey: venueQueryKeys.layout(venueId) })
      }
    },
    onError: (error) => {
      setSectionError(apiErrorMessage(error, 'Could not add section'))
    },
  })

  const createSeatsMutation = useMutation({
    mutationFn: ({ sectionId, seats }: { sectionId: string; seats: SeatCreateRequest[] }) =>
      createSeatsBulk(sectionId, seats),
    onSuccess: async () => {
      setBulkSeatError(null)
      setBulkSeatSection(null)
      if (venueId) {
        await queryClient.invalidateQueries({ queryKey: venueQueryKeys.layout(venueId) })
      }
    },
    onError: (error) => {
      setBulkSeatError(apiErrorMessage(error, 'Could not create seats'))
    },
  })

  const seatAccessibilityMutation = useMutation({
    mutationFn: (seat: Seat) => updateSeatAccessibility(seat.id, !seat.accessible),
    onMutate: (seat) => {
      setPendingSeatIds((current) => [...current, seat.id])
    },
    onSuccess: async (seat) => {
      setSeatAccessibilityError(null)
      if (venueId) {
        await queryClient.invalidateQueries({ queryKey: venueQueryKeys.layout(venueId) })
      }
      notification.success({
        title: `Seat ${seat.seatLabel} is now ${seat.accessible ? 'wheelchair accessible' : 'standard'}`,
      })
    },
    onError: (error) => {
      setSeatAccessibilityError(apiErrorMessage(error, 'Could not update the seat'))
    },
    onSettled: (_seat, _error, seat) => {
      setPendingSeatIds((current) => current.filter((seatId) => seatId !== seat.id))
    },
  })

  if (isEditMode && !venueId) {
    return <Navigate to={ROUTES.admin} replace />
  }

  const loadingVenue = isEditMode && venueQuery.isLoading
  const submittingVenue = createVenueMutation.isPending || updateVenueMutation.isPending
  const initialValues = venueQuery.data
    ? {
        name: venueQuery.data.name,
        address: venueQuery.data.address,
        city: venueQuery.data.city,
        country: venueQuery.data.country,
        timezone: venueQuery.data.timezone,
      }
    : undefined

  const handleVenueSubmit = (values: VenueFormValues) => {
    setFormError(null)

    if (isEditMode) {
      updateVenueMutation.mutate(values)
      return
    }

    createVenueMutation.mutate(values)
  }

  const handleAddSection = async (values: SectionFormValues) => {
    setSectionError(null)
    await createSectionMutation.mutateAsync(values)
  }

  const handleBulkSeatSubmit = (sectionId: string, seats: SeatCreateRequest[]) => {
    setBulkSeatError(null)
    createSeatsMutation.mutate({ sectionId, seats })
  }

  return (
    <section className="page-surface admin-page">
      <div className="admin-page-header">
        <Typography.Title level={1}>
          {isEditMode ? 'Edit venue' : 'Create venue'}
        </Typography.Title>
        <Button icon={<ArrowLeftOutlined />}>
          <Link to={ROUTES.admin}>Back to venues</Link>
        </Button>
      </div>

      {formError ? (
        <Alert className="admin-inline-alert" message={formError} showIcon type="error" />
      ) : null}

      {venueQuery.isError ? (
        <Alert
          className="admin-inline-alert"
          message={apiErrorMessage(venueQuery.error, 'Could not load venue')}
          showIcon
          type="error"
        />
      ) : null}

      <Spin spinning={loadingVenue}>
        <section className="admin-section">
          <VenueForm
            initialValues={initialValues}
            submitLabel={isEditMode ? 'Save venue' : 'Create venue'}
            submitting={submittingVenue}
            onSubmit={handleVenueSubmit}
          />
        </section>
      </Spin>

      {isEditMode ? (
        <>
          <SectionManagement
            createError={sectionError}
            creating={createSectionMutation.isPending}
            layout={layoutQuery.data}
            layoutError={
              layoutQuery.isError
                ? apiErrorMessage(layoutQuery.error, 'Could not load sections')
                : null
            }
            loadingLayout={layoutQuery.isLoading}
            onAddSection={handleAddSection}
            onOpenBulkSeats={(section) => {
              setBulkSeatError(null)
              setBulkSeatSection(section)
            }}
          />

          <SeatLayoutPreview
            error={
              layoutQuery.isError
                ? apiErrorMessage(layoutQuery.error, 'Could not load seat layout')
                : seatAccessibilityError
            }
            layout={layoutQuery.data}
            loading={layoutQuery.isLoading}
            pendingSeatIds={pendingSeatIds}
            onToggleAccessible={(seat) => seatAccessibilityMutation.mutate(seat)}
          />

          <BulkSeatModal
            error={bulkSeatError}
            open={Boolean(bulkSeatSection)}
            section={bulkSeatSection}
            submitting={createSeatsMutation.isPending}
            onCancel={() => {
              if (!createSeatsMutation.isPending) {
                setBulkSeatError(null)
                setBulkSeatSection(null)
              }
            }}
            onSubmit={handleBulkSeatSubmit}
          />
        </>
      ) : null}
    </section>
  )
}
