package com.seatflow.consumer;

public class ConsumerSideEffectException extends RuntimeException {

	public ConsumerSideEffectException(String consumerName) {
		super("Unable to apply side effect for consumer %s".formatted(consumerName));
	}
}
