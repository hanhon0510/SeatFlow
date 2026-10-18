package com.seatflow.notification;

import java.nio.charset.StandardCharsets;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;

@Component
@ConditionalOnProperty(prefix = "seatflow.mail", name = "enabled", havingValue = "true")
public class SmtpEmailGateway implements EmailGateway {

	private final JavaMailSender mailSender;
	private final SeatFlowMailProperties properties;

	public SmtpEmailGateway(JavaMailSender mailSender, SeatFlowMailProperties properties) {
		this.mailSender = mailSender;
		this.properties = properties;
	}

	@Override
	public void send(EmailMessage message) {
		try {
			MimeMessage mime = mailSender.createMimeMessage();
			MimeMessageHelper helper = new MimeMessageHelper(mime, true, StandardCharsets.UTF_8.name());
			helper.setFrom(properties.from());
			helper.setTo(message.to());
			helper.setSubject(message.subject());
			helper.setText(message.text(), message.html());
			mailSender.send(mime);
		}
		catch (MessagingException | MailException ex) {
			throw new EmailDeliveryException("Could not send \"%s\"".formatted(message.subject()), ex);
		}
	}
}
