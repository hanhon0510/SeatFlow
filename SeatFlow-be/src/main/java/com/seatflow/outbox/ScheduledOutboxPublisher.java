package com.seatflow.outbox;

import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnBean(OutboxPublisher.class)
@ConditionalOnProperty(prefix = "seatflow.outbox.publisher", name = "enabled", havingValue = "true")
public class ScheduledOutboxPublisher {

	private final OutboxPublisher outboxPublisher;

	public ScheduledOutboxPublisher(OutboxPublisher outboxPublisher) {
		this.outboxPublisher = outboxPublisher;
	}

	@Scheduled(fixedDelayString = "${seatflow.outbox.publisher.fixed-delay-ms:5000}")
	public void publishPendingEvents() {
		outboxPublisher.publishPending();
	}
}
