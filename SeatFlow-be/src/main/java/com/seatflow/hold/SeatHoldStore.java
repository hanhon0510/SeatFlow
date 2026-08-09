package com.seatflow.hold;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public interface SeatHoldStore {

	boolean createHold(SeatHoldRecord hold, Duration ttl);

	Optional<SeatHoldRecord> findHold(UUID holdId);

	boolean isHoldActive(SeatHoldRecord hold);

	void releaseHold(SeatHoldRecord hold);

	Map<UUID, UUID> findActiveSeatHoldOwners(UUID eventId, List<UUID> eventSeatIds);
}
