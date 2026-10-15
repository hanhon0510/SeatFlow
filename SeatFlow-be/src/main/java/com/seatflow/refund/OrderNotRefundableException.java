package com.seatflow.refund;

/** The order is not in a state a refund can be issued from. */
public class OrderNotRefundableException extends RuntimeException {

	public OrderNotRefundableException(String message) {
		super(message);
	}
}
