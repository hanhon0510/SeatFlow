package com.seatflow.reminder;

import java.time.Instant;
import java.util.UUID;

public record EventReminderSettingsRecord(
		UUID eventId, boolean enabled, int hoursBefore, UUID updatedBy, Instant updatedAt) {
}
