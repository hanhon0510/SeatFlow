package com.seatflow.event;

import java.util.List;

public record EventSalesOrdersResponse(
		EventSalesOrderCounts counts,
		List<EventSalesRecentOrderResponse> recent) {

	public EventSalesOrdersResponse {
		recent = List.copyOf(recent);
	}
}
