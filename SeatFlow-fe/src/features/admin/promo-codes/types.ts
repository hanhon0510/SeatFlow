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
