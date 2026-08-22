import { expect, test } from '@playwright/test'

import {
  accountFor,
  adminAccount,
  appBaseUrl,
  continueToCheckout,
  createDraftEventWithPricing,
  createHold,
  ensureAccount,
  getPublicSeatLayout,
  loginForToken,
  loginThroughUi,
  openEventFromCatalog,
  registerThroughUi,
  seatLayout,
  seedNames,
  seedPublishedEvent,
  selectSeat,
  waitForBackend,
} from './helpers/seatflow'

let adminToken = ''

test.beforeAll(async ({ request }) => {
  await waitForBackend(request)
  adminToken = await loginForToken(request, adminAccount(), 'admin')
})

test('admin creates venue seats and publishes an event', async ({ page, request }, testInfo) => {
  const names = seedNames(testInfo, 'admin')

  await loginThroughUi(page, adminAccount())
  await page.goto('/admin')
  await expect(page.getByRole('heading', { name: 'Venues' })).toBeVisible()
  await page.getByRole('link', { name: 'Create venue' }).click()

  await page.getByLabel('Venue name').fill(names.venueName)
  await page.getByLabel('Address').fill('100 Market Street')
  await page.getByLabel('City').fill('San Francisco')
  await page.getByLabel('Country').fill('US')
  await page.getByRole('button', { name: 'Create venue' }).click()

  await expect(page).toHaveURL(/\/admin\/venues\/[0-9a-f-]+\/edit$/i)
  const venueId = new URL(page.url()).pathname.split('/').at(-2)
  expect(venueId).toBeTruthy()

  await page.getByLabel('Section name').fill(names.sectionName)
  await page.getByLabel('Display order').fill('0')
  await page.getByRole('button', { name: 'Add section' }).click()
  await expect(page.getByText(names.sectionName)).toBeVisible()

  await page.getByRole('button', { name: `Add seats to ${names.sectionName}` }).click()
  await page.getByLabel('Row label').fill(names.rowLabel)
  await page.getByLabel('Starting seat number').fill('1')
  await page.getByLabel('Seat count').fill('3')
  await page.getByRole('button', { name: 'Create seats' }).click()
  await expect(page.getByLabel('Seat A1')).toBeVisible()
  await expect(page.getByLabel('Seat A3')).toBeVisible()

  const layout = await seatLayout(request, adminToken, venueId!)
  expect(layout.sections).toHaveLength(1)
  expect(layout.sections[0].seats).toHaveLength(3)

  const event = await createDraftEventWithPricing(
    request,
    adminToken,
    names,
    venueId!,
    [layout.sections[0].id],
  )

  await page.goto(`/admin/events/${event.id}/edit`)
  await expect(page.getByRole('heading', { name: 'Edit event' })).toBeVisible()
  await page.getByRole('button', { name: 'Publish event' }).click()

  const publishDialog = page.getByRole('dialog', { name: 'Publish event' })
  await expect(publishDialog).toBeVisible()
  await publishDialog.getByRole('button', { name: 'Publish event' }).click()
  await expect(page.getByText('PUBLISHED')).toBeVisible()

  const publicLayout = await getPublicSeatLayout(request, event.id)
  expect(publicLayout.sections.flatMap((section) => section.rows.flatMap((row) => row.seats))).toHaveLength(3)
})

test('purchase flow registers logs in pays and opens ticket', async ({ page, request }, testInfo) => {
  const names = seedNames(testInfo, 'purchase')
  const seed = await seedPublishedEvent(request, adminToken, names, 2)
  const buyer = accountFor(testInfo, 'buyer')
  const paymentPosts: string[] = []

  page.on('request', (browserRequest) => {
    const url = new URL(browserRequest.url())
    if (
      browserRequest.method() === 'POST'
      && /\/api\/v1\/orders\/[^/]+\/payments$/.test(url.pathname)
    ) {
      paymentPosts.push(browserRequest.url())
    }
  })

  await registerThroughUi(page, buyer)
  await loginThroughUi(page, buyer)
  await openEventFromCatalog(page, seed)
  await selectSeat(page, 'A1')
  await selectSeat(page, 'A2')
  await continueToCheckout(page)

  await expect(page.getByRole('heading', { name: 'Checkout' })).toBeVisible()
  await expect(page.getByText('Reservation summary')).toBeVisible()
  await expect(page.getByText('Payment simulator')).toBeVisible()
  await expect(page.getByText('A1')).toBeVisible()
  await expect(page.getByText('A2')).toBeVisible()

  await page.getByRole('button', { name: 'Pay now' }).dblclick()
  await expect(page).toHaveURL(/\/checkout\/[0-9a-f-]+\/result$/i)
  await expect(page.getByText('Payment succeeded')).toBeVisible()
  expect(paymentPosts).toHaveLength(1)

  await page.reload()
  await expect(page.getByText('Payment succeeded')).toBeVisible()
  await expect(page.getByText('Refreshing this page will not repeat the purchase.')).toBeVisible()
  expect(paymentPosts).toHaveLength(1)

  await page.getByRole('button', { name: 'Tickets' }).click()
  await expect(page).toHaveURL(/\/tickets$/)
  await expect(page.getByRole('heading', { name: 'Tickets' })).toBeVisible()
  await expect(page.getByRole('link', { name: seed.event.name }).first()).toBeVisible()

  await page.getByRole('link', { name: seed.event.name }).first().click()
  await expect(page.getByRole('heading', { name: seed.event.name })).toBeVisible()
  await expect(page.getByText('ACTIVE')).toBeVisible()
  await expect(page.getByText('Code')).toBeVisible()
  await expect(page.getByText('QR data')).toBeVisible()
  await expect(page.locator('.ticket-qr-panel svg')).toBeVisible()
})

test('second user loses a conflicting seat hold', async ({ browser, request }, testInfo) => {
  const names = seedNames(testInfo, 'conflict')
  const seed = await seedPublishedEvent(request, adminToken, names, 1)
  const firstUser = accountFor(testInfo, 'first-user')
  const secondUser = accountFor(testInfo, 'second-user')
  const firstContext = await browser.newContext({ baseURL: appBaseUrl() })
  const secondContext = await browser.newContext({ baseURL: appBaseUrl() })
  const firstPage = await firstContext.newPage()
  const secondPage = await secondContext.newPage()

  try {
    await registerThroughUi(firstPage, firstUser)
    await loginThroughUi(firstPage, firstUser)
    await registerThroughUi(secondPage, secondUser)
    await loginThroughUi(secondPage, secondUser)

    await openEventFromCatalog(firstPage, seed)
    await openEventFromCatalog(secondPage, seed)
    await selectSeat(firstPage, 'A1')
    await selectSeat(secondPage, 'A1')

    await continueToCheckout(firstPage)
    await expect(firstPage.getByRole('heading', { name: 'Checkout' })).toBeVisible()

    const secondContinue = secondPage.getByRole('button', { name: 'Continue' })
    if (await secondContinue.isEnabled()) {
      await secondContinue.click()
    }

    await expect(secondPage.getByText(/Some selected seats are no longer available|Unavailable seats were removed|No seats selected/i)).toBeVisible()
    await expect(secondPage.getByRole('button', { name: 'Continue' })).toBeDisabled()
  } finally {
    await firstContext.close()
    await secondContext.close()
  }
})

test('expired hold disables payment', async ({ page, request }, testInfo) => {
  const names = seedNames(testInfo, 'expiration')
  const seed = await seedPublishedEvent(request, adminToken, names, 1)
  const user = accountFor(testInfo, 'expiration-user')
  const userToken = await ensureAccount(request, user)
  const hold = await createHold(request, userToken, seed.event.id, [seed.eventSeats[0].eventSeatId])

  await page.clock.install({ time: new Date(Date.parse(hold.expiresAt) + 1000) })
  await loginThroughUi(page, user)
  await page.goto(`/checkout/${hold.holdId}`)
  await page.clock.runFor(1000)

  await expect(page.getByText('Hold expired')).toBeVisible()
  await expect(page.getByText('Payment is disabled because the server hold has expired.')).toBeVisible()
  await expect(page.getByRole('button', { name: 'Pay now' })).toBeDisabled()
})

test('user cannot access admin page', async ({ page }, testInfo) => {
  const user = accountFor(testInfo, 'plain-user')

  await registerThroughUi(page, user)
  await loginThroughUi(page, user)
  await page.goto('/admin')

  await expect(page.getByRole('heading', { name: '403' })).toBeVisible()
  await expect(page.getByText('You are not authorized to access this page.')).toBeVisible()
})
