package com.seatflow.event;

public class InvalidEventCatalogQueryException extends RuntimeException {

	public InvalidEventCatalogQueryException() {
		super("Invalid event catalog query");
	}
}
