package com.seatflow.seating;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public record SectionUpdateRequest(
		@NotBlank @Size(max = 120) String name,
		@NotNull @PositiveOrZero Integer displayOrder) {
}
