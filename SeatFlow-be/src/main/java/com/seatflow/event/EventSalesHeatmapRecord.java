package com.seatflow.event;

import java.util.UUID;

/**
 * One section/row aggregate straight out of the database. The section identity rides along on
 * every row because the query groups flat; the service folds these into sections before they
 * leave, so the wire shape stays nested.
 */
public record EventSalesHeatmapRecord(
		UUID sectionId,
		String sectionName,
		int displayOrder,
		String rowLabel,
		long seatsTotal,
		long seatsAvailable,
		long seatsSold,
		long seatsBlocked) {
}
