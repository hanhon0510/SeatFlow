package com.seatflow.notification;

import java.time.DateTimeException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/** The wording of emails about a ticket changing hands. Pure: the same inputs always give the same message. */
public class TicketEmailTemplates {

	private static final String GREETING = "Hello,";
	private static final String SIGN_OFF = "The SeatFlow team";
	private static final DateTimeFormatter EVENT_TIME =
			DateTimeFormatter.ofPattern("EEEE d MMMM yyyy, HH:mm (zzz)", Locale.ENGLISH);

	private final String frontendBaseUrl;

	public TicketEmailTemplates(String frontendBaseUrl) {
		this.frontendBaseUrl = frontendBaseUrl;
	}

	/** Tells the new holder a ticket is now theirs and where to find it. */
	public EmailMessage ticketReceived(
			String to,
			String fromEmail,
			UUID ticketId,
			String eventName,
			Instant eventStartTime,
			String venueTimezone,
			String venueName,
			String seat) {
		EmailBody body = new EmailBody()
				.paragraph(fromEmail + " has transferred a ticket for " + eventName + " to you.")
				.facts(List.of(
						new String[] {"Event", eventName},
						new String[] {"When", EVENT_TIME.format(eventStartTime.atZone(zone(venueTimezone)))},
						new String[] {"Where", venueName},
						new String[] {"Seat", seat}))
				.paragraph("Open the ticket to see its QR code. Show it at the door on the day.")
				.link("Open your ticket", frontendBaseUrl + "/tickets/" + ticketId);
		return new EmailMessage(
				to,
				"A ticket for " + eventName + " is yours",
				body.text(GREETING, SIGN_OFF),
				body.html(GREETING, SIGN_OFF));
	}

	private static ZoneId zone(String id) {
		try {
			return id == null ? ZoneOffset.UTC : ZoneId.of(id);
		}
		catch (DateTimeException ex) {
			return ZoneOffset.UTC;
		}
	}
}
