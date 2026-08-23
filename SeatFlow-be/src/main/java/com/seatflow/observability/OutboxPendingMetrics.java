package com.seatflow.observability;

import org.springframework.stereotype.Component;

import com.seatflow.outbox.OutboxMapper;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;

@Component
public class OutboxPendingMetrics {

	public OutboxPendingMetrics(MeterRegistry registry, OutboxMapper outboxMapper) {
		Gauge.builder("outbox_pending_count", outboxMapper, OutboxMapper::countPending)
				.description("Pending outbox events")
				.register(registry);
	}
}
