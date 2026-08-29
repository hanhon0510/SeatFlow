package com.seatflow.maintenance;

public record MaintenanceSummary(
		int expiredReservations,
		int closedOrders,
		int purgedIdempotencyRecords,
		int purgedRefreshTokens,
		int purgedOutboxEvents,
		int purgedProcessedEvents) {

	public boolean isEmpty() {
		return expiredReservations == 0
				&& closedOrders == 0
				&& purgedIdempotencyRecords == 0
				&& purgedRefreshTokens == 0
				&& purgedOutboxEvents == 0
				&& purgedProcessedEvents == 0;
	}
}
