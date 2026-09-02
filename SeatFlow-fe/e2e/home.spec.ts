import { test, expect } from '@playwright/test'

test('home page redirects to the event catalogue', async ({ page }) => {
  await page.goto('/')
  await expect(page.getByRole('link', { name: /SeatFlow/ })).toBeVisible()
  await expect(page).toHaveURL(/\/events$/)
  await expect(page.getByRole('heading', { name: 'Events' })).toBeVisible()
})
