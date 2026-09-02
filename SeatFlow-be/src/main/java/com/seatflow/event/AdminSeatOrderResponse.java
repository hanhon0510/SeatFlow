package com.seatflow.event;

import java.time.Instant;
import java.util.UUID;

import com.seatflow.order.OrderStatus;

/**
 * Who took one seat off the market. Sent flat and keyed by event seat rather than nested into
 * the layout: only a minority of seats have an order, and the layout tree is shared with the
 * buyer-facing map, which must never carry a buyer's identity.
 */
public record AdminSeatOrderResponse(
		UUID eventSeatId,
		UUID orderId,
		String buyerEmail,
		OrderStatus orderStatus,
		Instant orderedAt) {
}
