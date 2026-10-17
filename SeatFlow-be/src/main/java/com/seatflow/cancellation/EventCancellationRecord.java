package com.seatflow.cancellation;

import java.time.Instant;
import java.util.UUID;

public record EventCancellationRecord(UUID eventId, String reason, UUID cancelledBy, Instant cancelledAt) {
}
