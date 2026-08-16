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
			Duration publishTimeout) {

		private static Publisher defaults() {
			return new Publisher(false, 50, Duration.ofSeconds(30), Duration.ofSeconds(10));
		}

		private Publisher withDefaults() {
			Publisher defaults = defaults();
			return new Publisher(
					enabled,
					batchSize > 0 ? batchSize : defaults.batchSize,
					retryDelay != null ? retryDelay : defaults.retryDelay,
					publishTimeout != null ? publishTimeout : defaults.publishTimeout);
		}
	}
}
