package com.seatflow.kafka;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Headers;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.support.serializer.DeserializationException;

import com.seatflow.consumer.ConsumerSideEffectException;
import com.seatflow.consumer.UnsupportedEventVersionException;

class KafkaDeadLetterMetadataTests {

	private static final UUID EVENT_ID = UUID.fromString("49d62b68-ef93-4d14-aeca-67a1aa20f60e");
	private static final UUID AGGREGATE_ID = UUID.fromString("e4e49b16-7b6b-4f20-a1a7-47164b4e08da");
	private static final UUID CORRELATION_ID = UUID.fromString("ec515130-c85d-4f48-a8aa-7c95eeb067bd");

	@Test
	void headersIncludeOriginalEventMetadataAndPermanentCategory() {
		Headers headers = KafkaDeadLetterMetadata.headers(
				record(envelope()),
				new UnsupportedEventVersionException("OrderPaid", 2, 1));

		assertThat(header(headers, KafkaDeadLetterMetadata.ORIGINAL_EVENT_ID)).isEqualTo(EVENT_ID.toString());
		assertThat(header(headers, KafkaDeadLetterMetadata.ORIGINAL_TOPIC)).isEqualTo("seatflow.order-events.v1");
		assertThat(header(headers, KafkaDeadLetterMetadata.ORIGINAL_PARTITION)).isEqualTo("0");
		assertThat(header(headers, KafkaDeadLetterMetadata.CORRELATION_ID)).isEqualTo(CORRELATION_ID.toString());
		assertThat(header(headers, KafkaDeadLetterMetadata.ERROR_CATEGORY)).isEqualTo("PERMANENT");
	}

	@Test
	void transientFailureGetsSafeTransientCategory() {
		Headers headers = KafkaDeadLetterMetadata.headers(
				record(envelope()),
				new ConsumerSideEffectException("order-paid-analytics"));

		assertThat(header(headers, KafkaDeadLetterMetadata.ERROR_CATEGORY)).isEqualTo("TRANSIENT");
	}

	@Test
	void poisonFailureUsesUnknownEventMetadataWhenValueCannotDeserialize() {
		Headers headers = KafkaDeadLetterMetadata.headers(
				record(null),
				new DeserializationException("invalid json", new byte[] { 1, 2, 3 }, false,
						new IllegalArgumentException("invalid json")));

		assertThat(header(headers, KafkaDeadLetterMetadata.ORIGINAL_EVENT_ID))
				.isEqualTo(KafkaDeadLetterMetadata.UNKNOWN);
		assertThat(header(headers, KafkaDeadLetterMetadata.CORRELATION_ID))
				.isEqualTo(KafkaDeadLetterMetadata.UNKNOWN);
		assertThat(header(headers, KafkaDeadLetterMetadata.ERROR_CATEGORY)).isEqualTo("POISON");
	}

	private static ConsumerRecord<String, Object> record(Object value) {
		return new ConsumerRecord<>("seatflow.order-events.v1", 0, 42L, AGGREGATE_ID.toString(), value);
	}

	private static EventEnvelope<Map<String, String>> envelope() {
		return new EventEnvelope<>(
				EVENT_ID,
				"OrderPaid",
				1,
				AGGREGATE_ID,
				CORRELATION_ID,
				Instant.parse("2026-08-17T01:02:03Z"),
				Map.of("orderId", AGGREGATE_ID.toString()));
	}

	private static String header(Headers headers, String name) {
		return new String(headers.lastHeader(name).value(), StandardCharsets.UTF_8);
	}
}
