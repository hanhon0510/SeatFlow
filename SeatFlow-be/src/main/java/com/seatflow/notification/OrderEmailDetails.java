package com.seatflow.notification;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** What an order email says about the order, the event and where it takes place. */
public record OrderEmailDetails(
		UUID orderId,
		String buyerEmail,
		UUID eventId,
		String eventName,
		Instant eventStartTime,
		String venueName,
		String venueAddress,
		String venueCity,
		String venueTimezone,
		BigDecimal totalAmount,
		BigDecimal discountAmount,
		String promoCode,
		String currency,
		boolean eventCancelled,
		String cancellationReason) {
}
