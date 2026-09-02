package com.seatflow.event;

public record EventSalesOrderCounts(
		long total,
		long paid,
		long pending,
		long failed,
		long cancelled) {
}
