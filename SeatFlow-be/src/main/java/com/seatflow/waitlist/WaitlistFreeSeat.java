package com.seatflow.waitlist;

import java.util.UUID;

/** A seat that is on sale and not set aside for an offer; where it sits is used to keep a group together. */
public record WaitlistFreeSeat(UUID eventSeatId, UUID sectionId, String rowLabel, int seatNumber) {
}
