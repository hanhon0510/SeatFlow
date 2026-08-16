package com.seatflow.consumer;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.seatflow.kafka.EventEnvelope;
import com.seatflow.outbox.OrderPaidPayload;
import com.seatflow.support.PostgresTestContainerSupport;

@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
class OrderPaidAnalyticsConsumerIntegrationTests extends PostgresTestContainerSupport {

	private static final UUID EVENT_ID = UUID.fromString("33333333-3333-4333-8333-333333333333");
	private static final UUID ORDER_ID = UUID.fromString("44444444-4444-4444-8444-444444444444");
	private static final UUID RESERVATION_ID = UUID.fromString("55555555-5555-4555-8555-555555555555");
	private static final UUID USER_ID = UUID.fromString("66666666-6666-4666-8666-666666666666");
	private static final UUID PAYMENT_ID = UUID.fromString("77777777-7777-4777-8777-777777777777");
	private static final UUID CORRELATION_ID = UUID.fromString("88888888-8888-4888-8888-888888888888");
	private static final UUID EVENT_SEAT_ID = UUID.fromString("99999999-9999-4999-8999-999999999999");
	private static final Instant OCCURRED_AT = Instant.parse("2026-08-10T11:59:00Z");

	@Autowired
	private OrderPaidAnalyticsConsumer consumer;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@BeforeEach
	void setUp() {
		cleanDatabase();
	}

	@AfterEach
	void tearDown() {
		cleanDatabase();
	}

	@Test
	void firstDeliveryProcessesAndDuplicateDeliveryIsRestartSafe() {
		EventEnvelope<OrderPaidPayload> envelope = envelope();

		assertThat(consumer.process(envelope)).isTrue();
		assertThat(consumer.process(envelope)).isFalse();

		assertThat(countRows("processed_events")).isEqualTo(1);
		assertThat(countRows("order_paid_analytics")).isEqualTo(1);
		assertThat(jdbcTemplate.queryForObject(
				"SELECT consumer_name FROM processed_events WHERE event_id = ?",
				String.class,
				EVENT_ID)).isEqualTo(OrderPaidAnalyticsConsumer.CONSUMER_NAME);
		assertThat(jdbcTemplate.queryForObject(
				"SELECT seat_count FROM order_paid_analytics WHERE event_id = ?",
				Integer.class,
				EVENT_ID)).isEqualTo(1);
	}

	@Test
	void concurrentDuplicateDeliveryCreatesOneSideEffect() throws Exception {
		EventEnvelope<OrderPaidPayload> envelope = envelope();
		CountDownLatch ready = new CountDownLatch(2);
		CountDownLatch start = new CountDownLatch(1);
		ExecutorService executor = Executors.newFixedThreadPool(2);

		try {
			Future<Boolean> first = executor.submit(() -> processWhenReleased(envelope, ready, start));
			Future<Boolean> second = executor.submit(() -> processWhenReleased(envelope, ready, start));
			assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
			start.countDown();

			List<Boolean> results = List.of(
					first.get(10, TimeUnit.SECONDS),
					second.get(10, TimeUnit.SECONDS));

			assertThat(results).containsExactlyInAnyOrder(true, false);
			assertThat(countRows("processed_events")).isEqualTo(1);
			assertThat(countRows("order_paid_analytics")).isEqualTo(1);
		}
		finally {
			start.countDown();
			executor.shutdownNow();
		}
	}

	private boolean processWhenReleased(
			EventEnvelope<OrderPaidPayload> envelope,
			CountDownLatch ready,
			CountDownLatch start) throws InterruptedException {
		ready.countDown();
		start.await();
		return consumer.process(envelope);
	}

	private static EventEnvelope<OrderPaidPayload> envelope() {
		return new EventEnvelope<>(
				EVENT_ID,
				"OrderPaid",
				1,
				ORDER_ID,
				CORRELATION_ID,
				OCCURRED_AT,
				new OrderPaidPayload(
						ORDER_ID,
						RESERVATION_ID,
						USER_ID,
						PAYMENT_ID,
						new BigDecimal("125000.00"),
						"VND",
						List.of(EVENT_SEAT_ID),
						OCCURRED_AT));
	}

	private int countRows(String table) {
		return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class);
	}

	private void cleanDatabase() {
		jdbcTemplate.update("DELETE FROM order_paid_analytics");
		jdbcTemplate.update("DELETE FROM processed_events");
	}
}
