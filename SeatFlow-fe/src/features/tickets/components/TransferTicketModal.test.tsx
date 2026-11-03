import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { TransferTicketModal } from './TransferTicketModal'

const api = vi.hoisted(() => ({ transferTicket: vi.fn() }))

vi.mock('../api/ticketsApi', async (importOriginal) => ({
  ...(await importOriginal<typeof import('../api/ticketsApi')>()),
  transferTicket: api.transferTicket,
}))

function renderModal(onTransferred = vi.fn()) {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  render(
    <QueryClientProvider client={queryClient}>
      <TransferTicketModal
        eventName="Opening Night"
        open
        ticketId="ticket-1"
        onClose={vi.fn()}
        onTransferred={onTransferred}
      />
    </QueryClientProvider>,
  )
  return onTransferred
}

describe('TransferTicketModal', () => {
  beforeEach(() => vi.clearAllMocks())

  it('sends the trimmed recipient email and reports success', async () => {
    api.transferTicket.mockResolvedValue(undefined)
    const onTransferred = renderModal()

    await userEvent.type(screen.getByLabelText(/recipient email/i), '  friend@example.com  ')
    await userEvent.click(screen.getByRole('button', { name: /transfer ticket/i }))

    await waitFor(() => expect(onTransferred).toHaveBeenCalled())
    expect(api.transferTicket).toHaveBeenCalledWith('ticket-1', 'friend@example.com')
  })

  it('shows an error and keeps the ticket when the transfer fails', async () => {
    api.transferTicket.mockRejectedValue(new Error('boom'))
    const onTransferred = renderModal()

    await userEvent.type(screen.getByLabelText(/recipient email/i), 'nobody@example.com')
    await userEvent.click(screen.getByRole('button', { name: /transfer ticket/i }))

    expect(await screen.findByText(/could not transfer the ticket/i)).toBeInTheDocument()
    expect(onTransferred).not.toHaveBeenCalled()
  })
})
