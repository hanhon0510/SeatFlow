package com.seatflow.notification;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import com.seatflow.kafka.EventEnvelope;
import com.seatflow.observability.BusinessMetrics;

/**
 * Reads the order events topic in a consumer group of its own, so a slow or failing mail server
 * never delays the analytics consumer, and each group keeps its own place in the topic.
 */
@Component
@ConditionalOnProperty(prefix = "seatflow.kafka", name = "enabled", havingValue = "true")
public class OrderEmailKafkaListener {

	private final OrderEmailNotificationConsumer consumer;
	private final BusinessMetrics businessMetrics;

	public OrderEmailKafkaListener(OrderEmailNotificationConsumer consumer, BusinessMetrics businessMetrics) {
		this.consumer = consumer;
		this.businessMetrics = businessMetrics;
	}

	@KafkaListener(
			id = "order-email-notifications",
			topics = "${seatflow.kafka.topics.order-events}",
			groupId = "${seatflow.kafka.consumer-groups.notification-events}",
			containerFactory = "kafkaListenerContainerFactory")
	public void handle(EventEnvelope<?> event) {
		try {
			consumer.process(event);
		}
		catch (RuntimeException ex) {
			businessMetrics.kafkaConsumerFailure();
			throw ex;
		}
	}
}
