package com.seatflow.cancellation;

import java.util.UUID;

/** A seat hold still backing a reservation that is waiting for payment. */
record OpenHold(UUID holdId, UUID userId) {
}
