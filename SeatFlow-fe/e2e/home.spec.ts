import { test, expect } from '@playwright/test'

test('home page loads', async ({ page }) => {
  await page.goto('/')
  await expect(page.getByText('SeatFlow')).toBeVisible()
  await expect(page.getByRole('heading', { name: 'Welcome' })).toBeVisible()
})
