package com.seatflow.event;

import java.time.Instant;
import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** {@code transfersAllowed} left out means allowed. */
public record EventCreateRequest(
		@NotNull UUID venueId,
		@NotBlank @Size(max = 180) String name,
		@Size(max = 4000) String description,
		@NotNull Instant startTime,
		@NotNull Instant salesStartTime,
		@NotNull Instant salesEndTime,
		Boolean transfersAllowed) {

	public EventCreateRequest(
			UUID venueId,
			String name,
			String description,
			Instant startTime,
			Instant salesStartTime,
			Instant salesEndTime) {
		this(venueId, name, description, startTime, salesStartTime, salesEndTime, null);
	}
}
