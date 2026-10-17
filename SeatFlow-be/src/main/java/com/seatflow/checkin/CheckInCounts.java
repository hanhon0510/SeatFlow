package com.seatflow.checkin;

/** Tickets that can still get someone in: cancelled ones are not part of the expected crowd. */
public record CheckInCounts(long valid, long checkedIn) {
}
