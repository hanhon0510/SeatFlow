package com.seatflow.event;

import java.util.UUID;

public record EventPublishResponse(
		UUID eventId,
		EventStatus status,
		long inventoryCount) {
}
