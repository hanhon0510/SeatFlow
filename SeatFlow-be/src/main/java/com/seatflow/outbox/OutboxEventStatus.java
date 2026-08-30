package com.seatflow.outbox;

public enum OutboxEventStatus {
	PENDING,
	PUBLISHED,
	/** Terminal. The event can never succeed, so it is no longer retried. */
	FAILED
}
