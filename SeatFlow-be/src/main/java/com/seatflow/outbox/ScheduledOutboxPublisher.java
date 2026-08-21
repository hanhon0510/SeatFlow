package com.seatflow.outbox;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnBean(OutboxPublisher.class)
@ConditionalOnProperty(prefix = "seatflow.outbox.publisher", name = "enabled", havingValue = "true")
public class ScheduledOutboxPublisher {

	private static final Logger log = LoggerFactory.getLogger(ScheduledOutboxPublisher.class);

	private final OutboxPublisher outboxPublisher;

	public ScheduledOutboxPublisher(OutboxPublisher outboxPublisher) {
		this.outboxPublisher = outboxPublisher;
	}

	@Scheduled(fixedDelayString = "${seatflow.outbox.publisher.fixed-delay-ms:5000}")
	public void publishPendingEvents() {
		try {
			outboxPublisher.publishPending();
		}
		catch (RuntimeException ex) {
			log.warn("Outbox publisher pass failed; pending records remain eligible for a later retry", ex);
		}
	}
}
