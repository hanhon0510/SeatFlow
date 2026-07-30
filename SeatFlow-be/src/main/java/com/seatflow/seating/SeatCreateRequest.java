package com.seatflow.seating;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record SeatCreateRequest(
		@NotBlank @Size(max = 32) String rowLabel,
		@NotNull @Positive Integer seatNumber,
		@NotBlank @Size(max = 64) String seatLabel,
		boolean accessible) {
}
