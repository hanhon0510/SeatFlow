package com.seatflow.waitlist;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @param enabled whether the background job that offers freed seats runs; off by default so a
 *        fresh checkout never tells anyone seats are waiting for them without sending an email
 * @param offerWindow how long offered seats are kept for the person they were offered to
 * @param batchSize events looked at per pass, so one pass stays short
 * @param scanLimit free seats considered per event per pass
 * @param fixedDelayMs pause between passes
 */
@ConfigurationProperties(prefix = "seatflow.waitlist")
public record WaitlistProperties(
		Boolean enabled, Duration offerWindow, Integer batchSize, Integer scanLimit, Long fixedDelayMs) {

	private static final Duration DEFAULT_OFFER_WINDOW = Duration.ofMinutes(10);

	public WaitlistProperties {
		enabled = Boolean.TRUE.equals(enabled);
		if (offerWindow == null) {
			offerWindow = DEFAULT_OFFER_WINDOW;
		}
		if (offerWindow.isZero() || offerWindow.isNegative()) {
			throw new IllegalStateException("Waitlist offer window must be positive");
		}
		batchSize = batchSize == null || batchSize < 1 ? 50 : batchSize;
		scanLimit = scanLimit == null || scanLimit < 1 ? 500 : scanLimit;
		fixedDelayMs = fixedDelayMs == null || fixedDelayMs < 1000 ? 15_000L : fixedDelayMs;
	}
}
