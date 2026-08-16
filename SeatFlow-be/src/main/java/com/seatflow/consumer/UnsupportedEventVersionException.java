package com.seatflow.consumer;

public class UnsupportedEventVersionException extends IllegalArgumentException {

	public UnsupportedEventVersionException(String eventType, int eventVersion, int expectedEventVersion) {
		super("Unsupported %s event version %s, expected %s"
				.formatted(eventType, eventVersion, expectedEventVersion));
	}
}
