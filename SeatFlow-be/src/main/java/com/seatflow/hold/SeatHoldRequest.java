package com.seatflow.hold;

import java.util.UUID;

import jakarta.validation.constraints.NotNull;

public record SeatHoldRequest(@NotNull UUID eventSeatId) {
}
