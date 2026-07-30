package com.seatflow.venue;

public class VenueAlreadyArchivedException extends RuntimeException {

	public VenueAlreadyArchivedException() {
		super("Venue is already archived");
	}
}
