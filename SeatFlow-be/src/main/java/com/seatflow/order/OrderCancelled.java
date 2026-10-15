package com.seatflow.order;

import java.util.UUID;

/** Published inside the cancellation transaction; the held seats are freed once it has committed. */
public record OrderCancelled(UUID holdId, UUID userId) {
}
