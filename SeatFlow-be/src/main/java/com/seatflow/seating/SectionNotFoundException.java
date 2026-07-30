package com.seatflow.seating;

public class SectionNotFoundException extends RuntimeException {

	public SectionNotFoundException() {
		super("Section not found");
	}
}
