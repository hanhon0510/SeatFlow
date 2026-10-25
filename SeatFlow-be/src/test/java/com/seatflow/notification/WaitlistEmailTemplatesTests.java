package com.seatflow.notification;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class WaitlistEmailTemplatesTests {

	private static final UUID EVENT_ID = UUID.fromString("11111111-2222-3333-4444-555555555555");

	private final WaitlistEmailTemplates templates = new WaitlistEmailTemplates("https://seatflow.example");

	@Test
	void tellsTheBuyerHowManySeatsAreKeptAndForHowLong() {
		EmailMessage message = templates.seatsAvailable(offer(2, "Asia/Ho_Chi_Minh"), Duration.ofMinutes(10));

		assertThat(message.to()).isEqualTo("buyer@example.com");
		assertThat(message.subject()).isEqualTo("Seats are available for Autumn Concert");
		assertThat(message.text())
				.contains("2 seats for Autumn Concert")
				.contains("for 10 minutes")
				.contains("Seats kept for you: 2")
				.contains("https://seatflow.example/events/" + EVENT_ID);
		assertThat(message.html()).contains("href=\"https://seatflow.example/events/" + EVENT_ID + "\"");
	}

	@Test
	void showsTheStartInTheVenuesOwnTimeZone() {
		// 12:00 UTC is 19:00 in Ho Chi Minh City.
		EmailMessage message = templates.seatsAvailable(offer(1, "Asia/Ho_Chi_Minh"), Duration.ofHours(1));

		assertThat(message.text()).contains("19:00").contains("1 seat for").contains("for 1 hour");
	}

	@Test
	void fallsBackToUtcForAnUnknownTimeZone() {
		EmailMessage message = templates.seatsAvailable(offer(1, "Not/AZone"), Duration.ofMinutes(10));

		assertThat(message.text()).contains("12:00");
	}

	@Test
	void escapesTheEventNameInTheHtmlBody() {
		WaitlistOfferEmailDetails offer = new WaitlistOfferEmailDetails(
				UUID.randomUUID(), "buyer@example.com", EVENT_ID, "<script>x</script>",
				Instant.parse("2030-01-05T12:00:00Z"), "Hall", null, null, "UTC", 1,
				Instant.parse("2030-01-01T00:10:00Z"));

		EmailMessage message = templates.seatsAvailable(offer, Duration.ofMinutes(10));

		assertThat(message.html()).doesNotContain("<script>").contains("&lt;script&gt;");
	}

	private static WaitlistOfferEmailDetails offer(int seatCount, String timezone) {
		return new WaitlistOfferEmailDetails(
				UUID.randomUUID(), "buyer@example.com", EVENT_ID, "Autumn Concert",
				Instant.parse("2030-01-05T12:00:00Z"), "Opera House", "1 Main Street", "Ho Chi Minh City",
				timezone, seatCount, Instant.parse("2030-01-01T00:10:00Z"));
	}
}
