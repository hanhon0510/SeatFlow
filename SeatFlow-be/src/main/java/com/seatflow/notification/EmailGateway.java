package com.seatflow.notification;

/**
 * Where emails leave the application. The rest of the code builds messages and does not know
 * whether they are delivered over SMTP, only logged, or captured by a test.
 */
public interface EmailGateway {

	/** @throws EmailDeliveryException when the message could not be handed over, so the caller can retry */
	void send(EmailMessage message);
}
