package com.seatflow.venue;

public class InvalidVenueTimezoneException extends RuntimeException {

	public InvalidVenueTimezoneException(String timezone) {
		super("Invalid venue timezone: " + timezone);
	}
}
