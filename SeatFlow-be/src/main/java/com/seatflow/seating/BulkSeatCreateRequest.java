package com.seatflow.seating;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

public record BulkSeatCreateRequest(@NotEmpty List<@Valid SeatCreateRequest> seats) {
}
