import type { Dayjs } from 'dayjs'

export type PromoCodeCharacterSet = 'LETTERS' | 'NUMBERS' | 'ALPHANUMERIC'

export type PromoCodeGenerationRequest = {
  length: number
  characterSet: PromoCodeCharacterSet
}

export type PromoCodeGenerationResponse = {
  code: string
}

export type PromoCodeGeneratorValues = PromoCodeGenerationRequest & {
  code: string
}

export type PromoDiscountType = 'PERCENT' | 'FIXED'

export type PromoCode = {
  id: string
  code: string
  discountType: PromoDiscountType
  discountValue: number
  eventId: string | null
  expiresAt: string | null
  maxUses: number | null
  usedCount: number
  active: boolean
  createdAt: string
  updatedAt: string
}

export type PromoCodePage = {
  items: PromoCode[]
  page: number
  size: number
  totalItems: number
  totalPages: number
}

export type PromoCodeCreateRequest = {
  code: string
  discountType: PromoDiscountType
  discountValue: number
  eventId?: string | null
  expiresAt?: string | null
  maxUses?: number | null
}

export type PromoCodeFormValues = {
  code: string
  discountType: PromoDiscountType
  discountValue: number
  eventId?: string
  expiresAt?: Dayjs
  maxUses?: number
}
