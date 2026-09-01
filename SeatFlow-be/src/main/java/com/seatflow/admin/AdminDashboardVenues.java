package com.seatflow.admin;

public record AdminDashboardVenues(
		long total,
		long active,
		long archived,
		long sections,
		long seats) {
}
