package com.seatflow.outbox;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "seatflow.outbox")
public record OutboxProperties(Publisher publisher) {

	public OutboxProperties {
		publisher = publisher == null ? Publisher.defaults() : publisher.withDefaults();
	}

	public record Publisher(
			boolean enabled,
			int batchSize,
			Duration retryDelay,
			Duration retryMaxDelay,
			Duration publishTimeout) {

		private static Publisher defaults() {
			return new Publisher(false, 50, Duration.ofSeconds(30), Duration.ofMinutes(5), Duration.ofSeconds(10));
		}

		private Publisher withDefaults() {
			Publisher defaults = defaults();
			return new Publisher(
					enabled,
					batchSize > 0 ? batchSize : defaults.batchSize,
					positive(retryDelay) ? retryDelay : defaults.retryDelay,
					positive(retryMaxDelay) ? retryMaxDelay : defaults.retryMaxDelay,
					positive(publishTimeout) ? publishTimeout : defaults.publishTimeout);
		}
	}

	private static boolean positive(Duration duration) {
		return duration != null && !duration.isNegative() && !duration.isZero();
	}
}
