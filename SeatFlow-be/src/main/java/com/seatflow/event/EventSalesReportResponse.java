package com.seatflow.event;

import java.time.Instant;
import java.util.List;

public record EventSalesReportResponse(
		EventSalesEventResponse event,
		EventSalesInventoryResponse inventory,
		List<EventSalesRevenueResponse> revenue,
		EventSalesOrdersResponse orders,
		EventSalesTicketsResponse tickets,
		List<EventSalesSectionResponse> sections,
		List<EventSalesDailyPointResponse> dailySales,
		Instant generatedAt) {

	public EventSalesReportResponse {
		revenue = List.copyOf(revenue);
		sections = List.copyOf(sections);
		dailySales = List.copyOf(dailySales);
	}
}
