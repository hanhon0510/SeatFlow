package com.seatflow.analytics;

import java.math.BigDecimal;

public record AnalyticsCurrencyTotal(
		String currency,
		long orders,
		long seats,
		BigDecimal gross,
		BigDecimal refunded,
		long refundedOrders) {

	public BigDecimal net() {
		return gross.subtract(refunded);
	}
}
