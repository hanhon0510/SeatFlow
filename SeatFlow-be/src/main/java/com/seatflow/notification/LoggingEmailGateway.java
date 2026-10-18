package com.seatflow.notification;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Used while {@code seatflow.mail.enabled} is false (the default): the message is described in the
 * log and goes nowhere. The body is left out because it carries a customer's order details.
 */
@Component
@ConditionalOnProperty(prefix = "seatflow.mail", name = "enabled", havingValue = "false", matchIfMissing = true)
public class LoggingEmailGateway implements EmailGateway {

	private static final Logger log = LoggerFactory.getLogger(LoggingEmailGateway.class);

	@Override
	public void send(EmailMessage message) {
		log.info("Email sending is disabled; would have sent \"{}\" to {}", message.subject(), message.to());
	}
}
