package com.seatflow.order;

import java.util.List;

public record OrderPageResponse(
		List<OrderResponse> items,
		int page,
		int size,
		long totalItems,
		int totalPages) {

	public static OrderPageResponse from(List<OrderRecord> orders, int page, int size, long totalItems) {
		int totalPages = totalItems == 0 ? 0 : (int) Math.ceil((double) totalItems / size);
		return new OrderPageResponse(
				orders.stream().map(OrderResponse::from).toList(),
				page,
				size,
				totalItems,
				totalPages);
	}

	public OrderPageResponse {
		items = List.copyOf(items);
	}
}
