package com.seatflow.event;

import java.math.BigDecimal;
import java.util.UUID;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

public record EventSectionItemRequest(
		@NotNull UUID venueSectionId,
		@NotNull @DecimalMin("0.00") BigDecimal price,
		@NotNull Boolean salesEnabled) {
}
