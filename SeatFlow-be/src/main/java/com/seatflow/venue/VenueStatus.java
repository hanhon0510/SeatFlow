package com.seatflow.venue;

public enum VenueStatus {
	ACTIVE,
	ARCHIVED;

	public boolean canHostNewEvents() {
		return this == ACTIVE;
	}
}
