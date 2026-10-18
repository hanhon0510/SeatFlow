package com.seatflow.notification;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.mail.MailSenderAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/**
 * Which gateway the application picks from configuration. Uses a bare context runner rather than
 * the whole application, so it needs neither a database nor another cached Spring context.
 */
class EmailGatewayWiringTests {

	private final ApplicationContextRunner runner = new ApplicationContextRunner()
			.withConfiguration(AutoConfigurations.of(MailSenderAutoConfiguration.class))
			.withUserConfiguration(MailConfig.class, LoggingEmailGateway.class, SmtpEmailGateway.class)
			.withPropertyValues("spring.mail.host=localhost", "spring.mail.port=1025");

	@Test
	void onlyLogsTheMessagesByDefaultSoNoMailServerIsNeeded() {
		runner.run(context -> {
			assertThat(context).hasSingleBean(EmailGateway.class);
			assertThat(context.getBean(EmailGateway.class)).isInstanceOf(LoggingEmailGateway.class);
		});
	}

	@Test
	void onlyLogsWhenMailIsExplicitlyDisabled() {
		runner.withPropertyValues("seatflow.mail.enabled=false").run(context ->
				assertThat(context.getBean(EmailGateway.class)).isInstanceOf(LoggingEmailGateway.class));
	}

	@Test
	void sendsOverSmtpWithTheConfiguredSenderAndLinksWhenEnabled() {
		runner.withPropertyValues(
				"seatflow.mail.enabled=true",
				"seatflow.mail.from=SeatFlow <tickets@seatflow.test>",
				"seatflow.mail.frontend-base-url=https://seatflow.test/").run(context -> {
					assertThat(context).hasSingleBean(EmailGateway.class);
					assertThat(context.getBean(EmailGateway.class)).isInstanceOf(SmtpEmailGateway.class);
					SeatFlowMailProperties properties = context.getBean(SeatFlowMailProperties.class);
					assertThat(properties.from()).isEqualTo("SeatFlow <tickets@seatflow.test>");
					assertThat(properties.frontendBaseUrl()).isEqualTo("https://seatflow.test");
				});
	}

	@Test
	void linksInEmailsUseTheConfiguredFrontendAddress() {
		runner.withPropertyValues("seatflow.mail.frontend-base-url=https://tickets.example.com").run(context -> {
			OrderEmailTemplates templates = context.getBean(OrderEmailTemplates.class);
			EmailMessage message = templates.orderPaid(
					new OrderEmailDetails(java.util.UUID.randomUUID(), "a@b.test", java.util.UUID.randomUUID(), "Show",
							java.time.Instant.parse("2026-12-05T12:30:00Z"), "Hall", null, null, "UTC",
							java.math.BigDecimal.TEN, java.math.BigDecimal.ZERO, null, "VND", false, null),
					java.util.List.of());
			assertThat(message.text()).contains("https://tickets.example.com/tickets");
		});
	}
}
