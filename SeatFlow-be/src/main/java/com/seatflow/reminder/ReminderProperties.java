package com.seatflow.reminder;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @param enabled whether the background job that sends reminders runs; off by default so a fresh
 *        checkout never marks orders as reminded without really sending anything
 * @param defaultHoursBefore how long before the start reminders go out for events nobody configured
 * @param batchSize orders reminded per pass, so one pass stays short
 * @param fixedDelayMs pause between passes
 */
@ConfigurationProperties(prefix = "seatflow.reminders")
public record ReminderProperties(Boolean enabled, Integer defaultHoursBefore, Integer batchSize, Long fixedDelayMs) {

	public static final int MIN_HOURS = 1;
	public static final int MAX_HOURS = 336;

	public ReminderProperties {
		enabled = Boolean.TRUE.equals(enabled);
		defaultHoursBefore = defaultHoursBefore == null || defaultHoursBefore < MIN_HOURS
				|| defaultHoursBefore > MAX_HOURS ? 24 : defaultHoursBefore;
		batchSize = batchSize == null || batchSize < 1 ? 100 : batchSize;
		fixedDelayMs = fixedDelayMs == null || fixedDelayMs < 1000 ? 60_000L : fixedDelayMs;
	}

	public boolean isEnabled() {
		return Boolean.TRUE.equals(enabled);
	}
}
