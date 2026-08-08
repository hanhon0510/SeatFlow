package com.seatflow.hold;

import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonIgnore;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;

public record SeatHoldRequest(
		UUID eventSeatId,
		List<@NotNull UUID> eventSeatIds) {

	public SeatHoldRequest(UUID eventSeatId) {
		this(eventSeatId, null);
	}

	@JsonIgnore
	@AssertTrue(message = "At least one seat is required")
	public boolean hasSeatSelection() {
		return eventSeatId != null || (eventSeatIds != null && !eventSeatIds.isEmpty());
	}

	@JsonIgnore
	public List<UUID> requestedEventSeatIds() {
		if (eventSeatIds != null && !eventSeatIds.isEmpty()) {
			return List.copyOf(eventSeatIds);
		}
		if (eventSeatId == null) {
			return List.of();
		}
		return List.of(eventSeatId);
	}
}
