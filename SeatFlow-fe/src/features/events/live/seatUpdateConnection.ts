import { Client, type IMessage, type StompConfig, type StompSubscription } from '@stomp/stompjs'

import { parseSeatStateUpdate, seatStateDestination, type SeatStateUpdateMessage } from './seatStateUpdates'

type SeatUpdateClient = Pick<Client, 'activate' | 'deactivate' | 'subscribe'>

export type SeatUpdateClientFactory = (config: StompConfig) => SeatUpdateClient

export type SeatUpdateConnectionOptions = {
  eventId: string
  onMessage: (message: SeatStateUpdateMessage) => void
  onReconnect: () => void
  onDisconnect?: () => void
  onError?: () => void
  clientFactory?: SeatUpdateClientFactory
}

export type SeatUpdateConnection = {
  deactivate: () => Promise<void>
}

export function createSeatUpdateConnection(options: SeatUpdateConnectionOptions): SeatUpdateConnection {
  let connectedOnce = false
  let deactivating = false
  let subscription: StompSubscription | null = null
  const clientRef: { current?: SeatUpdateClient } = {}

  const config: StompConfig = {
    brokerURL: seatUpdatesBrokerUrl(),
    reconnectDelay: 3000,
    heartbeatIncoming: 10000,
    heartbeatOutgoing: 10000,
    debug: () => undefined,
    onConnect: () => {
      const currentClient = clientRef.current
      if (!currentClient) {
        return
      }
      subscription?.unsubscribe()
      subscription = currentClient.subscribe(seatStateDestination(options.eventId), (frame: IMessage) => {
        const message = parseSeatStateUpdate(frame.body)
        if (message) {
          options.onMessage(message)
        }
      })

      if (connectedOnce) {
        options.onReconnect()
      }
      connectedOnce = true
    },
    onStompError: () => {
      options.onError?.()
    },
    onWebSocketClose: () => {
      if (connectedOnce && !deactivating) {
        options.onDisconnect?.()
      }
    },
  }

  const client = options.clientFactory ? options.clientFactory(config) : new Client(config)
  clientRef.current = client
  client.activate()

  return {
    deactivate: async () => {
      deactivating = true
      subscription?.unsubscribe()
      await client.deactivate()
    },
  }
}

export function seatUpdatesBrokerUrl() {
  const configuredUrl = import.meta.env.VITE_SEATFLOW_WS_URL
  if (configuredUrl) {
    return configuredUrl
  }

  const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:'
  return `${protocol}//${window.location.host}/ws`
}
