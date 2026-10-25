package com.seatflow.waitlist;

import java.util.UUID;

/** A seat currently set aside for the person an offer was made to. */
public record WaitlistOfferedSeat(UUID eventSeatId, UUID userId) {
}
