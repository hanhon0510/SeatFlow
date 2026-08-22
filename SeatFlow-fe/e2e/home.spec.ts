import { test, expect } from '@playwright/test'

test('home page loads', async ({ page }) => {
  await page.goto('/')
  await expect(page.getByRole('link', { name: /SeatFlow/ })).toBeVisible()
  await expect(page.getByRole('heading', { name: 'SeatFlow frontend is running.' })).toBeVisible()
})
