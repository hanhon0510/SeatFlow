import { useCallback, useEffect, useRef, type Dispatch, type SetStateAction } from 'react'
import { useQueryClient } from '@tanstack/react-query'

import { publicEventQueryKeys } from '../api/eventsApi'
import type { EventSeatLayout } from '../types'
import { createSeatUpdateConnection } from '../live/seatUpdateConnection'
import {
  applySeatStateUpdate,
  invalidSelectedSeatIds,
  type SeatStateUpdateMessage,
} from '../live/seatStateUpdates'

type NotificationConfig = {
  title: string
  description?: string
}

type SeatUpdateNotification = {
  info: (config: NotificationConfig) => void
  success: (config: NotificationConfig) => void
  warning: (config: NotificationConfig) => void
  error: (config: NotificationConfig) => void
}

type UseEventSeatUpdatesOptions = {
  eventId: string
  selectedSeatIds: string[]
  setSelectedSeatIds: Dispatch<SetStateAction<string[]>>
  notification: SeatUpdateNotification
  onSelectionInvalidated?: () => void
}

export function useEventSeatUpdates({
  eventId,
  selectedSeatIds,
  setSelectedSeatIds,
  notification,
  onSelectionInvalidated,
}: UseEventSeatUpdatesOptions) {
  const queryClient = useQueryClient()
  const selectedSeatIdsRef = useRef(selectedSeatIds)
  const notificationRef = useRef(notification)
  const onSelectionInvalidatedRef = useRef(onSelectionInvalidated)
  // A ref, not state: the server broadcasts the hold before answering the request that
  // created it, so the claim has to be readable before React commits another render.
  const heldByYouSeatIdsRef = useRef<ReadonlySet<string>>(new Set())

  const claimSeatsHeldByYou = useCallback((eventSeatIds: string[]) => {
    heldByYouSeatIdsRef.current = new Set(eventSeatIds)
  }, [])

  useEffect(() => {
    selectedSeatIdsRef.current = selectedSeatIds
  }, [selectedSeatIds])

  useEffect(() => {
    notificationRef.current = notification
  }, [notification])

  useEffect(() => {
    onSelectionInvalidatedRef.current = onSelectionInvalidated
  }, [onSelectionInvalidated])

  useEffect(() => {
    const queryKey = publicEventQueryKeys.seatLayout(eventId)
    const connection = createSeatUpdateConnection({
      eventId,
      onMessage: (message) => {
        if (message.eventId !== eventId) {
          return
        }

        const currentLayout = queryClient.getQueryData<EventSeatLayout>(queryKey)
        if (!currentLayout) {
          void queryClient.invalidateQueries({ queryKey })
          return
        }

        const heldByYouSeatIds = heldByYouSeatIdsRef.current
        const updatedLayout = applySeatStateUpdate(currentLayout, message, heldByYouSeatIds)
        queryClient.setQueryData(queryKey, updatedLayout)

        // The visitor's own hold already has its own confirmation; echoing it back as a seat
        // map update would only contradict it.
        if (message.eventSeatIds.every((eventSeatId) => heldByYouSeatIds.has(eventSeatId))) {
          return
        }

        const removedSeatIds = invalidSelectedSeatIds(updatedLayout, selectedSeatIdsRef.current)
        if (removedSeatIds.length > 0) {
          const removedSeatIdSet = new Set(removedSeatIds)
          setSelectedSeatIds((current) => current.filter((seatId) => !removedSeatIdSet.has(seatId)))
          onSelectionInvalidatedRef.current?.()
          notificationRef.current.warning({
            title: 'Selection updated',
            description: 'Unavailable seats were removed from your selection.',
          })
          return
        }

        showSeatUpdateNotification(message, notificationRef.current)
      },
      onReconnect: () => {
        void queryClient.refetchQueries({ queryKey, type: 'active' })
        notificationRef.current.info({
          title: 'Live updates reconnected',
          description: 'Seat map refreshed.',
        })
      },
      onDisconnect: () => {
        notificationRef.current.warning({
          title: 'Live updates disconnected',
          description: 'Reconnecting to seat updates.',
        })
      },
      onError: () => {
        notificationRef.current.error({
          title: 'Live updates error',
          description: 'Seat updates will retry automatically.',
        })
      },
    })

    return () => {
      void connection.deactivate()
    }
  }, [eventId, queryClient, setSelectedSeatIds])

  return { claimSeatsHeldByYou }
}

function showSeatUpdateNotification(
  message: SeatStateUpdateMessage,
  notification: SeatUpdateNotification,
) {
  if (message.type === 'SEATS_RELEASED') {
    notification.success({
      title: 'Seats released',
      description: 'The seat map was updated.',
    })
    return
  }

  if (message.type === 'SEATS_SOLD') {
    notification.warning({
      title: 'Seats sold',
      description: 'The seat map was updated.',
    })
    return
  }

  notification.info({
    title: 'Seats held',
    description: 'The seat map was updated.',
  })
}
