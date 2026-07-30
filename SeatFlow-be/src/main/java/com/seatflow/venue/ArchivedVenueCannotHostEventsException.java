package com.seatflow.venue;

public class ArchivedVenueCannotHostEventsException extends RuntimeException {

	public ArchivedVenueCannotHostEventsException() {
		super("Archived venue cannot host new events");
	}
}
