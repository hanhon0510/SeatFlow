package com.seatflow.account;

import java.util.UUID;

import com.seatflow.ticket.TicketStatus;

/** A seat of an order. The ticket is absent while the order has not been paid. */
record OrderHistorySeatRow(
		UUID orderId,
		UUID ticketId,
		TicketStatus ticketStatus,
		String sectionName,
		String rowLabel,
		String seatLabel) {
}
