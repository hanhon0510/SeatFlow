package com.seatflow.seating;

import jakarta.validation.constraints.NotNull;

public record SeatUpdateRequest(
		@NotNull Boolean accessible) {
}
