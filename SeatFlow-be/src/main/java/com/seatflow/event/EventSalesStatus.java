package com.seatflow.event;

/**
 * What a buyer can do with a published event right now. Derived from the clock on every read
 * rather than stored, because a stored column is stale the moment a sales window closes.
 */
public enum EventSalesStatus {

	/** Sales are open and at least one seat is still available. */
	ON_SALE,

	/** Published, but sales have not opened yet. */
	UPCOMING,

	/** Sales are open and every seat is sold or blocked. */
	SOLD_OUT,

	/** Sales closed before the event started. */
	SALES_CLOSED,

	/** The event has already started, so nothing can be bought for it. */
	ENDED
}
