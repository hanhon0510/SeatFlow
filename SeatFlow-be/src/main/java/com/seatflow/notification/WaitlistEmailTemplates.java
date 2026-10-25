package com.seatflow.notification;

import java.time.DateTimeException;
import java.time.Duration;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** The wording of emails about the event waitlist. Pure: the same inputs always give the same message. */
public class WaitlistEmailTemplates {

	private static final String GREETING = "Hello,";
	private static final String SIGN_OFF = "The SeatFlow team";
	private static final DateTimeFormatter EVENT_TIME =
			DateTimeFormatter.ofPattern("EEEE d MMMM yyyy, HH:mm (zzz)", Locale.ENGLISH);

	private final String frontendBaseUrl;

	public WaitlistEmailTemplates(String frontendBaseUrl) {
		this.frontendBaseUrl = frontendBaseUrl;
	}

	/** Tells the buyer seats are being kept for them, and until when. */
	public EmailMessage seatsAvailable(WaitlistOfferEmailDetails offer, Duration validFor) {
		String seats = offer.seatCount() == 1 ? "1 seat" : offer.seatCount() + " seats";
		EmailBody body = new EmailBody()
				.paragraph("Good news: " + seats + " for " + offer.eventName() + " just became available, "
						+ "and we are keeping them for you.")
				.facts(facts(offer))
				.paragraph("The seats are yours for " + describe(validFor) + ". After that they go back on "
						+ "sale and the next person on the waitlist is told. Open the event to choose your "
						+ "seats and check out.")
				.link("Get your seats", frontendBaseUrl + "/events/" + offer.eventId());
		return new EmailMessage(
				offer.email(),
				"Seats are available for " + offer.eventName(),
				body.text(GREETING, SIGN_OFF),
				body.html(GREETING, SIGN_OFF));
	}

	private static List<String[]> facts(WaitlistOfferEmailDetails offer) {
		List<String[]> facts = new ArrayList<>();
		facts.add(new String[] {"Event", offer.eventName()});
		facts.add(new String[] {"When", EVENT_TIME.format(offer.eventStartTime().atZone(zone(offer.venueTimezone())))});
		facts.add(new String[] {"Where", venue(offer)});
		facts.add(new String[] {"Seats kept for you", String.valueOf(offer.seatCount())});
		return facts;
	}

	private static String venue(WaitlistOfferEmailDetails offer) {
		List<String> parts = new ArrayList<>();
		parts.add(offer.venueName());
		if (offer.venueAddress() != null && !offer.venueAddress().isBlank()) {
			parts.add(offer.venueAddress());
		}
		if (offer.venueCity() != null && !offer.venueCity().isBlank()) {
			parts.add(offer.venueCity());
		}
		return String.join(", ", parts);
	}

	private static ZoneId zone(String id) {
		try {
			return id == null ? ZoneOffset.UTC : ZoneId.of(id);
		}
		catch (DateTimeException ex) {
			return ZoneOffset.UTC;
		}
	}

	private static String describe(Duration duration) {
		long minutes = Math.max(1, duration.toMinutes());
		if (minutes >= 60 && minutes % 60 == 0) {
			long hours = minutes / 60;
			return hours == 1 ? "1 hour" : hours + " hours";
		}
		return minutes == 1 ? "1 minute" : minutes + " minutes";
	}
}
