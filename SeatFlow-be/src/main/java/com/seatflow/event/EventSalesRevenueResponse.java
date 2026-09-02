package com.seatflow.event;

import java.math.BigDecimal;

/** Money taken for one event in one currency. Currencies are reported side by side, never summed. */
public record EventSalesRevenueResponse(
		String currency,
		BigDecimal paidAmount,
		long paidOrders,
		BigDecimal pendingAmount,
		long pendingOrders) {
}
