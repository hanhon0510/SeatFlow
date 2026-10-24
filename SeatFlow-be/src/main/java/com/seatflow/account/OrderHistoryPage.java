package com.seatflow.account;

import java.util.List;

public record OrderHistoryPage(
		List<OrderHistoryItem> items,
		int page,
		int size,
		long totalItems,
		int totalPages) {

	public OrderHistoryPage {
		items = List.copyOf(items);
	}
}
