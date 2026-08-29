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
			Duration publishTimeout,
			Duration claimLease,
			Duration passTimeout) {

		private static Publisher defaults() {
			return new Publisher(
					false,
					50,
					Duration.ofSeconds(30),
					Duration.ofMinutes(5),
					Duration.ofSeconds(10),
					Duration.ofMinutes(1),
					Duration.ofSeconds(30));
		}

		private Publisher withDefaults() {
			Publisher defaults = defaults();
			Duration resolvedPublishTimeout = positive(publishTimeout) ? publishTimeout : defaults.publishTimeout;
			Duration resolvedClaimLease = positive(claimLease) ? claimLease : defaults.claimLease;
			if (resolvedClaimLease.compareTo(resolvedPublishTimeout) <= 0) {
				throw new IllegalStateException(
						"Outbox claim-lease must exceed publish-timeout so a slow send cannot lose its claim");
			}
			return new Publisher(
					enabled,
					batchSize > 0 ? batchSize : defaults.batchSize,
					positive(retryDelay) ? retryDelay : defaults.retryDelay,
					positive(retryMaxDelay) ? retryMaxDelay : defaults.retryMaxDelay,
					resolvedPublishTimeout,
					resolvedClaimLease,
					positive(passTimeout) ? passTimeout : defaults.passTimeout);
		}
	}

	private static boolean positive(Duration duration) {
		return duration != null && !duration.isNegative() && !duration.isZero();
	}
}
