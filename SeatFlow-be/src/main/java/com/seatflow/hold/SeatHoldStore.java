package com.seatflow.hold;

import java.time.Duration;
import java.util.UUID;

public interface SeatHoldStore {

	boolean tryAcquireSeat(UUID eventId, UUID eventSeatId, UUID holdId, Duration ttl);

	void storeHold(SeatHoldRecord hold, Duration ttl);

	void releaseSeat(UUID eventId, UUID eventSeatId);
}
