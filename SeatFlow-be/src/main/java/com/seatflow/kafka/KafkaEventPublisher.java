package com.seatflow.kafka;

import java.util.concurrent.CompletableFuture;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(prefix = "seatflow.kafka", name = "enabled", havingValue = "true")
public class KafkaEventPublisher {

	private final KafkaTemplate<Object, Object> kafkaTemplate;

	public KafkaEventPublisher(KafkaTemplate<Object, Object> kafkaTemplate) {
		this.kafkaTemplate = kafkaTemplate;
	}

	public CompletableFuture<SendResult<Object, Object>> publish(String topic, EventEnvelope<?> envelope) {
		return kafkaTemplate.send(topic, envelope.aggregateId().toString(), envelope);
	}
}
