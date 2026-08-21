package com.seatflow.health;

public class KafkaHealthUnavailableException extends RuntimeException {

	public KafkaHealthUnavailableException() {
		super("Kafka health check failed");
	}

	public KafkaHealthUnavailableException(Throwable cause) {
		super("Kafka health check failed", cause);
	}
}
