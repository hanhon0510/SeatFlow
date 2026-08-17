package com.seatflow.kafka;

import java.nio.charset.StandardCharsets;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Headers;
import org.apache.kafka.common.header.internals.RecordHeaders;
import org.springframework.kafka.support.serializer.DeserializationException;

final class KafkaDeadLetterMetadata {

	static final String ORIGINAL_EVENT_ID = "seatflow-original-event-id";
	static final String ORIGINAL_TOPIC = "seatflow-original-topic";
	static final String ORIGINAL_PARTITION = "seatflow-original-partition";
	static final String CORRELATION_ID = "seatflow-correlation-id";
	static final String ERROR_CATEGORY = "seatflow-error-category";
	static final String UNKNOWN = "unknown";

	private KafkaDeadLetterMetadata() {
	}

	static Headers headers(ConsumerRecord<?, ?> record, Exception exception) {
		RecordHeaders headers = new RecordHeaders();
		add(headers, ORIGINAL_EVENT_ID, eventId(record.value()));
		add(headers, ORIGINAL_TOPIC, record.topic());
		add(headers, ORIGINAL_PARTITION, Integer.toString(record.partition()));
		add(headers, CORRELATION_ID, correlationId(record.value()));
		add(headers, ERROR_CATEGORY, category(exception));
		return headers;
	}

	private static String eventId(Object value) {
		if (value instanceof EventEnvelope<?> event) {
			return event.eventId().toString();
		}
		return UNKNOWN;
	}

	private static String correlationId(Object value) {
		if (value instanceof EventEnvelope<?> event) {
			return event.correlationId().toString();
		}
		return UNKNOWN;
	}

	private static String category(Throwable exception) {
		if (hasCause(exception, DeserializationException.class)) {
			return "POISON";
		}
		if (hasCause(exception, IllegalArgumentException.class)) {
			return "PERMANENT";
		}
		return "TRANSIENT";
	}

	private static boolean hasCause(Throwable exception, Class<? extends Throwable> type) {
		Throwable current = exception;
		while (current != null) {
			if (type.isInstance(current)) {
				return true;
			}
			current = current.getCause();
		}
		return false;
	}

	private static void add(Headers headers, String name, String value) {
		headers.add(name, value.getBytes(StandardCharsets.UTF_8));
	}
}
