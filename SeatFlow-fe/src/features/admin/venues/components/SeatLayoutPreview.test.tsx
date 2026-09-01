import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'

import type { Seat, SeatLayout } from '../types'
import { SeatLayoutPreview } from './SeatLayoutPreview'

const sectionId = 'f5936746-4e3c-4e50-a64d-a0d45f3d3861'
const accessibleSeatId = '8a58df81-409e-4f2d-bf7b-2270c35b9087'
const standardSeatId = '868af2d5-42c2-4ea4-8406-87137214ca2a'

describe('SeatLayoutPreview', () => {
  it('labels each seat with its accessibility and the action that changes it', () => {
    render(<SeatLayoutPreview error={null} layout={layout()} loading={false} onToggleAccessible={vi.fn()} />)

    expect(
      screen.getByRole('button', {
        name: 'Seat A1, wheelchair accessible. Select to mark it standard.',
      }),
    ).toHaveAttribute('aria-pressed', 'true')
    expect(
      screen.getByRole('button', {
        name: 'Seat A2, standard seat. Select to mark it wheelchair accessible.',
      }),
    ).toHaveAttribute('aria-pressed', 'false')
  })

  it('toggles a single seat rather than the whole row', async () => {
    const onToggleAccessible = vi.fn()
    render(
      <SeatLayoutPreview
        error={null}
        layout={layout()}
        loading={false}
        onToggleAccessible={onToggleAccessible}
      />,
    )

    await userEvent.click(screen.getByRole('button', { name: /Seat A2/ }))

    expect(onToggleAccessible).toHaveBeenCalledOnce()
    expect(onToggleAccessible).toHaveBeenCalledWith(expect.objectContaining({ id: standardSeatId }))
  })

  it('disables a seat while its change is in flight', () => {
    render(
      <SeatLayoutPreview
        error={null}
        layout={layout()}
        loading={false}
        pendingSeatIds={[accessibleSeatId]}
        onToggleAccessible={vi.fn()}
      />,
    )

    expect(screen.getByRole('button', { name: /Seat A1/ })).toBeDisabled()
    expect(screen.getByRole('button', { name: /Seat A2/ })).toBeEnabled()
  })

  it('counts the accessible seats in the section', () => {
    render(<SeatLayoutPreview error={null} layout={layout()} loading={false} onToggleAccessible={vi.fn()} />)

    expect(screen.getByText('1 accessible')).toBeInTheDocument()
    expect(screen.getByText('2 seats')).toBeInTheDocument()
  })

  it('renders read-only seats when no handler is supplied', () => {
    render(<SeatLayoutPreview error={null} layout={layout()} loading={false} />)

    expect(screen.queryByRole('button', { name: /Seat A1/ })).not.toBeInTheDocument()
    expect(screen.getByLabelText('Seat A1, wheelchair accessible')).toBeInTheDocument()
  })
})

function layout(): SeatLayout {
  return {
    venueId: '14b239a1-0c3f-4755-8b74-cfa8a0b00000',
    sections: [
      {
        id: sectionId,
        venueId: '14b239a1-0c3f-4755-8b74-cfa8a0b00000',
        name: 'Orchestra',
        displayOrder: 1,
        createdAt: '2026-08-20T00:00:00Z',
        seats: [seat(accessibleSeatId, 'A', 1, true), seat(standardSeatId, 'A', 2, false)],
      },
    ],
  }
}

function seat(id: string, rowLabel: string, seatNumber: number, accessible: boolean): Seat {
  return {
    id,
    sectionId,
    rowLabel,
    seatNumber,
    seatLabel: `${rowLabel}${seatNumber}`,
    accessible,
    createdAt: '2026-08-20T00:00:00Z',
  }
}
