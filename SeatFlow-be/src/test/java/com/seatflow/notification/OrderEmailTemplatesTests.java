package com.seatflow.notification;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.seatflow.refund.RefundRecord;

class OrderEmailTemplatesTests {

	private static final UUID ORDER_ID = UUID.fromString("abcdef12-3456-4789-8abc-def012345678");
	private static final Instant EVENT_START = Instant.parse("2026-12-05T12:30:00Z");

	private final OrderEmailTemplates templates = new OrderEmailTemplates("https://seatflow.example");

	private final List<OrderEmailSeat> seats = List.of(
			new OrderEmailSeat(UUID.randomUUID(), "Orchestra", "A", "A1"),
			new OrderEmailSeat(UUID.randomUUID(), "Orchestra", "A", "A2"));

	@Test
	void paidEmailListsTheEventSeatsAndTotal() {
		EmailMessage message = templates.orderPaid(order("Opening Night", "0", null, false, null), seats);

		assertThat(message.to()).isEqualTo("buyer@example.com");
		assertThat(message.subject()).isEqualTo("Your tickets for Opening Night");
		assertThat(message.text())
				.contains("Event: Opening Night")
				.contains("Where: Concert Hall, 1 Main Street, Ho Chi Minh City")
				.contains("Order: ABCDEF12")
				.contains("- Orchestra, row A, seat A1")
				.contains("- Orchestra, row A, seat A2")
				.contains("Total paid: 200,000 VND")
				.contains("View your tickets: https://seatflow.example/tickets");
		assertThat(message.text()).doesNotContain("Discount");
		assertThat(message.html()).contains("Opening Night").contains("href=\"https://seatflow.example/tickets\"");
	}

	@Test
	void paidEmailShowsTheEventTimeInTheVenueTimeZone() {
		EmailMessage message = templates.orderPaid(order("Night", "0", null, false, null), seats);

		// 12:30 UTC is 19:30 in Ho Chi Minh City (UTC+7).
		assertThat(message.text()).contains("When: Saturday 5 December 2026, 19:30");
	}

	@Test
	void anUnknownVenueTimeZoneFallsBackToUtcInsteadOfFailing() {
		OrderEmailDetails order = new OrderEmailDetails(
				ORDER_ID, "buyer@example.com", UUID.randomUUID(), "Night", EVENT_START,
				"Hall", null, null, "Not/AZone", new BigDecimal("1000"), BigDecimal.ZERO, null, "VND", false, null);

		EmailMessage message = templates.orderPaid(order, seats);

		assertThat(message.text()).contains("12:30").contains("Where: Hall\n");
	}

	@Test
	void paidEmailBreaksOutAPromoDiscount() {
		EmailMessage message = templates.orderPaid(order("Night", "50000", "SPRING", false, null), seats);

		assertThat(message.text())
				.contains("Subtotal: 250,000 VND")
				.contains("Discount (SPRING): -50,000 VND")
				.contains("Total paid: 200,000 VND");
	}

	@Test
	void aSingleSeatIsIntroducedInTheSingular() {
		EmailMessage message = templates.orderPaid(order("Night", "0", null, false, null), seats.subList(0, 1));

		assertThat(message.text()).contains("Your seat\n").doesNotContain("Your seats");
	}

	@Test
	void refundEmailStatesTheAmountAndThatTheTicketsAreVoid() {
		EmailMessage message = templates.orderRefunded(
				order("Night", "0", null, false, null), seats, refund("Duplicate purchase"));

		assertThat(message.subject()).isEqualTo("Your refund for Night");
		assertThat(message.text())
				.contains("We have refunded your order for Night (200,000 VND).")
				.contains("Reason: Duplicate purchase")
				.contains("Amount refunded: 200,000 VND")
				.contains("Tickets no longer valid")
				.contains("- Orchestra, row A, seat A1");
	}

	@Test
	void refundEmailLeavesOutAMissingReason() {
		EmailMessage message = templates.orderRefunded(order("Night", "0", null, false, null), seats, refund(null));

		assertThat(message.text()).doesNotContain("Reason:");
	}

	@Test
	void aRefundCausedByACancelledEventApologisesAndExplains() {
		EmailMessage message = templates.orderRefunded(
				order("Night", "0", null, true, "Venue flooded"), seats, refund("Event cancelled: Venue flooded"));

		assertThat(message.subject()).isEqualTo("Night has been cancelled - you have been refunded");
		assertThat(message.text())
				.contains("We are sorry to let you know that Night has been cancelled.")
				.contains("Reason: Venue flooded")
				.contains("refunded in full (200,000 VND). You do not need to do anything.");
		assertThat(message.text()).doesNotContain("Event cancelled: Venue flooded");
	}

	@Test
	void aCancelledEventWithoutAReasonDoesNotMentionOne() {
		EmailMessage message = templates.orderRefunded(
				order("Night", "0", null, true, null), seats, refund("Event cancelled"));

		assertThat(message.text()).doesNotContain("Reason:");
	}

	@Test
	void htmlEscapesEverythingTheBuyerOrAnAdminCouldHaveTyped() {
		OrderEmailDetails order = order("<script>alert(1)</script> & Co", "0", null, true, "<b>flood</b>");

		EmailMessage message = templates.orderRefunded(order, seats, refund("<img src=x>"));

		assertThat(message.html())
				.doesNotContain("<script>")
				.doesNotContain("<b>flood</b>")
				.contains("&lt;script&gt;alert(1)&lt;/script&gt; &amp; Co")
				.contains("&lt;b&gt;flood&lt;/b&gt;");
		// The plain-text part is not HTML, so it keeps the original characters.
		assertThat(message.text()).contains("<script>alert(1)</script> & Co");
	}

	private static OrderEmailDetails order(
			String eventName, String discount, String promoCode, boolean cancelled, String cancellationReason) {
		return new OrderEmailDetails(
				ORDER_ID,
				"buyer@example.com",
				UUID.randomUUID(),
				eventName,
				EVENT_START,
				"Concert Hall",
				"1 Main Street",
				"Ho Chi Minh City",
				"Asia/Ho_Chi_Minh",
				new BigDecimal("200000.00"),
				new BigDecimal(discount),
				promoCode,
				"VND",
				cancelled,
				cancellationReason);
	}

	private static RefundRecord refund(String reason) {
		return new RefundRecord(
				UUID.randomUUID(),
				ORDER_ID,
				UUID.randomUUID(),
				new BigDecimal("200000.00"),
				"VND",
				reason,
				UUID.randomUUID(),
				Instant.parse("2026-11-01T00:00:00Z"));
	}
}
