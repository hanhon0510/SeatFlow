package com.seatflow.consumer;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import com.seatflow.kafka.EventEnvelope;
import com.seatflow.observability.BusinessMetrics;

@Component
@ConditionalOnProperty(prefix = "seatflow.kafka", name = "enabled", havingValue = "true")
public class OrderPaidAnalyticsKafkaListener {

	private final OrderPaidAnalyticsConsumer consumer;
	private final BusinessMetrics businessMetrics;

	public OrderPaidAnalyticsKafkaListener(OrderPaidAnalyticsConsumer consumer, BusinessMetrics businessMetrics) {
		this.consumer = consumer;
		this.businessMetrics = businessMetrics;
	}

	@KafkaListener(
			topics = "${seatflow.kafka.topics.order-events}",
			groupId = "${seatflow.kafka.consumer-groups.order-events}",
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
