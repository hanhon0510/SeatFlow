package com.seatflow.notification;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class AccountAndReminderTemplatesTests {

	private final AccountEmailTemplates account = new AccountEmailTemplates("https://seatflow.example");
	private final OrderEmailTemplates orders = new OrderEmailTemplates("https://seatflow.example");

	@Test
	void passwordResetEmailCarriesTheLinkAndExplainsItExpires() {
		EmailMessage message = account.passwordReset("a@example.com", "abc_DEF-123", Duration.ofHours(1));

		assertThat(message.to()).isEqualTo("a@example.com");
		assertThat(message.subject()).isEqualTo("Reset your SeatFlow password");
		assertThat(message.text())
				.contains("https://seatflow.example/reset-password?token=abc_DEF-123")
				.contains("expires in 1 hour")
				.contains("you can ignore this email");
		assertThat(message.html()).contains("href=\"https://seatflow.example/reset-password?token=abc_DEF-123\"");
	}

	@Test
	void theLifetimeIsDescribedInTheRightUnit() {
		assertThat(account.passwordReset("a@b.test", "t", Duration.ofMinutes(30)).text()).contains("30 minutes");
		assertThat(account.passwordReset("a@b.test", "t", Duration.ofHours(3)).text()).contains("3 hours");
		assertThat(account.passwordReset("a@b.test", "t", Duration.ofMinutes(90)).text()).contains("90 minutes");
	}

	@Test
	void passwordChangedEmailTellsTheOwnerWhatToDoIfItWasNotThem() {
		EmailMessage message = account.passwordChanged("a@example.com");

		assertThat(message.subject()).isEqualTo("Your SeatFlow password was changed");
		assertThat(message.text())
				.contains("signed out everywhere")
				.contains("https://seatflow.example/forgot-password");
	}

	@Test
	void reminderSaysHowSoonTheShowStartsAndWhereToGo() {
		EmailMessage message = orders.eventReminder(order(), seats(), Duration.ofHours(24));

		assertThat(message.subject()).isEqualTo("Reminder: Opening Night starts soon");
		assertThat(message.text())
				.contains("Opening Night starts in about 24 hours.")
				.contains("Where: Concert Hall, 1 Main Street, Ho Chi Minh City")
				.contains("- Orchestra, row A, seat A1")
				.contains("https://seatflow.example/tickets");
	}

	@Test
	void reminderUsesDaysForLongLeadTimesAndAtLeastAnHour() {
		assertThat(orders.eventReminder(order(), seats(), Duration.ofHours(72)).text()).contains("about 3 days");
		assertThat(orders.eventReminder(order(), seats(), Duration.ofHours(47)).text()).contains("about 47 hours");
		assertThat(orders.eventReminder(order(), seats(), Duration.ofMinutes(10)).text()).contains("about 1 hour.");
	}

	private static OrderEmailDetails order() {
		return new OrderEmailDetails(
				UUID.randomUUID(), "buyer@example.com", UUID.randomUUID(), "Opening Night",
				Instant.parse("2026-12-05T12:30:00Z"), "Concert Hall", "1 Main Street", "Ho Chi Minh City",
				"Asia/Ho_Chi_Minh", new BigDecimal("200000"), BigDecimal.ZERO, null, "VND", false, null);
	}

	private static List<OrderEmailSeat> seats() {
		return List.of(new OrderEmailSeat(UUID.randomUUID(), "Orchestra", "A", "A1"));
	}
}
