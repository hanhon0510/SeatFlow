package com.seatflow.venue;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record VenueCreateRequest(
		@NotBlank @Size(max = 160) String name,
		@NotBlank @Size(max = 255) String address,
		@NotBlank @Size(max = 120) String city,
		@NotBlank @Size(max = 120) String country,
		@NotBlank @Size(max = 64) String timezone) {
}
