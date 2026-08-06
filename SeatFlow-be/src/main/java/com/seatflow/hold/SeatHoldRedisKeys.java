package com.seatflow.hold;

import java.util.UUID;

public final class SeatHoldRedisKeys {

	private static final String PREFIX = "seatflow:hold";

	private SeatHoldRedisKeys() {
	}

	public static String seat(UUID eventId, UUID eventSeatId) {
		return "%s:seat:%s:%s".formatted(PREFIX, eventId, eventSeatId);
	}

	public static String data(UUID holdId) {
		return "%s:data:%s".formatted(PREFIX, holdId);
	}

	public static String user(UUID userId) {
		return "%s:user:%s".formatted(PREFIX, userId);
	}
}
