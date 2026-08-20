import type { IMessage, IFrame, StompConfig, StompSubscription } from '@stomp/stompjs'
import { describe, expect, it, vi } from 'vitest'

import { createSeatUpdateConnection } from './seatUpdateConnection'
import { seatStateDestination, type SeatStateUpdateMessage } from './seatStateUpdates'

const eventId = '8fb3eb5f-9a73-45d8-8494-ffb98a3137d2'

describe('seat update connection', () => {
  it('subscribes and forwards parsed messages', () => {
    const setup = createConnection()
    setup.connect()
    const update: SeatStateUpdateMessage = {
      type: 'SEATS_HELD',
      eventId,
      eventSeatIds: ['8a58df81-409e-4f2d-bf7b-2270c35b9087'],
    }

    setup.message(update)

    expect(setup.client.activate).toHaveBeenCalledOnce()
    expect(setup.client.subscribe).toHaveBeenCalledWith(seatStateDestination(eventId), expect.any(Function))
    expect(setup.onMessage).toHaveBeenCalledWith(update)
  })

  it('notifies reconnect after the first connection', () => {
    const setup = createConnection()

    setup.connect()
    setup.connect()

    expect(setup.onReconnect).toHaveBeenCalledOnce()
    expect(setup.subscription.unsubscribe).toHaveBeenCalledOnce()
  })

  it('cleans up subscription and websocket client', async () => {
    const onDisconnect = vi.fn()
    const setup = createConnection({ onDisconnect })
    setup.connect()

    await setup.connection.deactivate()
    setup.close()

    expect(setup.subscription.unsubscribe).toHaveBeenCalledOnce()
    expect(setup.client.deactivate).toHaveBeenCalledOnce()
    expect(onDisconnect).not.toHaveBeenCalled()
  })
})

function createConnection(options: { onDisconnect?: () => void } = {}) {
  let config: StompConfig | undefined
  const subscription = {
    id: 'event-seat-subscription',
    unsubscribe: vi.fn(),
  } as unknown as StompSubscription
  const client = {
    activate: vi.fn(),
    deactivate: vi.fn().mockResolvedValue(undefined),
    subscribe: vi.fn((destination: string, callback: (message: IMessage) => void) => {
      void destination
      void callback
      return subscription
    }),
  }
  const onMessage = vi.fn()
  const onReconnect = vi.fn()
  const connection = createSeatUpdateConnection({
    eventId,
    onMessage,
    onReconnect,
    onDisconnect: options.onDisconnect,
    clientFactory: (nextConfig) => {
      config = nextConfig
      return client
    },
  })

  return {
    client,
    connection,
    subscription,
    onMessage,
    onReconnect,
    connect: () => {
      config?.onConnect?.({} as IFrame)
    },
    close: () => {
      config?.onWebSocketClose?.({} as CloseEvent)
    },
    message: (message: SeatStateUpdateMessage) => {
      const callback = client.subscribe.mock.calls.at(-1)?.[1]
      callback?.({ body: JSON.stringify(message) } as IMessage)
    },
  }
}
