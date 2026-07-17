import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it } from 'vitest'
import App from './App'

describe('App', () => {
  it('renders the health page', () => {
    render(<App />)

    expect(screen.getByRole('heading', { name: 'SeatFlow frontend is running.' })).toBeInTheDocument()
  })

  it('navigates between placeholder routes', async () => {
    const user = userEvent.setup()
    render(<App />)

    await user.click(screen.getByRole('link', { name: /events/i }))

    expect(screen.getByRole('heading', { name: 'Events' })).toBeInTheDocument()
  })

  it('renders a 404 result for unknown routes', () => {
    window.history.pushState({}, '', '/missing-route')

    render(<App />)

    expect(screen.getByText('404')).toBeInTheDocument()
    expect(screen.getByText('Page not found')).toBeInTheDocument()
  })
})
