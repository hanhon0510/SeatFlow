package com.seatflow.hold;

import java.time.Duration;

public interface SeatHoldStore {

	boolean createHold(SeatHoldRecord hold, Duration ttl);
}
