package com.seatflow.kafka;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "seatflow.kafka")
public record SeatFlowKafkaProperties(
		boolean enabled,
		TopicNames topics,
		ConsumerGroups consumerGroups) {

	public SeatFlowKafkaProperties {
		topics = topics == null ? TopicNames.defaults() : topics.withDefaults();
		consumerGroups = consumerGroups == null ? ConsumerGroups.defaults() : consumerGroups.withDefaults();
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

	private static boolean hasText(String value) {
		return value != null && !value.isBlank();
	}
}
