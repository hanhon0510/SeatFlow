package com.seatflow.account;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.seatflow.order.OrderStatus;
import com.seatflow.ticket.TicketStatus;

/**
 * One order as the buyer sees it in their history: what they bought, what they paid, and, when
 * it ended in a refund, what they got back and why.
 *
 * @param refund null unless the order was refunded
 */
public record OrderHistoryItem(
		UUID orderId,
		OrderStatus status,
		BigDecimal totalAmount,
		BigDecimal discountAmount,
		String promoCode,
		String currency,
		Instant createdAt,
		Instant updatedAt,
		Event event,
		List<Seat> seats,
		Refund refund) {

	public OrderHistoryItem {
		seats = List.copyOf(seats);
	}

	/** @param cancelled the event was cancelled by the organiser, which is why a refund may exist */
	public record Event(
			UUID id,
			String name,
			Instant startTime,
			String venueName,
			String venueTimezone,
			boolean cancelled,
			String cancellationReason) {
	}

	/** @param ticketId null until the order is paid */
	public record Seat(UUID ticketId, TicketStatus ticketStatus, String sectionName, String rowLabel, String seatLabel) {
	}

	public record Refund(BigDecimal amount, String reason, Instant refundedAt) {
	}
}
