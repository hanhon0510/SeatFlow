package com.seatflow.observability;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;

import org.springframework.stereotype.Component;

@Component
public class BusinessMetrics {

	private final Counter seatHoldCreated;
	private final Counter seatHoldConflict;
	private final Counter seatHoldReleased;
	private final Counter reservationCreated;
	private final Counter reservationExpired;
	private final Counter paymentSuccess;
	private final Counter paymentFailure;
	private final Counter ticketIssued;
	private final Counter outboxPublishFailure;
	private final Counter kafkaConsumerFailure;

	public BusinessMetrics(MeterRegistry registry) {
		this.seatHoldCreated = counter(registry, "seat_hold_created", "Seat holds created successfully");
		this.seatHoldConflict = counter(registry, "seat_hold_conflict", "Seat hold conflicts");
		this.seatHoldReleased = counter(registry, "seat_hold_released", "Seat holds released");
		this.reservationCreated = counter(registry, "reservation_created", "Reservations created");
		this.reservationExpired = counter(registry, "reservation_expired", "Reservations closed by the retention sweep");
		this.paymentSuccess = counter(registry, "payment_success", "Successful payments");
		this.paymentFailure = counter(registry, "payment_failure", "Failed payments");
		this.ticketIssued = counter(registry, "ticket_issued", "Tickets issued");
		this.outboxPublishFailure = counter(registry, "outbox_publish_failure", "Outbox publish failures");
		this.kafkaConsumerFailure = counter(registry, "kafka_consumer_failure", "Kafka consumer failures");
	}

	public void seatHoldCreated() {
		seatHoldCreated.increment();
	}

	public void seatHoldConflict() {
		seatHoldConflict.increment();
	}

	public void seatHoldReleased() {
		seatHoldReleased.increment();
	}

	public void reservationCreated() {
		reservationCreated.increment();
	}

	public void reservationExpired(int count) {
		if (count > 0) {
			reservationExpired.increment(count);
		}
	}

	public void paymentSuccess() {
		paymentSuccess.increment();
	}

	public void paymentFailure() {
		paymentFailure.increment();
	}

	public void ticketIssued() {
		ticketIssued.increment();
	}

	public void outboxPublishFailure() {
		outboxPublishFailure.increment();
	}

	public void kafkaConsumerFailure() {
		kafkaConsumerFailure.increment();
	}

	private static Counter counter(MeterRegistry registry, String name, String description) {
		return Counter.builder(name)
				.description(description)
				.register(registry);
	}
}
