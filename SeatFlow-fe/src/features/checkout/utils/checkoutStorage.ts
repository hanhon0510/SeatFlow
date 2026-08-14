import type { CheckoutPaymentResult } from '../types'

type CheckoutDraft = {
  holdId: string
  reservationId?: string
  orderId?: string
  idempotencyKey: string
}

const draftPrefix = 'seatflow.checkout.draft'
const resultPrefix = 'seatflow.checkout.result'

export function getCheckoutDraft(holdId: string): CheckoutDraft {
  const existing = readJson<CheckoutDraft>(draftKey(holdId))
  if (existing?.holdId === holdId && existing.idempotencyKey) {
    return existing
  }

  const draft = {
    holdId,
    idempotencyKey: generateIdempotencyKey(),
  }
  writeJson(draftKey(holdId), draft)
  return draft
}

export function saveCheckoutDraft(draft: CheckoutDraft) {
  writeJson(draftKey(draft.holdId), draft)
}

export function savePaymentResult(result: CheckoutPaymentResult) {
  writeJson(resultKey(result.holdId), result)
}

export function getPaymentResult(holdId: string) {
  const result = readJson<CheckoutPaymentResult>(resultKey(holdId))
  return result?.holdId === holdId ? result : null
}

function draftKey(holdId: string) {
  return `${draftPrefix}.${holdId}`
}

function resultKey(holdId: string) {
  return `${resultPrefix}.${holdId}`
}

function generateIdempotencyKey() {
  if (crypto.randomUUID) {
    return crypto.randomUUID()
  }
  const bytes = new Uint8Array(16)
  crypto.getRandomValues(bytes)
  return Array.from(bytes, (byte) => byte.toString(16).padStart(2, '0')).join('')
}

function readJson<T>(key: string) {
  try {
    const value = window.sessionStorage.getItem(key)
    return value ? (JSON.parse(value) as T) : null
  }
  catch {
    return null
  }
}

function writeJson<T>(key: string, value: T) {
  window.sessionStorage.setItem(key, JSON.stringify(value))
}
