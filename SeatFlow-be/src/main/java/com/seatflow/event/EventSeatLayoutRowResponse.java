package com.seatflow.event;

import java.util.List;

public record EventSeatLayoutRowResponse(
		String rowLabel,
		List<EventSeatLayoutSeatResponse> seats) {
}
