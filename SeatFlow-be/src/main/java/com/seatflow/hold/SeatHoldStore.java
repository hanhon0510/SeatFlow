package com.seatflow.hold;

import java.time.Duration;
import java.util.Optional;

public interface SeatHoldStore {

	boolean createHold(SeatHoldRecord hold, Duration ttl);

	Optional<SeatHoldRecord> findHold(java.util.UUID holdId);

	boolean isHoldActive(SeatHoldRecord hold);

	void releaseHold(SeatHoldRecord hold);
}
