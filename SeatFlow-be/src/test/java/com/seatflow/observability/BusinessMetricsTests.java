package com.seatflow.observability;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import com.seatflow.outbox.OutboxMapper;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micrometer.prometheusmetrics.PrometheusConfig;
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry;

class BusinessMetricsTests {

	@Test
	void prometheusScrapeUsesRequiredMetricNames() {
		PrometheusMeterRegistry registry = new PrometheusMeterRegistry(PrometheusConfig.DEFAULT);
		BusinessMetrics metrics = new BusinessMetrics(registry);

		metrics.seatHoldCreated();
		metrics.seatHoldConflict();
		metrics.seatHoldReleased();
		metrics.reservationCreated();
		metrics.paymentSuccess();
		metrics.paymentFailure();
		metrics.ticketIssued();
		metrics.outboxPublishFailure();
		metrics.kafkaConsumerFailure();

		String scrape = new PrometheusMetricNameCompatibilityFilter().rewrite(registry.scrape());
		assertThat(scrape)
				.contains("seat_hold_created_total")
				.contains("seat_hold_conflict_total")
				.contains("seat_hold_released_total")
				.contains("reservation_created_total")
				.contains("payment_success_total")
				.contains("payment_failure_total")
				.contains("ticket_issued_total")
				.contains("outbox_publish_failure_total")
				.contains("kafka_consumer_failure_total")
				.doesNotContain("payment_success_total_total");
	}

	@Test
	void metricsDoNotUseUserIdLabels() {
		SimpleMeterRegistry registry = new SimpleMeterRegistry();
		BusinessMetrics metrics = new BusinessMetrics(registry);

		metrics.seatHoldCreated();
		metrics.paymentSuccess();

		assertThat(registry.getMeters())
				.allSatisfy(meter -> assertThat(meter.getId().getTags())
						.noneMatch(tag -> tag.getKey().equals("userId")));
	}

	@Test
	void outboxPendingGaugeReadsMapperCount() {
		SimpleMeterRegistry registry = new SimpleMeterRegistry();
		OutboxMapper outboxMapper = Mockito.mock(OutboxMapper.class);
		when(outboxMapper.countPending()).thenReturn(7L);

		new OutboxPendingMetrics(registry, outboxMapper);

		assertThat(registry.get("outbox_pending_count").gauge().value()).isEqualTo(7.0);
	}
}
