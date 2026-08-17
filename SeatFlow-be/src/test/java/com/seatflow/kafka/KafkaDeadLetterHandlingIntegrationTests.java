package com.seatflow.kafka;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Headers;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.support.KafkaHeaders;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.seatflow.outbox.OrderPaidPayload;
import com.seatflow.support.KafkaTestContainerSupport;

@SpringBootTest(properties = {
		"seatflow.kafka.retry.max-attempts=3",
		"seatflow.kafka.retry.backoff=10ms"
})
@Testcontainers(disabledWithoutDocker = true)
class KafkaDeadLetterHandlingIntegrationTests extends KafkaTestContainerSupport {

	@Autowired
	private KafkaEventPublisher publisher;

	@Autowired
	private SeatFlowKafkaProperties kafkaProperties;

	@Autowired
	private ConsumerFactory<Object, Object> consumerFactory;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@BeforeEach
	void setUp() {
		cleanDatabase();
	}

	@AfterEach
	void tearDown() {
		dropTransientFailureTrigger();
		cleanDatabase();
	}

	@Test
	void permanentFailureReachesDeadLetterWithMetadataAndConsumerContinues() throws Exception {
		UUID badEventId = UUID.randomUUID();
		UUID validEventId = UUID.randomUUID();
		UUID orderId = UUID.randomUUID();
		UUID correlationId = UUID.randomUUID();

		try (Consumer<Object, Object> deadLetterConsumer =
				consumer("seatflow-dlt-permanent-test-" + UUID.randomUUID())) {
			deadLetterConsumer.subscribe(List.of(kafkaProperties.topics().deadLetter()));
			publisher.publish(kafkaProperties.topics().orderEvents(),
					envelope(badEventId, orderId, correlationId, 2)).get();

			ConsumerRecord<Object, Object> deadLetter = readDeadLetter(deadLetterConsumer, badEventId);
			Headers headers = deadLetter.headers();
			assertThat(header(headers, KafkaDeadLetterMetadata.ORIGINAL_EVENT_ID)).isEqualTo(badEventId.toString());
			assertThat(header(headers, KafkaDeadLetterMetadata.ORIGINAL_TOPIC))
					.isEqualTo(kafkaProperties.topics().orderEvents());
			assertThat(header(headers, KafkaDeadLetterMetadata.ORIGINAL_PARTITION)).isEqualTo("0");
			assertThat(header(headers, KafkaDeadLetterMetadata.CORRELATION_ID)).isEqualTo(correlationId.toString());
			assertThat(header(headers, KafkaDeadLetterMetadata.ERROR_CATEGORY)).isEqualTo("PERMANENT");
			assertThat(headers.lastHeader(KafkaHeaders.DLT_EXCEPTION_STACKTRACE)).isNull();

			publisher.publish(kafkaProperties.topics().orderEvents(),
					envelope(validEventId, UUID.randomUUID(), UUID.randomUUID(), 1)).get();
			awaitRows("order_paid_analytics", 1);
			assertThat(countRows("processed_events")).isEqualTo(1);
		}
	}

	@Test
	void transientFailureRetriesAndEventuallySucceedsIdempotently() throws Exception {
		installTransientFailureTrigger(2);
		UUID eventId = UUID.randomUUID();

		publisher.publish(kafkaProperties.topics().orderEvents(),
				envelope(eventId, UUID.randomUUID(), UUID.randomUUID(), 1)).get();

		awaitRows("order_paid_analytics", 1);
		assertThat(countRows("processed_events")).isEqualTo(1);
		assertThat(jdbcTemplate.queryForObject(
				"SELECT last_value FROM seatflow_test_analytics_attempts",
				Long.class)).isEqualTo(3L);
	}

	private Consumer<Object, Object> consumer(String groupId) {
		return consumerFactory.createConsumer(groupId, "seatflow-dlt-test");
	}

	private ConsumerRecord<Object, Object> readDeadLetter(
			Consumer<Object, Object> consumer,
			UUID eventId) {
		Instant deadline = Instant.now().plus(Duration.ofSeconds(20));
		while (Instant.now().isBefore(deadline)) {
			for (ConsumerRecord<Object, Object> record : consumer.poll(Duration.ofMillis(250))) {
				String header = header(record.headers(), KafkaDeadLetterMetadata.ORIGINAL_EVENT_ID);
				if (eventId.toString().equals(header)) {
					return record;
				}
			}
		}
		throw new AssertionError("Expected dead-letter record for event " + eventId);
	}

	private void awaitRows(String table, int expectedRows) throws InterruptedException {
		Instant deadline = Instant.now().plus(Duration.ofSeconds(20));
		while (Instant.now().isBefore(deadline)) {
			if (countRows(table) == expectedRows) {
				return;
			}
			Thread.sleep(100);
		}
		throw new AssertionError("Expected %s rows in %s".formatted(expectedRows, table));
	}

	private int countRows(String table) {
		return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class);
	}

	private void cleanDatabase() {
		jdbcTemplate.update("DELETE FROM order_paid_analytics");
		jdbcTemplate.update("DELETE FROM processed_events");
	}

	private void installTransientFailureTrigger(int failuresBeforeSuccess) {
		dropTransientFailureTrigger();
		jdbcTemplate.execute("CREATE SEQUENCE seatflow_test_analytics_attempts START 1");
		jdbcTemplate.execute("""
				CREATE OR REPLACE FUNCTION seatflow_test_fail_analytics_insert()
				RETURNS trigger AS $$
				DECLARE
				    attempt bigint;
				BEGIN
				    attempt := nextval('seatflow_test_analytics_attempts');
				    IF attempt <= %s THEN
				        RAISE EXCEPTION 'temporary analytics failure';
				    END IF;
				    RETURN NEW;
				END;
				$$ LANGUAGE plpgsql
				""".formatted(failuresBeforeSuccess));
		jdbcTemplate.execute("""
				CREATE TRIGGER seatflow_test_fail_analytics_insert_trigger
				BEFORE INSERT ON order_paid_analytics
				FOR EACH ROW EXECUTE FUNCTION seatflow_test_fail_analytics_insert()
				""");
	}

	private void dropTransientFailureTrigger() {
		jdbcTemplate.execute("DROP TRIGGER IF EXISTS seatflow_test_fail_analytics_insert_trigger ON order_paid_analytics");
		jdbcTemplate.execute("DROP FUNCTION IF EXISTS seatflow_test_fail_analytics_insert()");
		jdbcTemplate.execute("DROP SEQUENCE IF EXISTS seatflow_test_analytics_attempts");
	}

	private static EventEnvelope<OrderPaidPayload> envelope(
			UUID eventId,
			UUID orderId,
			UUID correlationId,
			int version) {
		Instant occurredAt = Instant.parse("2026-08-17T01:02:03Z");
		return new EventEnvelope<>(
				eventId,
				"OrderPaid",
				version,
				orderId,
				correlationId,
				occurredAt,
				new OrderPaidPayload(
						orderId,
						UUID.randomUUID(),
						UUID.randomUUID(),
						correlationId,
						new BigDecimal("125000.00"),
						"VND",
						List.of(UUID.randomUUID()),
						occurredAt));
	}

	private static String header(Headers headers, String name) {
		if (headers.lastHeader(name) == null) {
			return null;
		}
		return new String(headers.lastHeader(name).value(), StandardCharsets.UTF_8);
	}
}
