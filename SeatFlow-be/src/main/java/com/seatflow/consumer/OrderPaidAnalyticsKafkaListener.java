package com.seatflow.consumer;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import com.seatflow.kafka.EventEnvelope;
import com.seatflow.observability.BusinessMetrics;

/**
 * Entry point for the order events topic. Each event type has its own consumer; a type nobody
 * handles still reaches the paid consumer, which rejects it so it ends up on the dead-letter topic.
 */
@Component
@ConditionalOnProperty(prefix = "seatflow.kafka", name = "enabled", havingValue = "true")
public class OrderPaidAnalyticsKafkaListener {

	private final OrderPaidAnalyticsConsumer paidConsumer;
	private final OrderRefundedAnalyticsConsumer refundedConsumer;
	private final BusinessMetrics businessMetrics;

	public OrderPaidAnalyticsKafkaListener(
			OrderPaidAnalyticsConsumer paidConsumer,
			OrderRefundedAnalyticsConsumer refundedConsumer,
			BusinessMetrics businessMetrics) {
		this.paidConsumer = paidConsumer;
		this.refundedConsumer = refundedConsumer;
		this.businessMetrics = businessMetrics;
	}

	@KafkaListener(
			topics = "${seatflow.kafka.topics.order-events}",
			groupId = "${seatflow.kafka.consumer-groups.order-events}",
			containerFactory = "kafkaListenerContainerFactory")
	public void handle(EventEnvelope<?> event) {
		try {
			if (OrderRefundedAnalyticsConsumer.EVENT_TYPE.equals(event.eventType())) {
				refundedConsumer.process(event);
			}
			else {
				paidConsumer.process(event);
			}
		}
		catch (RuntimeException ex) {
			businessMetrics.kafkaConsumerFailure();
			throw ex;
		}
	}
}
