package com.seatflow.event;

import java.math.BigDecimal;
import java.util.UUID;

public record EventSeatLayoutRow(
		UUID sectionId,
		String sectionName,
		int sectionDisplayOrder,
		String rowLabel,
		UUID eventSeatId,
		String seatLabel,
		int seatNumber,
		BigDecimal price,
		EventSeatStatus permanentStatus,
		boolean accessible) {
}
