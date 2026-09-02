package com.seatflow.event;

import java.time.LocalDate;

/** One UTC day of paid demand, so an admin can see whether sales are still moving. */
public record EventSalesDailyPointResponse(
		LocalDate date,
		long paidOrders,
		long seatsSold) {
}
