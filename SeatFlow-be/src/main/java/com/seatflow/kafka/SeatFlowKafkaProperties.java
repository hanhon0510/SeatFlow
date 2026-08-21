package com.seatflow.kafka;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "seatflow.kafka")
public record SeatFlowKafkaProperties(
		boolean enabled,
		TopicNames topics,
		ConsumerGroups consumerGroups,
		Retry retry,
		Health health) {

	public SeatFlowKafkaProperties(boolean enabled, TopicNames topics, ConsumerGroups consumerGroups) {
		this(enabled, topics, consumerGroups, null, null);
	}

	public SeatFlowKafkaProperties(boolean enabled, TopicNames topics, ConsumerGroups consumerGroups, Retry retry) {
		this(enabled, topics, consumerGroups, retry, null);
	}

	public SeatFlowKafkaProperties {
		topics = topics == null ? TopicNames.defaults() : topics.withDefaults();
		consumerGroups = consumerGroups == null ? ConsumerGroups.defaults() : consumerGroups.withDefaults();
		retry = retry == null ? Retry.defaults() : retry.withDefaults();
		health = health == null ? Health.defaults() : health.withDefaults();
	}

	public record TopicNames(
			String orderEvents,
			String notificationEvents,
			String deadLetter) {

		public static TopicNames defaults() {
			return new TopicNames(
					"seatflow.order-events.v1",
					"seatflow.notification-events.v1",
					"seatflow.dead-letter.v1");
		}

		private TopicNames withDefaults() {
			TopicNames defaults = defaults();
			return new TopicNames(
					hasText(orderEvents) ? orderEvents : defaults.orderEvents,
					hasText(notificationEvents) ? notificationEvents : defaults.notificationEvents,
					hasText(deadLetter) ? deadLetter : defaults.deadLetter);
		}
	}

	public record ConsumerGroups(
			String orderEvents,
			String notificationEvents) {

		public static ConsumerGroups defaults() {
			return new ConsumerGroups(
					"seatflow.order-events.consumer.v1",
					"seatflow.notification-events.consumer.v1");
		}

		private ConsumerGroups withDefaults() {
			ConsumerGroups defaults = defaults();
			return new ConsumerGroups(
					hasText(orderEvents) ? orderEvents : defaults.orderEvents,
					hasText(notificationEvents) ? notificationEvents : defaults.notificationEvents);
		}
	}

	public record Retry(
			int maxAttempts,
			Duration backoff) {

		public static Retry defaults() {
			return new Retry(3, Duration.ofSeconds(1));
		}

		private Retry withDefaults() {
			Retry defaults = defaults();
			return new Retry(
					maxAttempts > 0 ? maxAttempts : defaults.maxAttempts,
					backoff != null && !backoff.isNegative() ? backoff : defaults.backoff);
		}
	}

	public record Health(Duration timeout) {

		public static Health defaults() {
			return new Health(Duration.ofSeconds(2));
		}

		private Health withDefaults() {
			Health defaults = defaults();
			return new Health(timeout != null && !timeout.isNegative() && !timeout.isZero()
					? timeout
					: defaults.timeout);
		}
	}

	private static boolean hasText(String value) {
		return value != null && !value.isBlank();
	}
}
