import { expect, type APIRequestContext, type APIResponse, type Page, type TestInfo } from '@playwright/test'
import { readFileSync } from 'node:fs'
import { fileURLToPath } from 'node:url'

export type E2EAccount = {
  email: string
  password: string
}

type LoginResponse = {
  accessToken: string
}

type Venue = {
  id: string
  name: string
  timezone: string
}

type VenueSection = {
  id: string
  name: string
}

type Seat = {
  id: string
  rowLabel: string
  seatNumber: number
  seatLabel: string
}

type SeatLayout = {
  sections: Array<VenueSection & { seats: Seat[] }>
}

type Event = {
  id: string
  name: string
  venueId: string
  status: 'DRAFT' | 'PUBLISHED' | 'CANCELLED' | 'COMPLETED'
}

type EventPublishResponse = {
  eventId: string
  status: Event['status']
  inventoryCount: number
}

type EventSeatLayoutSeat = {
  eventSeatId: string
  seatLabel: string
  seatNumber: number
  price: number
}

type EventSeatLayout = {
  eventId: string
  sections: Array<{
    id: string
    name: string
    rows: Array<{
      rowLabel: string
      seats: EventSeatLayoutSeat[]
    }>
  }>
}

type SeatHold = {
  holdId: string
  eventId: string
  eventSeatIds: string[]
  expiresAt: string
}

export type SeedNames = {
  base: string
  eventName: string
  sectionName: string
  venueName: string
  rowLabel: string
}

export type PublishedEventSeed = {
  event: Event
  venue: Venue
  section: VenueSection
  seats: Seat[]
  eventSeats: EventSeatLayoutSeat[]
}

const eventStartTime = '2099-06-01T20:00:00.000Z'
const salesStartTime = '2024-01-01T00:00:00.000Z'
const salesEndTime = '2099-05-31T20:00:00.000Z'

let rootEnvCache: Record<string, string> | null = null

export function appBaseUrl() {
  return envValue('E2E_BASE_URL', 'http://localhost:5173')
}

export function apiBaseUrl() {
  return withoutTrailingSlash(envValue('E2E_API_BASE_URL', 'http://localhost:8080/api/v1'))
}

export function adminAccount(): E2EAccount {
  return {
    email: envValue('E2E_ADMIN_EMAIL', envValue('SEATFLOW_LOCAL_ADMIN_EMAIL', 'admin@example.com')),
    password: envValue('E2E_ADMIN_PASSWORD', envValue('SEATFLOW_LOCAL_ADMIN_PASSWORD', 'ChangeMeStrong123!')),
  }
}

export function accountFor(testInfo: TestInfo, role: string): E2EAccount {
  const namespace = seedNamespace().replace(/-/g, '.')
  const title = slug(testInfo.title).replace(/-/g, '.')
  const localPart = `${namespace}.${slug(role).replace(/-/g, '.')}.${title}`.slice(0, 64)

  return {
    email: `${localPart}@example.test`,
    password: envValue('E2E_USER_PASSWORD', generatedE2ePassword(namespace, role, title)),
  }
}

export function seedNames(testInfo: TestInfo, label: string): SeedNames {
  const base = `${seedNamespace()}-${slug(label)}-${slug(testInfo.title)}`.slice(0, 80)
  return {
    base,
    eventName: `Event ${base}`,
    sectionName: `Orchestra ${slug(label)}`,
    venueName: `Venue ${base}`,
    rowLabel: 'A',
  }
}

export async function waitForBackend(request: APIRequestContext) {
  const response = await request.get(apiUrl('/health/live'), { timeout: 10_000 })
  await expectOk(response, 'check backend liveness')
}

export async function loginForToken(
  request: APIRequestContext,
  account: E2EAccount,
  label = account.email,
) {
  const response = await request.post(apiUrl('/auth/login'), {
    data: account,
  })
  if (!response.ok()) {
    throw new Error(
      `${label} login failed with HTTP ${response.status()}. For admin setup, enable the backend local profile with SEATFLOW_LOCAL_ADMIN_ENABLED=true and matching admin credentials.`,
    )
  }
  const body = await response.json() as LoginResponse
  return body.accessToken
}

export async function ensureAccount(request: APIRequestContext, account: E2EAccount) {
  const registerResponse = await request.post(apiUrl('/auth/register'), {
    data: account,
  })

  if (![201, 409, 429].includes(registerResponse.status())) {
    await throwResponse(registerResponse, `register ${account.email}`)
  }

  return loginForToken(request, account)
}

export async function registerThroughUi(page: Page, account: E2EAccount) {
  await page.goto('/register')
  await page.getByLabel('Email').fill(account.email)
  await page.getByLabel('Password').fill(account.password)
  await page.getByRole('button', { name: 'Create account' }).click()

  await Promise.race([
    page.waitForURL(/\/login$/, { timeout: 10_000 }).catch(() => null),
    page.getByText(/Registration failed|User already exists/i).waitFor({ timeout: 10_000 }).catch(() => null),
  ])

  if (!new URL(page.url()).pathname.endsWith('/login')) {
    await page.goto('/login')
  }

  await expect(page.getByRole('heading', { name: 'Login' })).toBeVisible()
}

export async function loginThroughUi(page: Page, account: E2EAccount) {
  await page.goto('/login')
  await page.getByLabel('Email').fill(account.email)
  await page.getByLabel('Password').fill(account.password)
  await page.getByRole('button', { name: 'Log in' }).click()
  await expect(page).toHaveURL(/\/events$/)
}

export async function createVenueWithSeats(
  request: APIRequestContext,
  adminToken: string,
  names: SeedNames,
  seatCount: number,
) {
  const venue = await postJson<Venue>(request, '/admin/venues', adminToken, {
    name: names.venueName,
    address: '100 Market Street',
    city: 'San Francisco',
    country: 'US',
    timezone: 'America/Los_Angeles',
  }, 'create venue')
  const section = await postJson<VenueSection>(request, `/admin/venues/${venue.id}/sections`, adminToken, {
    name: names.sectionName,
    displayOrder: 0,
  }, 'create section')
  const seats = await postJson<Seat[]>(request, `/admin/sections/${section.id}/seats/bulk`, adminToken, {
    seats: Array.from({ length: seatCount }, (_, index) => {
      const seatNumber = index + 1
      return {
        rowLabel: names.rowLabel,
        seatNumber,
        seatLabel: `${names.rowLabel}${seatNumber}`,
        accessible: false,
      }
    }),
  }, 'create seats')

  return { venue, section, seats }
}

export async function createDraftEventWithPricing(
  request: APIRequestContext,
  adminToken: string,
  names: SeedNames,
  venueId: string,
  sectionIds: string[],
  price = 120_000,
) {
  const event = await postJson<Event>(request, '/admin/events', adminToken, {
    venueId,
    name: names.eventName,
    description: `Deterministic SF-046 event ${names.base}`,
    startTime: eventStartTime,
    salesStartTime,
    salesEndTime,
  }, 'create event')

  await putJson(request, `/admin/events/${event.id}/sections`, adminToken, {
    sections: sectionIds.map((venueSectionId) => ({
      venueSectionId,
      price,
      salesEnabled: true,
    })),
  }, 'price event sections')

  return event
}

export async function publishEvent(
  request: APIRequestContext,
  adminToken: string,
  eventId: string,
) {
  return postJson<EventPublishResponse>(
    request,
    `/admin/events/${eventId}/publish`,
    adminToken,
    undefined,
    'publish event',
  )
}

export async function seatLayout(
  request: APIRequestContext,
  adminToken: string,
  venueId: string,
) {
  return getJson<SeatLayout>(
    request,
    `/admin/venues/${venueId}/seat-layout`,
    adminToken,
    'load seat layout',
  )
}

export async function seedPublishedEvent(
  request: APIRequestContext,
  adminToken: string,
  names: SeedNames,
  seatCount: number,
) {
  const inventory = await createVenueWithSeats(request, adminToken, names, seatCount)
  const event = await createDraftEventWithPricing(
    request,
    adminToken,
    names,
    inventory.venue.id,
    [inventory.section.id],
  )
  const published = await publishEvent(request, adminToken, event.id)
  expect(published.status).toBe('PUBLISHED')
  expect(published.inventoryCount).toBe(seatCount)

  const layout = await getPublicSeatLayout(request, event.id)
  const eventSeats = flattenEventSeats(layout)
  expect(eventSeats).toHaveLength(seatCount)

  return {
    ...inventory,
    event: { ...event, status: 'PUBLISHED' as const },
    eventSeats,
  } satisfies PublishedEventSeed
}

export async function getPublicSeatLayout(request: APIRequestContext, eventId: string) {
  return getJson<EventSeatLayout>(request, `/events/${eventId}/seats`, null, 'load public seat layout')
}

export async function createHold(
  request: APIRequestContext,
  userToken: string,
  eventId: string,
  eventSeatIds: string[],
) {
  return postJson<SeatHold>(
    request,
    `/events/${eventId}/holds`,
    userToken,
    { eventSeatIds },
    'create hold',
  )
}

export async function openEventFromCatalog(page: Page, seed: PublishedEventSeed) {
  await page.goto('/events')
  await page.getByLabel('Search events').fill(seed.event.name)
  await page.getByLabel('Search events').press('Enter')

  const eventLink = page.locator(`a[href="/events/${seed.event.id}"]`, {
    hasText: seed.event.name,
  })
  await expect(eventLink).toBeVisible()
  await eventLink.click()
  await expect(page.getByRole('heading', { name: seed.event.name })).toBeVisible()
}

export async function selectSeat(page: Page, seatLabel: string) {
  const seatButton = page.getByRole('button', {
    name: new RegExp(`^Seat ${escapeRegExp(seatLabel)}, available`, 'i'),
  })
  await expect(seatButton).toBeEnabled()
  await seatButton.click()
  await expect(page.getByRole('button', {
    name: new RegExp(`^Seat ${escapeRegExp(seatLabel)}, selected`, 'i'),
  })).toBeVisible()
}

export async function continueToCheckout(page: Page) {
  await page.getByRole('button', { name: 'Continue' }).click()
  await expect(page).toHaveURL(/\/checkout\/[0-9a-f-]+$/i)
}

export function holdIdFromCheckoutUrl(page: Page) {
  const match = /\/checkout\/([^/]+)$/.exec(new URL(page.url()).pathname)
  if (!match) {
    throw new Error(`Current page is not a checkout URL: ${page.url()}`)
  }
  return match[1]
}

function apiUrl(path: string) {
  return `${apiBaseUrl()}${path.startsWith('/') ? path : `/${path}`}`
}

function authHeaders(token: string | null) {
  return token ? { Authorization: `Bearer ${token}` } : undefined
}

async function getJson<T>(
  request: APIRequestContext,
  path: string,
  token: string | null,
  action: string,
) {
  const response = await request.get(apiUrl(path), { headers: authHeaders(token) })
  return readJson<T>(response, action)
}

async function postJson<T>(
  request: APIRequestContext,
  path: string,
  token: string,
  data: unknown,
  action: string,
) {
  const response = await request.post(apiUrl(path), {
    headers: authHeaders(token),
    data,
  })
  return readJson<T>(response, action)
}

async function putJson(
  request: APIRequestContext,
  path: string,
  token: string,
  data: unknown,
  action: string,
) {
  const response = await request.put(apiUrl(path), {
    headers: authHeaders(token),
    data,
  })
  await expectOk(response, action)
}

async function readJson<T>(response: APIResponse, action: string) {
  await expectOk(response, action)
  return response.json() as Promise<T>
}

async function expectOk(response: APIResponse, action: string) {
  if (!response.ok()) {
    await throwResponse(response, action)
  }
}

async function throwResponse(response: APIResponse, action: string): Promise<never> {
  const body = await response.text().catch(() => '')
  throw new Error(`${action} failed with HTTP ${response.status()} ${response.statusText()}: ${body}`)
}

function flattenEventSeats(layout: EventSeatLayout) {
  return layout.sections.flatMap((section) =>
    section.rows.flatMap((row) => row.seats),
  )
}

function seedNamespace() {
  return slug(envValue('E2E_SEED_NAMESPACE', 'sf046'))
}

function generatedE2ePassword(namespace: string, role: string, title: string) {
  const base = `${namespace}-${slug(role)}-${title}`.slice(0, 80)
  return `E2e-${base}-A1!`
}

function envValue(key: string, fallback: string) {
  return process.env[key] ?? rootEnv()[key] ?? fallback
}

function rootEnv() {
  if (rootEnvCache) {
    return rootEnvCache
  }

  const envPath = fileURLToPath(new URL('../../../.env', import.meta.url))
  try {
    rootEnvCache = Object.fromEntries(
      readFileSync(envPath, 'utf8')
        .split(/\r?\n/)
        .map((line) => line.trim())
        .filter((line) => line && !line.startsWith('#'))
        .map((line) => {
          const separator = line.indexOf('=')
          if (separator === -1) {
            return ['', ''] as const
          }
          const key = line.slice(0, separator).trim()
          const value = line.slice(separator + 1).trim().replace(/^['"]|['"]$/g, '')
          return [key, value] as const
        })
        .filter(([key]) => key),
    )
  } catch {
    rootEnvCache = {}
  }

  return rootEnvCache
}

function slug(value: string) {
  return value
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, '-')
    .replace(/^-+|-+$/g, '')
    .slice(0, 48) || 'seed'
}

function withoutTrailingSlash(value: string) {
  return value.replace(/\/+$/, '')
}

function escapeRegExp(value: string) {
  return value.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')
}
