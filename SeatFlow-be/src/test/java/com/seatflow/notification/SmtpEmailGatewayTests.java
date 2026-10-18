package com.seatflow.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;

import jakarta.mail.Message;
import jakarta.mail.internet.MimeMessage;

@ExtendWith(MockitoExtension.class)
class SmtpEmailGatewayTests {

	private static final EmailMessage MESSAGE = new EmailMessage(
			"buyer@example.com", "Your tickets for Opening Night", "plain body", "<p>html body</p>");

	@Mock
	private JavaMailSender mailSender;

	private final SeatFlowMailProperties properties =
			new SeatFlowMailProperties(true, "SeatFlow <no-reply@seatflow.test>", "https://seatflow.example");

	@Test
	void buildsAMultipartMessageFromTheConfiguredSender() throws Exception {
		MimeMessage mime = new JavaMailSenderImpl().createMimeMessage();
		when(mailSender.createMimeMessage()).thenReturn(mime);

		new SmtpEmailGateway(mailSender, properties).send(MESSAGE);

		ArgumentCaptor<MimeMessage> sent = ArgumentCaptor.forClass(MimeMessage.class);
		verify(mailSender).send(sent.capture());
		MimeMessage result = sent.getValue();
		assertThat(result.getSubject()).isEqualTo("Your tickets for Opening Night");
		assertThat(result.getRecipients(Message.RecipientType.TO)[0].toString()).isEqualTo("buyer@example.com");
		assertThat(result.getFrom()[0].toString()).contains("no-reply@seatflow.test");
		result.saveChanges();
		assertThat(result.getContentType()).startsWith("multipart/");
	}

	@Test
	void aMailServerFailureBecomesAnEmailDeliveryException() {
		when(mailSender.createMimeMessage()).thenReturn(new JavaMailSenderImpl().createMimeMessage());
		doThrow(new MailSendException("connection refused")).when(mailSender).send(any(MimeMessage.class));

		assertThatThrownBy(() -> new SmtpEmailGateway(mailSender, properties).send(MESSAGE))
				.isInstanceOf(EmailDeliveryException.class)
				.hasMessageContaining("Your tickets for Opening Night");
	}

	@Test
	void anInvalidRecipientAddressIsAlsoADeliveryFailure() {
		when(mailSender.createMimeMessage()).thenReturn(new JavaMailSenderImpl().createMimeMessage());
		EmailMessage broken = new EmailMessage("not an address <<", "Subject", "text", "<p>html</p>");

		assertThatThrownBy(() -> new SmtpEmailGateway(mailSender, properties).send(broken))
				.isInstanceOf(EmailDeliveryException.class);
	}
}
