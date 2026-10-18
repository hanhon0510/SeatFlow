package com.seatflow.notification;

import java.util.UUID;

public record OrderEmailSeat(UUID ticketId, String sectionName, String rowLabel, String seatLabel) {

	String description() {
		return "%s, row %s, seat %s".formatted(sectionName, rowLabel, seatLabel);
	}
}
