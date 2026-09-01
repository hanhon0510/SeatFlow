package com.seatflow.admin;

public record AdminDashboardEvents(
		long total,
		long draft,
		long published,
		long cancelled,
		long completed,
		long onSaleNow,
		long startingSoon) {
}
