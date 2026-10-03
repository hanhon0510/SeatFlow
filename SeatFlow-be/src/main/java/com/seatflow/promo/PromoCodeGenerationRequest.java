package com.seatflow.promo;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record PromoCodeGenerationRequest(
		@NotNull @Min(PromoCodeGenerator.MIN_LENGTH) @Max(PromoCodeGenerator.MAX_LENGTH) Integer length,
		@NotNull PromoCodeCharacterSet characterSet) {
}
