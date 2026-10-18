package com.seatflow.reminder;

import java.time.Instant;
import java.util.UUID;

/**
 * @param usingDefault true when nobody has configured this event, so the application default applies
 * @param remindersSent orders already reminded, one email per order
 * @param remindAt when reminders start going out, or null when they are off or the event has no start
 */
public record EventReminderResponse(
		UUID eventId,
		boolean enabled,
		int hoursBefore,
		boolean usingDefault,
		long remindersSent,
		Instant remindAt) {
}
