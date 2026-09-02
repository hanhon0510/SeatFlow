package com.seatflow.event;

public record EventSalesTicketsResponse(
		long issued,
		long active,
		long used,
		long cancelled) {
}
