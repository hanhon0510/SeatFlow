package com.seatflow.event;

import java.time.Instant;
import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record EventCreateRequest(
		@NotNull UUID venueId,
		@NotBlank @Size(max = 180) String name,
		@Size(max = 4000) String description,
		@NotNull Instant startTime,
		@NotNull Instant salesStartTime,
		@NotNull Instant salesEndTime) {
}
