package com.seatflow.admin;

import java.util.List;

public record AdminDashboardSales(
		long paidOrders,
		long pendingOrders,
		long ticketsIssued,
		long ticketsUsed,
		List<AdminDashboardRevenue> revenue) {

	public AdminDashboardSales {
		revenue = List.copyOf(revenue);
	}
}
