package com.seatflow.seatupdates;

import java.util.List;
import java.util.UUID;

public interface SeatStateNotifier {

	void seatsHeld(UUID eventId, List<UUID> eventSeatIds);

	void seatsReleased(UUID eventId, List<UUID> eventSeatIds);

	void seatsSold(UUID eventId, List<UUID> eventSeatIds);
}
