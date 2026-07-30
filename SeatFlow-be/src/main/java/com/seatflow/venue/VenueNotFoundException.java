package com.seatflow.venue;

public class VenueNotFoundException extends RuntimeException {

	public VenueNotFoundException() {
		super("Venue not found");
	}
}
