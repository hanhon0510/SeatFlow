package com.seatflow.notification;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.DateTimeException;
import java.time.Duration;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.seatflow.refund.RefundRecord;

/** The wording of each order email. Pure: the same inputs always give the same message. */
public class OrderEmailTemplates {

	private static final String GREETING = "Hello,";
	private static final String SIGN_OFF = "The SeatFlow team";
	private static final DateTimeFormatter EVENT_TIME =
			DateTimeFormatter.ofPattern("EEEE d MMMM yyyy, HH:mm (zzz)", Locale.ENGLISH);

	private final String frontendBaseUrl;

	public OrderEmailTemplates(String frontendBaseUrl) {
		this.frontendBaseUrl = frontendBaseUrl;
	}

	public EmailMessage orderPaid(OrderEmailDetails order, List<OrderEmailSeat> seats) {
		EmailBody body = new EmailBody()
				.paragraph("Thank you for your purchase. Your order is confirmed and your tickets are ready.")
				.facts(eventFacts(order))
				.heading(seats.size() == 1 ? "Your seat" : "Your seats")
				.bullets(seats.stream().map(OrderEmailSeat::description).toList())
				.heading("Payment")
				.facts(paymentFacts(order))
				.paragraph("Show the QR code on each ticket at the door. Your tickets are always available "
						+ "in your account.")
				.link("View your tickets", frontendBaseUrl + "/tickets");
		return message(order, "Your tickets for " + order.eventName(), body);
	}

	public EmailMessage orderRefunded(OrderEmailDetails order, List<OrderEmailSeat> seats, RefundRecord refund) {
		String amount = money(refund.amount(), refund.currency());
		EmailBody body = new EmailBody();
		String subject;
		if (order.eventCancelled()) {
			subject = order.eventName() + " has been cancelled - you have been refunded";
			body.paragraph("We are sorry to let you know that " + order.eventName() + " has been cancelled.");
			if (hasText(order.cancellationReason())) {
				body.paragraph("Reason: " + order.cancellationReason());
			}
			body.paragraph("Your order has been refunded in full (" + amount + "). You do not need to do anything.");
		}
		else {
			subject = "Your refund for " + order.eventName();
			body.paragraph("We have refunded your order for " + order.eventName() + " (" + amount + ").");
			if (hasText(refund.reason())) {
				body.paragraph("Reason: " + refund.reason());
			}
		}
		body.heading("Refund")
				.facts(List.of(
						new String[] {"Amount refunded", amount},
						new String[] {"Order", reference(order)}))
				.heading(seats.size() == 1 ? "Ticket no longer valid" : "Tickets no longer valid")
				.bullets(seats.stream().map(OrderEmailSeat::description).toList())
				.paragraph("The money is returned to your original payment method. Depending on your bank it "
						+ "can take a few days to show up.")
				.link("View your orders and tickets", frontendBaseUrl + "/tickets");
		return message(order, subject, body);
	}

	/** Sent shortly before the event starts, to buyers who still hold valid tickets. */
	public EmailMessage eventReminder(OrderEmailDetails order, List<OrderEmailSeat> seats, Duration untilStart) {
		EmailBody body = new EmailBody()
				.paragraph(order.eventName() + " starts in about " + describe(untilStart) + ".")
				.facts(eventFacts(order))
				.heading(seats.size() == 1 ? "Your seat" : "Your seats")
				.bullets(seats.stream().map(OrderEmailSeat::description).toList())
				.paragraph("Have the QR code of each ticket ready at the door. Your tickets are always "
						+ "available in your account.")
				.link("View your tickets", frontendBaseUrl + "/tickets");
		return message(order, "Reminder: " + order.eventName() + " starts soon", body);
	}

	private static String describe(Duration duration) {
		long hours = Math.max(1, Math.round(duration.toMinutes() / 60.0));
		if (hours < 48) {
			return hours == 1 ? "1 hour" : hours + " hours";
		}
		long days = Math.round(hours / 24.0);
		return days + " days";
	}

	private static EmailMessage message(OrderEmailDetails order, String subject, EmailBody body) {
		return new EmailMessage(
				order.buyerEmail(),
				subject,
				body.text(GREETING, SIGN_OFF),
				body.html(GREETING, SIGN_OFF));
	}

	private static List<String[]> eventFacts(OrderEmailDetails order) {
		List<String[]> facts = new ArrayList<>();
		facts.add(new String[] {"Event", order.eventName()});
		facts.add(new String[] {"When", eventTime(order)});
		facts.add(new String[] {"Where", venue(order)});
		facts.add(new String[] {"Order", reference(order)});
		return facts;
	}

	private static List<String[]> paymentFacts(OrderEmailDetails order) {
		List<String[]> facts = new ArrayList<>();
		boolean discounted = order.discountAmount() != null && order.discountAmount().signum() > 0;
		if (discounted) {
			BigDecimal subtotal = order.totalAmount().add(order.discountAmount());
			facts.add(new String[] {"Subtotal", money(subtotal, order.currency())});
			String label = hasText(order.promoCode()) ? "Discount (" + order.promoCode() + ")" : "Discount";
			facts.add(new String[] {label, "-" + money(order.discountAmount(), order.currency())});
		}
		facts.add(new String[] {"Total paid", money(order.totalAmount(), order.currency())});
		return facts;
	}

	/** Shown in the venue's own time zone, which is the one the buyer needs to be on time. */
	private static String eventTime(OrderEmailDetails order) {
		return EVENT_TIME.format(order.eventStartTime().atZone(zone(order.venueTimezone())));
	}

	private static ZoneId zone(String id) {
		try {
			return id == null ? ZoneOffset.UTC : ZoneId.of(id);
		}
		catch (DateTimeException ex) {
			return ZoneOffset.UTC;
		}
	}

	private static String venue(OrderEmailDetails order) {
		List<String> parts = new ArrayList<>();
		parts.add(order.venueName());
		if (hasText(order.venueAddress())) {
			parts.add(order.venueAddress());
		}
		if (hasText(order.venueCity())) {
			parts.add(order.venueCity());
		}
		return String.join(", ", parts);
	}

	/** A short, quotable reference: the first block of the order id. */
	private static String reference(OrderEmailDetails order) {
		return order.orderId().toString().substring(0, 8).toUpperCase(Locale.ROOT);
	}

	private static String money(BigDecimal value, String currency) {
		NumberFormat format = NumberFormat.getNumberInstance(Locale.ENGLISH);
		format.setMaximumFractionDigits(2);
		return format.format(value) + " " + currency;
	}

	private static boolean hasText(String value) {
		return value != null && !value.isBlank();
	}
}
