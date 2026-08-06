package com.seatflow.hold;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.junit.jupiter.api.Test;

class SeatHoldRedisKeysTests {

	@Test
	void buildsCanonicalSeatHoldKeys() {
		UUID eventId = UUID.fromString("5d2b80f6-4a0e-4f91-9676-e2c2d8b59e42");
		UUID eventSeatId = UUID.fromString("0ab96cb6-0b8b-4db4-855e-1bd12f3fc0e5");
		UUID holdId = UUID.fromString("0ffae24a-c058-4e4e-8783-b556a70e097e");
		UUID userId = UUID.fromString("c144397b-1b17-4a45-a1ef-b30ef84d5a79");

		assertThat(SeatHoldRedisKeys.seat(eventId, eventSeatId))
				.isEqualTo("seatflow:hold:seat:5d2b80f6-4a0e-4f91-9676-e2c2d8b59e42:0ab96cb6-0b8b-4db4-855e-1bd12f3fc0e5");
		assertThat(SeatHoldRedisKeys.data(holdId))
				.isEqualTo("seatflow:hold:data:0ffae24a-c058-4e4e-8783-b556a70e097e");
		assertThat(SeatHoldRedisKeys.user(userId))
				.isEqualTo("seatflow:hold:user:c144397b-1b17-4a45-a1ef-b30ef84d5a79");
	}
}
