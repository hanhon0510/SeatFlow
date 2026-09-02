package com.seatflow.event;

import java.math.BigDecimal;
import java.util.UUID;

/** How one priced section of the venue is selling for this event. */
public record EventSalesSectionResponse(
		UUID venueSectionId,
		String name,
		BigDecimal price,
		boolean salesEnabled,
		long seatsTotal,
		long seatsAvailable,
		long seatsSold,
		long seatsBlocked,
		BigDecimal soldValue) {
}
