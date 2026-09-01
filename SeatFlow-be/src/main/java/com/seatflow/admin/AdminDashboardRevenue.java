package com.seatflow.admin;

import java.math.BigDecimal;

public record AdminDashboardRevenue(
		String currency,
		BigDecimal amount,
		long orderCount) {
}
