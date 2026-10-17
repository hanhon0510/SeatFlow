package com.seatflow.checkin;

/**
 * What a scan means for the person at the door. Every outcome is an ordinary answer to "may this
 * holder come in?", so all of them are returned as a normal response rather than as an error.
 */
public enum CheckInOutcome {

	/** First valid scan: the holder is admitted and the ticket is now used. */
	CHECKED_IN,
	/** The ticket was already scanned; the holder must not be admitted a second time. */
	ALREADY_USED,
	/** The ticket was voided, for example because its order was refunded. */
	CANCELLED,
	/** A genuine ticket, but for another event. */
	WRONG_EVENT,
	/** Nothing in the system matches what was scanned. */
	NOT_FOUND
}
