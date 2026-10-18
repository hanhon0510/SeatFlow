package com.seatflow.reminder;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record EventReminderRequest(
		@NotNull Boolean enabled,
		@NotNull @Min(ReminderProperties.MIN_HOURS) @Max(ReminderProperties.MAX_HOURS) Integer hoursBefore) {
}
