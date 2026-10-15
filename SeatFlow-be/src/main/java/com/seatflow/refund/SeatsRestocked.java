package com.seatflow.refund;

import java.util.List;
import java.util.UUID;

/** Published inside the refund transaction; acted on only once it has committed. */
public record SeatsRestocked(UUID eventId, List<UUID> eventSeatIds) {
}
